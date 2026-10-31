package com.abdullah.msal2.budget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BudgetGroupRepository extends JpaRepository<BudgetGroup, UUID> {

	List<BudgetGroup> findByBudgetIdOrderBySortOrderAscCreatedAtAsc(UUID budgetId);

	/** The user's group, if its budget is not deleted. */
	@Query("""
			select g from BudgetGroup g where g.id = :id and g.userId = :userId
			and exists (select 1 from Budget b where b.id = g.budgetId and b.deletedAt is null)
			""")
	Optional<BudgetGroup> findOwned(UUID id, UUID userId);

	@Query("select coalesce(max(g.sortOrder), 0) from BudgetGroup g where g.budgetId = :budgetId")
	int maxSortOrder(UUID budgetId);
}
