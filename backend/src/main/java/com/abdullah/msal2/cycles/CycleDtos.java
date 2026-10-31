package com.abdullah.msal2.cycles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * SRV-003 requests and responses (API.md section 4). Dates and amounts arrive as strings so a
 * wrong value gets its own error code (R-CYC-03) instead of a general one.
 */
public final class CycleDtos {

	private CycleDtos() {
	}

	public record CycleResponse(UUID id, LocalDate startDate, BigDecimal allowance, LocalDate expectedNextSalaryDate,
			LocalDate lastDay, boolean isOpen, int version) {
	}

	public record CyclesResponse(List<CycleResponse> cycles, boolean hasNoCycleEntries) {
	}

	public record PrefillResponse(LocalDate startDate, BigDecimal allowance) {
	}

	public record CreateCycleRequest(String startDate, String allowance, String expectedNextSalaryDate) {
	}

	public record UpdateCycleRequest(String startDate, String allowance, String expectedNextSalaryDate,
			@NotNull Integer version) {
	}

	public record MoveImpactResponse(int movedEntries) {
	}
}
