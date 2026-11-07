package com.abdullah.msal2.banksms;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The bank account digits linked to an account of the app ({@code sms_account_links}, V9, B-11).
 */
@Repository
public class SmsLinks {

	public record Link(String suffix, UUID accountId, String accountName, boolean archived, UUID budgetId,
			String budgetName) {
	}

	private final JdbcTemplate jdbc;

	public SmsLinks(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The user's links whose budget is not deleted, by digits. */
	public List<Link> of(UUID userId) {
		return jdbc.query("""
				select l.suffix, a.id, a.name, a.is_archived, b.id, b.name
				from msal.sms_account_links l
				join msal.accounts a on a.id = l.account_id
				join msal.budgets b on b.id = a.budget_id and b.deleted_at is null
				where l.user_id = ?
				order by l.suffix
				""", (rs, i) -> new Link(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getBoolean(4),
				rs.getObject(5, UUID.class), rs.getString(6)), userId);
	}

	public Optional<Link> find(UUID userId, String suffix) {
		return of(userId).stream().filter(l -> l.suffix().equals(suffix)).findFirst();
	}

	/** Links the digits to the account, or moves them to it. */
	public void save(UUID userId, String suffix, UUID accountId, Instant now) {
		jdbc.update("""
				insert into msal.sms_account_links (user_id, account_id, suffix, created_at) values (?, ?, ?, ?)
				on conflict (user_id, suffix) do update set account_id = excluded.account_id
				""", userId, accountId, suffix, Timestamp.from(now));
	}

	public int delete(UUID userId, String suffix) {
		return jdbc.update("delete from msal.sms_account_links where user_id = ? and suffix = ?", userId, suffix);
	}
}
