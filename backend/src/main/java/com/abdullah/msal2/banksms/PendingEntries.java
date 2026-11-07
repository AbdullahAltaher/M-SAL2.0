package com.abdullah.msal2.banksms;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Debits read from the bank's messages, waiting for review ({@code pending_entries}, V9). They
 * are not entries: home, the statistics and the budget never read this table (B-06).
 */
@Repository
public class PendingEntries {

	public record Row(UUID id, UUID userId, UUID budgetId, UUID accountId, BigDecimal amount, String merchant,
			UUID suggestedCategoryId, Instant receivedAt, LocalDate entryDate, String smsText, String smsHash) {

		/** For {@code audit_log}: everything but the message, which holds the balance (B-07). */
		public Map<String, Object> withoutText() {
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("accountId", accountId);
			data.put("amount", amount);
			data.put("merchant", merchant);
			data.put("suggestedCategoryId", suggestedCategoryId);
			data.put("receivedAt", receivedAt);
			data.put("entryDate", entryDate);
			return data;
		}
	}

	private static final String COLUMNS = """
			p.id, p.user_id, p.budget_id, p.account_id, p.amount, p.merchant, p.suggested_category_id, p.received_at,
			p.entry_date, p.sms_text, p.sms_hash
			""";

	private static final RowMapper<Row> ROW = (rs, i) -> new Row(rs.getObject(1, UUID.class),
			rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getBigDecimal(5),
			rs.getString(6), rs.getObject(7, UUID.class), rs.getTimestamp(8).toInstant(), rs.getDate(9).toLocalDate(),
			rs.getString(10), rs.getString(11));

	private final JdbcTemplate jdbc;

	public PendingEntries(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** False if the same message is already waiting (BANK-SMS.md 4.3, DUPLICATE). */
	public boolean insert(Row row) {
		return jdbc.update("""
				insert into msal.pending_entries (id, user_id, budget_id, account_id, amount, merchant,
				    suggested_category_id, received_at, entry_date, sms_text, sms_hash, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				on conflict (user_id, sms_hash) do nothing
				""", row.id(), row.userId(), row.budgetId(), row.accountId(), row.amount(), row.merchant(),
				row.suggestedCategoryId(), Timestamp.from(row.receivedAt()), Date.valueOf(row.entryDate()), row.smsText(),
				row.smsHash(), Timestamp.from(row.receivedAt())) == 1;
	}

	/** Newest first. */
	public List<Row> ofBudget(UUID budgetId) {
		return jdbc.query("select " + COLUMNS + " from msal.pending_entries p where p.budget_id = ?"
				+ " order by p.received_at desc, p.id", ROW, budgetId);
	}

	/** The user's pending entry, if its budget is not deleted. */
	public Optional<Row> owned(UUID id, UUID userId) {
		return jdbc.query("select " + COLUMNS + """
				 from msal.pending_entries p
				join msal.budgets b on b.id = p.budget_id and b.deleted_at is null
				where p.id = ? and p.user_id = ?
				""", ROW, id, userId).stream().findFirst();
	}

	public int count(UUID budgetId) {
		Integer n = jdbc.queryForObject("select count(*) from msal.pending_entries where budget_id = ?", Integer.class,
				budgetId);
		return n == null ? 0 : n;
	}

	public void delete(UUID id) {
		jdbc.update("delete from msal.pending_entries where id = ?", id);
	}
}
