package com.abdullah.msal2.cycles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SalaryCycleRepository extends JpaRepository<SalaryCycle, UUID> {

	List<SalaryCycle> findByAccountId(UUID accountId);

	/** The user's cycle, if its account's budget is not deleted. */
	@Query("""
			select c from SalaryCycle c where c.id = :id and c.userId = :userId
			and exists (select 1 from Account a, Budget b
			            where a.id = c.accountId and b.id = a.budgetId and b.deletedAt is null)
			""")
	Optional<SalaryCycle> findOwned(UUID id, UUID userId);
}
