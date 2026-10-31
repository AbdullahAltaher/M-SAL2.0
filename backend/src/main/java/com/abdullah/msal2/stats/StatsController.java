package com.abdullah.msal2.stats;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.cycles.CycleData;
import com.abdullah.msal2.cycles.CycleKey;
import com.abdullah.msal2.cycles.CycleSummary;
import com.abdullah.msal2.cycles.Ledger;
import com.abdullah.msal2.cycles.LedgerService;
import com.abdullah.msal2.expenses.EntryData;
import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.time.Today;

/**
 * SRV-006: the statistics of one cycle, or of "no cycle", of an account (R-STA-01 to R-STA-05).
 */
@RestController
@ServiceCode("SRV-006")
public class StatsController {

	/** How many category changes the screen shows (R-STA-05). */
	static final int CHANGES = 5;

	private final LedgerService ledgers;
	private final Clock clock;

	public StatsController(LedgerService ledgers, Clock clock) {
		this.ledgers = ledgers;
		this.clock = clock;
	}

	public record StatsResponse(BigDecimal total, BigDecimal dailyAverage, int count, BigDecimal extra,
			List<CategoryTotals.CategoryTotal> categories, List<Stats.DayTotal> lastDays, List<Stats.CycleTotal> trend,
			List<Stats.CategoryChange> changes, boolean hasPreviousCycle) {
	}

	@GetMapping("/accounts/{accountId}/stats")
	@Transactional(readOnly = true)
	public StatsResponse stats(CurrentUser user, @PathVariable UUID accountId,
			@RequestParam(defaultValue = CycleKey.LATEST) String cycle) {
		LocalDate today = Today.of(clock, user.timeZone());
		Ledger ledger = ledgers.load(user.id(), accountId);
		Optional<CycleData> selected = CycleKey.resolve(cycle, ledger.cycles());
		List<EntryData> list = CycleKey.entriesOf(selected, ledger.cycles(), ledger.entries());
		CycleSummary summary = selected.map(c -> CycleSummary.of(ledger.cycles(), c, ledger.entries(), today))
				.orElse(null);

		Stats.Overview overview = Stats.overview(list, summary, today);
		List<Stats.CycleTotal> trend = selected
				.map(c -> Stats.trend(ledger.cycles(), ledger.entries(), c.id(), Stats.TREND_CYCLES))
				.orElse(List.of());
		List<Stats.CategoryChange> changes = selected
				.map(c -> Stats.changes(ledger.cycles(), ledger.entries(), c.id()).stream().limit(CHANGES).toList())
				.orElse(List.of());
		boolean hasPrevious = trend.size() > 1;
		return new StatsResponse(overview.total(), overview.dailyAverage(), overview.count(), overview.extra(),
				CategoryTotals.of(list), Stats.lastDays(list, overview.firstDay(), overview.endDay(), Stats.LAST_DAYS),
				trend, changes, hasPrevious);
	}
}
