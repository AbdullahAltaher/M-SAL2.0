package com.abdullah.msal2.budget;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.abdullah.msal2.system.persistence.VersionedEntity;

/**
 * An item of the monthly ledger. An empty amount is {@code null} and counts as 0; "done" never
 * changes a total (R-BUD-01).
 */
@Entity
@Table(name = "budget_items")
public class BudgetItem extends VersionedEntity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "group_id", nullable = false, updatable = false)
	private UUID groupId;

	@Column(nullable = false)
	private String name = "";

	private BigDecimal amount;

	@Column(name = "is_approx", nullable = false)
	private boolean approx;

	@Column(name = "is_done", nullable = false)
	private boolean done;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "legacy_id")
	private String legacyId;

	protected BudgetItem() {
	}

	/** A new empty item (R-BUD-04). */
	public BudgetItem(UUID userId, UUID groupId, int sortOrder) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.groupId = groupId;
		this.sortOrder = sortOrder;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getGroupId() {
		return groupId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public boolean isApprox() {
		return approx;
	}

	public void setApprox(boolean approx) {
		this.approx = approx;
	}

	public boolean isDone() {
		return done;
	}

	public void setDone(boolean done) {
		this.done = done;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
