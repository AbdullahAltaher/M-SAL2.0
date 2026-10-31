package com.abdullah.msal2.categories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	List<Category> findByBudgetIdOrderBySortOrderAscCreatedAtAsc(UUID budgetId);

	/** The user's category, if its budget is not deleted. */
	@Query("""
			select c from Category c where c.id = :id and c.userId = :userId
			and exists (select 1 from Budget b where b.id = c.budgetId and b.deletedAt is null)
			""")
	Optional<Category> findOwned(UUID id, UUID userId);

	@Query("select coalesce(max(c.sortOrder), 0) from Category c where c.budgetId = :budgetId")
	int maxSortOrder(UUID budgetId);
}
