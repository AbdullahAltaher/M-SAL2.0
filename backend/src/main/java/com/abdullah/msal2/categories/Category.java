package com.abdullah.msal2.categories;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.abdullah.msal2.system.persistence.VersionedEntity;

/**
 * An expense category of a budget. Categories are hidden, never deleted, so old entries keep
 * theirs (R-CAT). {@code code} links a default category to its template (such as {@code cat_food}).
 */
@Entity
@Table(name = "categories")
public class Category extends VersionedEntity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "budget_id", nullable = false, updatable = false)
	private UUID budgetId;

	@Column(updatable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String emoji;

	@Column(name = "is_hidden", nullable = false)
	private boolean hidden;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "legacy_id")
	private String legacyId;

	protected Category() {
	}

	public Category(UUID userId, UUID budgetId, String code, String name, String emoji, int sortOrder) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.budgetId = budgetId;
		this.code = code;
		this.name = name;
		this.emoji = emoji;
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

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmoji() {
		return emoji;
	}

	public void setEmoji(String emoji) {
		this.emoji = emoji;
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}
}
