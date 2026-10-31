package com.abdullah.msal2.expenses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.abdullah.msal2.system.persistence.VersionedEntity;

/**
 * An expense or extra income (SRV-001). Its cycle is never stored: it comes from the date
 * (DATABASE.md D-13). Deleting it is a soft delete until it is purged from the trash.
 */
@Entity
@Table(name = "entries")
public class Entry extends VersionedEntity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "budget_id", nullable = false, updatable = false)
	private UUID budgetId;

	@Column(name = "account_id", nullable = false)
	private UUID accountId;

	@Column(name = "category_id", nullable = false)
	private UUID categoryId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EntryType type;

	@Column(nullable = false)
	private BigDecimal amount;

	@Column(nullable = false)
	private String name;

	@Column(name = "entry_date", nullable = false)
	private LocalDate entryDate;

	@Column(name = "legacy_id")
	private String legacyId;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Entry() {
	}

	public Entry(UUID userId, UUID budgetId, UUID accountId, UUID categoryId, EntryType type, BigDecimal amount,
			String name, LocalDate entryDate) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.budgetId = budgetId;
		this.accountId = accountId;
		this.categoryId = categoryId;
		this.type = type;
		this.amount = amount;
		this.name = name;
		this.entryDate = entryDate;
	}

	/** A copy dated today, with a new id and creation time (R-ENT-07). */
	public Entry copyFor(LocalDate today) {
		return new Entry(userId, budgetId, accountId, categoryId, type, amount, name, today);
	}

	public EntryData toData() {
		return new EntryData(id, type, amount, name, categoryId, entryDate, accountId, getCreatedAt(), deletedAt);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getBudgetId() {
		return budgetId;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public void setAccountId(UUID accountId) {
		this.accountId = accountId;
	}

	public UUID getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(UUID categoryId) {
		this.categoryId = categoryId;
	}

	public EntryType getType() {
		return type;
	}

	public void setType(EntryType type) {
		this.type = type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LocalDate getEntryDate() {
		return entryDate;
	}

	public void setEntryDate(LocalDate entryDate) {
		this.entryDate = entryDate;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public void softDelete(Instant now) {
		this.deletedAt = now;
	}

	public void restore() {
		this.deletedAt = null;
	}
}
