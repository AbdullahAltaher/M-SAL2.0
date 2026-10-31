package com.abdullah.msal2.categories;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One of the 20 default categories (DATABASE.md D-10). Every new budget gets a copy.
 */
@Entity
@Immutable
@Table(name = "category_templates")
public class CategoryTemplate {

	@Id
	private String code;

	@Column(name = "name_ar", nullable = false)
	private String nameAr;

	@Column(nullable = false)
	private String emoji;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "default_suggestions", nullable = false)
	private String[] defaultSuggestions;

	protected CategoryTemplate() {
	}

	public String getCode() {
		return code;
	}

	public String getNameAr() {
		return nameAr;
	}

	public String getEmoji() {
		return emoji;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	/** Starter suggestions with no history yet (R-ENT-12), such as ريوق، غدا، عشا. */
	public List<String> getDefaultSuggestions() {
		return List.of(defaultSuggestions);
	}
}
