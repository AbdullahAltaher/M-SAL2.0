package com.abdullah.msal2.cycles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abdullah.msal2.cycles.CycleDtos.CreateCycleRequest;
import com.abdullah.msal2.cycles.CycleDtos.CycleResponse;
import com.abdullah.msal2.cycles.CycleDtos.CyclesResponse;
import com.abdullah.msal2.cycles.CycleDtos.PrefillResponse;
import com.abdullah.msal2.cycles.CycleDtos.UpdateCycleRequest;
import com.abdullah.msal2.system.audit.AuditAction;
import com.abdullah.msal2.system.audit.AuditService;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.persistence.Versions;
import com.abdullah.msal2.system.web.Inputs;
import com.abdullah.msal2.system.web.Patch;

/**
 * SRV-003: salary cycles of an account (R-CYC-01 to R-CYC-03, R-CYC-07, R-CYC-08).
 * Entries never change when cycles do: they follow their dates.
 */
@Service
public class CycleService {

	private final SalaryCycleRepository cycles;
	private final LedgerService ledgers;
	private final AuditService audit;

	public CycleService(SalaryCycleRepository cycles, LedgerService ledgers, AuditService audit) {
		this.cycles = cycles;
		this.ledgers = ledgers;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public CyclesResponse list(UUID userId, UUID accountId) {
		Ledger ledger = ledgers.load(userId, accountId);
		List<CycleResponse> list = cycles.findByAccountId(accountId).stream()
				.sorted(Comparator.comparing(SalaryCycle::toData, Cycles.ORDER))
				.map(c -> toResponse(ledger.cycles(), c))
				.toList();
		boolean hasNoCycle = !Cycles.bucket(ledger.cycles(), ledger.entries()).noCycle().isEmpty();
		return new CyclesResponse(list, hasNoCycle);
	}

	/** "Salary arrived": today and the latest allowance (R-CYC-02). */
	@Transactional(readOnly = true)
	public PrefillResponse prefill(UUID userId, UUID accountId, LocalDate today) {
		Cycles.Prefill prefill = Cycles.prefill(ledgers.load(userId, accountId).cycles(), today);
		return new PrefillResponse(prefill.startDate(), prefill.allowance());
	}

	@Transactional
	public CycleResponse create(UUID userId, UUID accountId, CreateCycleRequest request) {
		Ledger ledger = ledgers.load(userId, accountId);
		LocalDate start = Inputs.date(request.startDate(), ErrorCode.SRV_003_E001);
		BigDecimal allowance = Inputs.allowance(request.allowance());
		LocalDate expected = expectedDate(request.expectedNextSalaryDate(), start);
		if (Cycles.isDuplicateDate(ledger.cycles(), start, null)) {
			throw new ApiException(ErrorCode.SRV_003_E004);
		}
		SalaryCycle cycle = save(new SalaryCycle(userId, accountId, start, allowance, expected));
		audit.record(userId, AuditAction.CREATE, "salary_cycle", cycle.getId(), null, snapshot(cycle));
		return toResponse(withCycle(ledger.cycles(), cycle), cycle);
	}

	/** Changes the date, allowance or next salary date ({@code null} clears it, R-CYC-07). */
	@Transactional
	public CycleResponse update(UUID userId, UUID cycleId, Patch<UpdateCycleRequest> patch) {
		SalaryCycle cycle = owned(userId, cycleId);
		UpdateCycleRequest request = patch.value();
		Versions.check(cycle, request.version());
		Ledger ledger = ledgers.load(userId, cycle.getAccountId());
		Map<String, Object> before = snapshot(cycle);

		if (patch.has("startDate")) {
			LocalDate start = Inputs.date(request.startDate(), ErrorCode.SRV_003_E001);
			if (Cycles.isDuplicateDate(ledger.cycles(), start, cycleId)) {
				throw new ApiException(ErrorCode.SRV_003_E004);
			}
			cycle.setStartDate(start);
		}
		if (patch.has("allowance")) {
			cycle.setAllowance(Inputs.allowance(request.allowance()));
		}
		if (patch.has("expectedNextSalaryDate")) {
			cycle.setExpectedNextSalaryDate(expectedDate(request.expectedNextSalaryDate(), cycle.getStartDate()));
		}
		else if (cycle.getExpectedNextSalaryDate() != null && !cycle.getExpectedNextSalaryDate().isAfter(cycle.getStartDate())) {
			throw new ApiException(ErrorCode.SRV_003_E003);
		}
		SalaryCycle saved = save(cycle);
		audit.record(userId, AuditAction.UPDATE, "salary_cycle", cycleId, before, snapshot(saved));
		return toResponse(withCycle(ledger.cycles(), saved), saved);
	}

	/** How many entries would change cycle with a new start date (R-CYC-07). */
	@Transactional(readOnly = true)
	public int moveImpact(UUID userId, UUID cycleId, String startDate) {
		SalaryCycle cycle = owned(userId, cycleId);
		LocalDate date = Inputs.date(startDate, ErrorCode.SRV_003_E001);
		Ledger ledger = ledgers.load(userId, cycle.getAccountId());
		return Cycles.movedEntryCount(ledger.cycles(), ledger.entries(), cycleId, date);
	}

	/** Its entries move to the previous cycle or to "no cycle"; none is deleted (R-CYC-08). */
	@Transactional
	public void delete(UUID userId, UUID cycleId) {
		SalaryCycle cycle = owned(userId, cycleId);
		Map<String, Object> before = snapshot(cycle);
		cycles.delete(cycle);
		cycles.flush();
		audit.record(userId, AuditAction.DELETE, "salary_cycle", cycleId, before, null);
	}

	private SalaryCycle owned(UUID userId, UUID cycleId) {
		return cycles.findOwned(cycleId, userId).orElseThrow(() -> new ApiException(ErrorCode.SYS_E004));
	}

	/** Two saves at once can still meet the unique (account, start date) index. */
	private SalaryCycle save(SalaryCycle cycle) {
		try {
			return cycles.saveAndFlush(cycle);
		}
		catch (DataIntegrityViolationException e) {
			throw new ApiException(ErrorCode.SRV_003_E004);
		}
	}

	/** Optional, and after the start (R-CYC-03). An empty string clears it like null. */
	private static LocalDate expectedDate(String value, LocalDate start) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		LocalDate expected = Inputs.date(value, ErrorCode.SRV_003_E001);
		if (!expected.isAfter(start)) {
			throw new ApiException(ErrorCode.SRV_003_E003);
		}
		return expected;
	}

	private static List<CycleData> withCycle(List<CycleData> cycles, SalaryCycle changed) {
		List<CycleData> list = new ArrayList<>(cycles.stream().filter(c -> !c.id().equals(changed.getId())).toList());
		list.add(changed.toData());
		return list;
	}

	private static CycleResponse toResponse(List<CycleData> all, SalaryCycle cycle) {
		Cycles.Bounds bounds = Cycles.bounds(all, cycle.toData());
		return new CycleResponse(cycle.getId(), cycle.getStartDate(), cycle.getAllowance(),
				cycle.getExpectedNextSalaryDate(), bounds.lastDay(), bounds.open(), cycle.getVersion());
	}

	private static Map<String, Object> snapshot(SalaryCycle cycle) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("accountId", cycle.getAccountId());
		data.put("startDate", cycle.getStartDate());
		data.put("allowance", cycle.getAllowance());
		data.put("expectedNextSalaryDate", cycle.getExpectedNextSalaryDate());
		return data;
	}
}
