package com.abdullah.msal2.accounts;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.abdullah.msal2.system.persistence.VersionedEntity;

/**
 * An account of a budget, such as "مصاريفي". It owns its salary cycles. Accounts are archived,
 * never deleted (DATABASE.md D-07).
 */
@Entity
@Table(name = "accounts")
public class Account extends VersionedEntity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "budget_id", nullable = false, updatable = false)
	private UUID budgetId;

	@Column(nullable = false)
	private String name;

	@Column(name = "is_archived", nullable = false)
	private boolean archived;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "legacy_id")
	private String legacyId;

	protected Account() {
	}

	public Account(UUID userId, UUID budgetId, String name, int sortOrder) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.budgetId = budgetId;
		this.name = name;
		this.sortOrder = sortOrder;
	}

	public AccountRules.AccountData toData() {
		return new AccountRules.AccountData(id, name, archived);
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isArchived() {
		return archived;
	}

	public void setArchived(boolean archived) {
		this.archived = archived;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
