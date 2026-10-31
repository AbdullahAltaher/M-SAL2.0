package com.abdullah.msal2.accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	List<Account> findByBudgetIdOrderBySortOrderAscCreatedAtAsc(UUID budgetId);

	/** The user's account, if its budget is not deleted. */
	@Query("""
			select a from Account a where a.id = :id and a.userId = :userId
			and exists (select 1 from Budget b where b.id = a.budgetId and b.deletedAt is null)
			""")
	Optional<Account> findOwned(UUID id, UUID userId);

	@Query("select coalesce(max(a.sortOrder), 0) from Account a where a.budgetId = :budgetId")
	int maxSortOrder(UUID budgetId);
}
