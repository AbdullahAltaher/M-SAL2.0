package com.abdullah.msal2.receipt;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.categories.CategoryService;
import com.abdullah.msal2.cycles.CycleData;
import com.abdullah.msal2.cycles.CycleKey;
import com.abdullah.msal2.cycles.Ledger;
import com.abdullah.msal2.cycles.LedgerService;
import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.time.Today;

/**
 * SRV-008: the data of the expenses receipt (R-RCP-01). Angular draws the picture. The monthly
 * budget receipt (R-RCP-02) is drawn from {@code GET /budgets/{id}}.
 */
@RestController
@ServiceCode("SRV-008")
public class ReceiptController {

	private final LedgerService ledgers;
	private final CategoryService categories;
	private final Clock clock;

	public ReceiptController(LedgerService ledgers, CategoryService categories, Clock clock) {
		this.ledgers = ledgers;
		this.categories = categories;
		this.clock = clock;
	}

	@GetMapping("/accounts/{accountId}/receipt")
	@Transactional(readOnly = true)
	public ReceiptModel receipt(CurrentUser user, @PathVariable UUID accountId,
			@RequestParam(defaultValue = CycleKey.LATEST) String cycle) {
		Ledger ledger = ledgers.load(user.id(), accountId);
		UUID cycleId = CycleKey.resolve(cycle, ledger.cycles()).map(CycleData::id).orElse(null);
		Map<UUID, String> names = categories.names(ledger.account().getBudgetId());
		return ReceiptModel.build(ledger.account().getName(), ledger.cycles(), ledger.entries(), cycleId,
				Today.of(clock, user.timeZone()), id -> names.getOrDefault(id, CategoryService.FALLBACK_NAME));
	}
}
