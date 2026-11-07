package com.abdullah.msal2.banksms;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The words that suggest a category ({@code merchant_keywords}, V9, B-10). Stored cleaned
 * ({@link MerchantClassifier#clean}), one row per word in a budget.
 */
@Repository
public class MerchantKeywords {

	/** @param categoryId null = "اختر الفئة كل مرة" (B-15) */
	public record Row(UUID id, UUID budgetId, String keyword, UUID categoryId) {
	}

	private static final RowMapper<Row> ROW = (rs, i) -> new Row(rs.getObject("id", UUID.class),
			rs.getObject("budget_id", UUID.class), rs.getString("keyword"), rs.getObject("category_id", UUID.class));

	private final JdbcTemplate jdbc;

	public MerchantKeywords(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Row> ofBudget(UUID budgetId) {
		return jdbc.query("""
				select id, budget_id, keyword, category_id from msal.merchant_keywords
				where budget_id = ? order by keyword
				""", ROW, budgetId);
	}

	/** The user's words in budgets that are not deleted. */
	public List<Row> ofUser(UUID userId) {
		return jdbc.query("""
				select k.id, k.budget_id, k.keyword, k.category_id from msal.merchant_keywords k
				join msal.budgets b on b.id = k.budget_id and b.deleted_at is null
				where k.user_id = ? order by k.keyword
				""", ROW, userId);
	}

	public Optional<Row> owned(UUID id, UUID userId) {
		return jdbc.query("""
				select k.id, k.budget_id, k.keyword, k.category_id from msal.merchant_keywords k
				join msal.budgets b on b.id = k.budget_id and b.deleted_at is null
				where k.id = ? and k.user_id = ?
				""", ROW, id, userId).stream().findFirst();
	}

	/** Adds the word, or moves it to this category if the budget already has it (B-16). */
	public Row save(UUID userId, UUID budgetId, String keyword, UUID categoryId, Instant now) {
		return jdbc.queryForObject("""
				insert into msal.merchant_keywords (user_id, budget_id, keyword, category_id, created_at)
				values (?, ?, ?, ?, ?)
				on conflict (budget_id, keyword) do update set category_id = excluded.category_id
				returning id, budget_id, keyword, category_id
				""", ROW, userId, budgetId, keyword, categoryId, Timestamp.from(now));
	}

	public void delete(UUID id) {
		jdbc.update("delete from msal.merchant_keywords where id = ?", id);
	}
}
