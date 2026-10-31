package com.abdullah.msal2.accounts;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SRV-004 requests and responses (API.md section 4).
 */
public final class AccountDtos {

	private AccountDtos() {
	}

	public record AccountResponse(UUID id, String name, boolean isArchived, int sortOrder, int version) {

		public static AccountResponse of(Account a) {
			return new AccountResponse(a.getId(), a.getName(), a.isArchived(), a.getSortOrder(), a.getVersion());
		}
	}

	public record CreateAccountRequest(@Size(max = 100) String name) {
	}

	public record UpdateAccountRequest(@Size(max = 100) String name, Boolean isArchived, @NotNull Integer version) {
	}
}
