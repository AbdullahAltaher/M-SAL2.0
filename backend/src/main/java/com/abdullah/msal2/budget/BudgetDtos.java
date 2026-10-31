package com.abdullah.msal2.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SRV-002 requests and responses (API.md section 4). Amounts are strings in JSON; a budget
 * amount may be negative, and {@code null} means an empty field (R-BUD-03).
 */
public final class BudgetDtos {

	static final String AMOUNT = "^-?\\d{1,10}(\\.\\d{1,2})?$";

	private BudgetDtos() {
	}

	public record BudgetListItem(UUID id, String name, String monthLabel, int sortOrder, int version) {
	}

	public record BudgetResponse(UUID id, String name, String monthLabel, String salaryLabel, Salary salary,
			List<GroupResponse> groups, Totals totals, UUID lastAccountId, int version) {
	}

	public record Salary(BigDecimal amount, boolean isApprox) {
	}

	public record GroupResponse(UUID id, String title, int sortOrder, List<ItemResponse> items, GroupTotals totals,
			int version) {
	}

	public record ItemResponse(UUID id, String name, BigDecimal amount, boolean isApprox, boolean isDone, int sortOrder,
			int version) {

		static ItemResponse of(BudgetItem i) {
			return new ItemResponse(i.getId(), i.getName(), i.getAmount(), i.isApprox(), i.isDone(), i.getSortOrder(),
					i.getVersion());
		}
	}

	public record GroupTotals(BigDecimal subtotal, boolean subtotalApprox, BigDecimal balanceAfter,
			boolean balanceApprox) {
	}

	public record Totals(@JsonProperty("final") BigDecimal finalBalance, boolean finalApprox) {
	}

	public record CreateBudgetRequest(@Size(max = 100) String name) {
	}

	public record SalaryInput(@Pattern(regexp = AMOUNT, message = "SRV-002-E003") String amount, boolean isApprox) {
	}

	public record UpdateBudgetRequest(
			@Size(max = 100) String name,
			@Size(max = 100) String monthLabel,
			@Size(max = 100) String salaryLabel,
			@Valid SalaryInput salary,
			UUID lastAccountId,
			@NotNull Integer version) {
	}

	public record CreateGroupRequest(@Size(max = 100) String title) {
	}

	public record UpdateGroupRequest(@Size(max = 100) String title, @NotNull Integer version) {
	}

	public record UpdateItemRequest(
			@Size(max = 200) String name,
			@Pattern(regexp = AMOUNT, message = "SRV-002-E003") String amount,
			Boolean isApprox,
			Boolean isDone,
			@NotNull Integer version) {
	}
}
