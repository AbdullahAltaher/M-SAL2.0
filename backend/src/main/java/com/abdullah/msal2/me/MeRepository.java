package com.abdullah.msal2.me;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only checks on the user's data for {@code /me}.
 */
@Repository
public class MeRepository {

	/**
	 * Whether the user has data to lose, like M-SAL's {@code contentScore > 0}: a second budget,
	 * a salary, a budget item with a name or amount, an entry, a cycle, or a second account.
	 */
	private static final String HAS_DATA = """
			select (select count(*) from msal.budgets where user_id = :userId and deleted_at is null) > 1
			    or exists (select 1 from msal.budgets
			               where user_id = :userId and deleted_at is null and salary_amount is not null)
			    or exists (select 1 from msal.budget_items
			               where user_id = :userId and (btrim(name) <> '' or amount is not null))
			    or exists (select 1 from msal.entries where user_id = :userId)
			    or exists (select 1 from msal.salary_cycles where user_id = :userId)
			    or exists (select 1 from msal.accounts where user_id = :userId
			               group by budget_id having count(*) > 1)
			""";

	private static final String OWNS_BUDGET = """
			select exists (select 1 from msal.budgets
			               where id = :budgetId and user_id = :userId and deleted_at is null)
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public MeRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public boolean hasData(UUID userId) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject(HAS_DATA, new MapSqlParameterSource("userId", userId), Boolean.class));
	}

	public boolean ownsBudget(UUID userId, UUID budgetId) {
		MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("budgetId", budgetId);
		return Boolean.TRUE.equals(jdbc.queryForObject(OWNS_BUDGET, params, Boolean.class));
	}
}
