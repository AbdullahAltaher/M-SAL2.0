package com.abdullah.msal2.categories;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SRV-005 requests and responses (API.md section 4).
 */
public final class CategoryDtos {

	private CategoryDtos() {
	}

	public record CategoryResponse(UUID id, String code, String name, String emoji, boolean isHidden, int sortOrder,
			int version) {

		static CategoryResponse of(Category c) {
			return new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getEmoji(), c.isHidden(), c.getSortOrder(),
					c.getVersion());
		}
	}

	public record CreateCategoryRequest(@Size(max = 100) String name, @Size(max = 16) String emoji) {
	}

	public record UpdateCategoryRequest(@Size(max = 100) String name, @Size(max = 16) String emoji, Boolean isHidden,
			@NotNull Integer version) {
	}

	public record OrderRequest(@NotNull List<UUID> categoryIds) {
	}
}
