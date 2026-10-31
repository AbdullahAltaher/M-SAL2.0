package com.abdullah.msal2.budget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

	List<Budget> findByUserIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID userId);

	/** The user's budget, if it is not deleted. Anyone else's is "not found". */
	Optional<Budget> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

	long countByUserIdAndDeletedAtIsNull(UUID userId);

	@Query("select coalesce(max(b.sortOrder), 0) from Budget b where b.userId = :userId")
	int maxSortOrder(UUID userId);
}
