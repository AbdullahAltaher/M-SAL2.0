package com.abdullah.msal2.push;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.abdullah.msal2.banksms.PendingEntries;

/**
 * The daily reminder "سجّلت مصاريف اليوم؟" (SCREENS.md section 7): each minute, users whose
 * reminder time has come in their own time zone (CLAUDE.md rule 14) are checked once for the
 * day, and notified only if they have no expense dated today. A server that was down at that
 * minute still sends it within the hour.
 */
@Component
public class DailyReminder {

	/**
	 * Angular's service worker shows it, and opening it goes to Expenses (ngsw's onActionClick).
	 * The number on the icon is 1, or the messages waiting for review if more (B-22).
	 */
	static final String PAYLOAD = """
			{"notification":{"title":"ميزانية","body":"سجّلت مصاريف اليوم؟","icon":"icons/icon-192.png",\
			"lang":"ar","dir":"rtl","tag":"daily-reminder",\
			"data":{"badge":%d,"onActionClick":{"default":{"operation":"navigateLastFocusedOrOpen","url":"expenses"}}}}}""";

	/**
	 * Claims the users due now: sets {@code last_reminder_on} to their "today", so the reminder
	 * is decided once a day whatever happens next.
	 */
	private static final String CLAIM_DUE = """
			with due as (
			    select u.id, (cast(? as timestamptz) at time zone u.time_zone) as local_now
			    from msal.app_users u
			    where u.daily_reminder_time is not null and u.status = 'ACTIVE'
			      and exists (select 1 from msal.push_subscriptions s where s.user_id = u.id)
			)
			update msal.app_users u set last_reminder_on = d.local_now::date
			from due d
			where u.id = d.id
			  and (u.last_reminder_on is null or u.last_reminder_on < d.local_now::date)
			  and d.local_now::time - u.daily_reminder_time >= interval '0'
			  and d.local_now::time - u.daily_reminder_time < interval '1 hour'
			returning u.id, d.local_now::date
			""";

	private static final String HAS_EXPENSE_TODAY = """
			select exists (select 1 from msal.entries e join msal.budgets b on b.id = e.budget_id
			               where e.user_id = ? and e.type = 'EXPENSE' and e.entry_date = ?
			                 and e.deleted_at is null and b.deleted_at is null)
			""";

	private record Due(UUID userId, LocalDate today) {
	}

	private final JdbcTemplate jdbc;
	private final UserPush push;
	private final PendingEntries pending;
	private final Clock clock;

	public DailyReminder(JdbcTemplate jdbc, UserPush push, PendingEntries pending, Clock clock) {
		this.jdbc = jdbc;
		this.push = push;
		this.pending = pending;
		this.clock = clock;
	}

	static String payload(int badge) {
		return PAYLOAD.formatted(Math.max(1, badge));
	}

	@Scheduled(cron = "${msal.push.reminder-cron:0 * * * * *}")
	public void everyMinute() {
		if (push.enabled()) {
			run();
		}
	}

	/** One check; returns how many users were notified. */
	public int run() {
		List<Due> due = jdbc.query(CLAIM_DUE,
				(rs, n) -> new Due(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class)),
				Timestamp.from(clock.instant()));
		int notified = 0;
		for (Due d : due) {
			if (Boolean.TRUE.equals(
					jdbc.queryForObject(HAS_EXPENSE_TODAY, Boolean.class, d.userId(), Date.valueOf(d.today())))) {
				continue;
			}
			boolean sent = push.send(d.userId(), payload(pending.countOfUser(d.userId())), "Daily reminder");
			if (sent) {
				notified++;
			}
		}
		return notified;
	}
}
