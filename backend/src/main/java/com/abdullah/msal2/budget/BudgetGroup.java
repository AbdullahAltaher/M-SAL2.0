package com.abdullah.msal2.budget;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.abdullah.msal2.system.persistence.VersionedEntity;

/**
 * A group of the monthly ledger. Its number ({@code 01}, {@code 02}) comes from its order.
 */
@Entity
@Table(name = "budget_groups")
public class BudgetGroup extends VersionedEntity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "budget_id", nullable = false, updatable = false)
	private UUID budgetId;

	@Column(nullable = false)
	private String title = "";

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "legacy_id")
	private String legacyId;

	protected BudgetGroup() {
	}

	public BudgetGroup(UUID userId, UUID budgetId, String title, int sortOrder) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.budgetId = budgetId;
		this.title = title;
		this.sortOrder = sortOrder;
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

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
