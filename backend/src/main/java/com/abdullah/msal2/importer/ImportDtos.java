package com.abdullah.msal2.importer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.abdullah.msal2.importer.ImportFile.Kind;
import com.abdullah.msal2.importer.ImportParser.Counts;
import com.abdullah.msal2.importer.ImportService.Mode;
import com.abdullah.msal2.importer.Warnings.Warning;

/**
 * SRV-007 request and response bodies (API.md SRV-007).
 */
public final class ImportDtos {

	private ImportDtos() {
	}

	/**
	 * What the file holds next to what the user has now. {@code target} is the budget a
	 * one-person file goes into (SINGLE only).
	 */
	public record PreviewResponse(Kind kind, boolean newer, Counts counts, Counts current, List<Warning> warnings,
			Target target) {
	}

	public record Target(UUID id, String name) {
	}

	public record ImportResponse(UUID id, String fileName, Mode mode, Counts counts, List<Warning> warnings,
			Instant createdAt) {
	}
}
