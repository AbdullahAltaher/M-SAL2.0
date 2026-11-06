package com.abdullah.msal2.expenses;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

	List<Entry> findByAccountIdAndDeletedAtIsNull(UUID accountId);

	List<Entry> findByBudgetIdAndCategoryIdAndDeletedAtIsNull(UUID budgetId, UUID categoryId);

	List<Entry> findByBudgetIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(UUID budgetId);

	List<Entry> findByBudgetIdAndDeletedAtIsNullAndEntryDateBetween(UUID budgetId, LocalDate from, LocalDate to);

	/** The user's entry (deleted or not), if its budget is not deleted. */
	@Query("""
			select e from Entry e where e.id = :id and e.userId = :userId
			and exists (select 1 from Budget b where b.id = e.budgetId and b.deletedAt is null)
			""")
	Optional<Entry> findOwned(UUID id, UUID userId);
}
