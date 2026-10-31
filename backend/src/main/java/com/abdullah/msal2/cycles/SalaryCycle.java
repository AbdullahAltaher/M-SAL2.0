package com.abdullah.msal2.cycles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.abdullah.msal2.system.persistence.VersionedEntity;

/**
 * A salary cycle of an account (SRV-003). It ends where the next one starts, so the end is
 * never stored (R-CYC-01).
 */
@Entity
@Table(name = "salary_cycles")
public class SalaryCycle extends VersionedEntity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private BigDecimal allowance;

	@Column(name = "expected_next_salary_date")
	private LocalDate expectedNextSalaryDate;

	@Column(name = "legacy_id")
	private String legacyId;

	protected SalaryCycle() {
	}

	public SalaryCycle(UUID userId, UUID accountId, LocalDate startDate, BigDecimal allowance,
			LocalDate expectedNextSalaryDate) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.accountId = accountId;
		this.startDate = startDate;
		this.allowance = allowance;
		this.expectedNextSalaryDate = expectedNextSalaryDate;
	}

	public CycleData toData() {
		return new CycleData(id, startDate, allowance, expectedNextSalaryDate);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public BigDecimal getAllowance() {
		return allowance;
	}

	public void setAllowance(BigDecimal allowance) {
		this.allowance = allowance;
	}

	public LocalDate getExpectedNextSalaryDate() {
		return expectedNextSalaryDate;
	}

	public void setExpectedNextSalaryDate(LocalDate expectedNextSalaryDate) {
		this.expectedNextSalaryDate = expectedNextSalaryDate;
	}
}
