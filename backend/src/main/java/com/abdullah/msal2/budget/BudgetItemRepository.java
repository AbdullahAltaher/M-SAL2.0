package com.abdullah.msal2.budget;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BudgetItemRepository extends JpaRepository<BudgetItem, UUID> {

	List<BudgetItem> findByGroupIdInOrderBySortOrderAscCreatedAtAsc(Collection<UUID> groupIds);

	List<BudgetItem> findByGroupIdOrderBySortOrderAscCreatedAtAsc(UUID groupId);

	/** The user's item, if its budget is not deleted. */
	@Query("""
			select i from BudgetItem i where i.id = :id and i.userId = :userId
			and exists (select 1 from BudgetGroup g, Budget b
			            where g.id = i.groupId and b.id = g.budgetId and b.deletedAt is null)
			""")
	Optional<BudgetItem> findOwned(UUID id, UUID userId);

	@Query("select coalesce(max(i.sortOrder), 0) from BudgetItem i where i.groupId = :groupId")
	int maxSortOrder(UUID groupId);
}
