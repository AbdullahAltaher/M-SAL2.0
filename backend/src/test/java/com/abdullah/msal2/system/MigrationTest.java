package com.abdullah.msal2.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.abdullah.msal2.IntegrationTest;

/**
 * The Flyway migrations (DATABASE.md section 7) on a real Postgres 17.
 */
class MigrationTest extends IntegrationTest {

	@Test
	void allMigrationsApplied() {
		assertThat(jdbc.queryForList(
				// version is null on Flyway's own "create schema" row
				"select version from msal.flyway_schema_history where success and version is not null order by installed_rank",
				String.class))
				.containsExactly("1", "2", "3", "4", "5", "6");
	}

	@Test
	void everyTableHasRowLevelSecurity() {
		// DATABASE.md D-02: RLS on every table, so nothing leaks if the schema is ever exposed
		assertThat(jdbc.queryForList("""
				select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
				where n.nspname = 'msal' and c.relkind = 'r'
				  and c.relname <> 'flyway_schema_history' and not c.relrowsecurity
				""", String.class)).isEmpty();
	}

	@Test
	void referenceDataIsSeeded() {
		assertThat(jdbc.queryForObject("select count(*) from msal.services", Integer.class)).isEqualTo(10);
		assertThat(jdbc.queryForList("select code from msal.roles order by code", String.class))
				.containsExactly("ADMIN", "USER");
		assertThat(jdbc.queryForObject("select count(*) from msal.category_templates", Integer.class)).isEqualTo(20);
		assertThat(jdbc.queryForObject(
				"select default_suggestions from msal.category_templates where code = 'cat_food'", String.class))
				.isEqualTo("{ريوق,غدا,عشا}");
	}

	@Test
	void entryCannotUseAnotherUsersAccount() {
		// DATABASE.md D-04: the composite foreign keys stop cross-user links even if the code is wrong
		UUID alice = insertUser("alice@test.local");
		UUID bob = insertUser("bob@test.local");
		UUID aliceBudget = insertBudget(alice, "أليس");
		UUID bobBudget = insertBudget(bob, "بوب");
		UUID bobAccount = jdbc.queryForObject(
				"insert into msal.accounts (user_id, budget_id, name) values (?, ?, 'مصاريفي') returning id",
				UUID.class, bob, bobBudget);
		UUID aliceCategory = jdbc.queryForObject(
				"insert into msal.categories (user_id, budget_id, name) values (?, ?, 'أخرى') returning id",
				UUID.class, alice, aliceBudget);

		assertThatThrownBy(() -> jdbc.update("""
				insert into msal.entries (user_id, budget_id, account_id, category_id, type, amount, entry_date)
				values (?, ?, ?, ?, 'EXPENSE', 10, current_date)
				""", alice, aliceBudget, bobAccount, aliceCategory))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void groupCannotPointToAnotherUsersBudget() {
		UUID alice = insertUser("alice@test.local");
		UUID bob = insertUser("bob@test.local");
		UUID bobBudget = insertBudget(bob, "بوب");

		assertThatThrownBy(() -> jdbc.update(
				"insert into msal.budget_groups (user_id, budget_id, title) values (?, ?, 'x')", alice, bobBudget))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void entryAmountMustBePositive() {
		UUID alice = insertUser("alice@test.local");
		UUID budget = insertBudget(alice, "أليس");
		UUID account = jdbc.queryForObject(
				"insert into msal.accounts (user_id, budget_id, name) values (?, ?, 'مصاريفي') returning id",
				UUID.class, alice, budget);
		UUID category = jdbc.queryForObject(
				"insert into msal.categories (user_id, budget_id, name) values (?, ?, 'أخرى') returning id",
				UUID.class, alice, budget);

		assertThatThrownBy(() -> jdbc.update("""
				insert into msal.entries (user_id, budget_id, account_id, category_id, type, amount, entry_date)
				values (?, ?, ?, ?, 'EXPENSE', 0, current_date)
				""", alice, budget, account, category))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
