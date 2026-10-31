package com.abdullah.msal2.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.abdullah.msal2.system.persistence.VersionedEntity;

/**
 * A budget ("a person" in M-SAL). It owns the monthly ledger, the accounts, the categories
 * and the entries. Deleting it is a soft delete (DATABASE.md D-07).
 */
@Entity
@Table(name = "budgets")
public class Budget extends VersionedEntity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false)
	private String name;

	@Column(name = "month_label", nullable = false)
	private String monthLabel = "";

	@Column(name = "salary_label", nullable = false)
	private String salaryLabel = "الراتب";

	@Column(name = "salary_amount")
	private BigDecimal salaryAmount;

	@Column(name = "salary_is_approx", nullable = false)
	private boolean salaryIsApprox;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "last_account_id")
	private UUID lastAccountId;

	@Column(name = "legacy_id")
	private String legacyId;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Budget() {
	}

	public Budget(UUID userId, String name, String monthLabel, int sortOrder) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.name = name;
		this.monthLabel = monthLabel;
		this.sortOrder = sortOrder;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getMonthLabel() {
		return monthLabel;
	}

	public void setMonthLabel(String monthLabel) {
		this.monthLabel = monthLabel;
	}

	public String getSalaryLabel() {
		return salaryLabel;
	}

	public void setSalaryLabel(String salaryLabel) {
		this.salaryLabel = salaryLabel;
	}

	public BigDecimal getSalaryAmount() {
		return salaryAmount;
	}

	public boolean isSalaryIsApprox() {
		return salaryIsApprox;
	}

	public void setSalary(BigDecimal amount, boolean approx) {
		this.salaryAmount = amount;
		this.salaryIsApprox = approx;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public UUID getLastAccountId() {
		return lastAccountId;
	}

	public void setLastAccountId(UUID lastAccountId) {
		this.lastAccountId = lastAccountId;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public void softDelete(Instant now) {
		this.deletedAt = now;
	}
}
