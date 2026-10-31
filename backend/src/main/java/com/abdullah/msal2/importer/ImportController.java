package com.abdullah.msal2.importer;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.abdullah.msal2.importer.ImportDtos.ImportResponse;
import com.abdullah.msal2.importer.ImportDtos.PreviewResponse;
import com.abdullah.msal2.importer.ImportService.Mode;
import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.access.StrictRateLimit;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;
import com.abdullah.msal2.system.time.Today;
import com.abdullah.msal2.system.web.RequestSizeFilter;

/**
 * SRV-007: export and import (API.md SRV-007, R-IO-01 to R-IO-05). The file is sent as
 * multipart {@code file}; the preview saves nothing, and the import sends the same file again.
 */
@RestController
@ServiceCode("SRV-007")
public class ImportController {

	private final ExportService export;
	private final ImportParser parser;
	private final ImportService imports;
	private final ImportLog log;
	private final Clock clock;

	public ImportController(ExportService export, ImportParser parser, ImportService imports, ImportLog log,
			Clock clock) {
		this.export = export;
		this.parser = parser;
		this.imports = imports;
		this.log = log;
		this.clock = clock;
	}

	/** {@code ميزانية-YYYY-MM-DD.json} (R-IO-01); marks the export for the reminder. */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(CurrentUser user) {
		byte[] body = log.exported(user.id(), export.export(user.id()));
		String name = "ميزانية-" + Today.of(clock, user.timeZone()) + ".json";
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(name, java.nio.charset.StandardCharsets.UTF_8).build().toString())
				.body(body);
	}

	@PostMapping("/imports/preview")
	public PreviewResponse preview(CurrentUser user, @RequestPart("file") MultipartFile file) {
		ImportParser.Result parsed = parse(user, file);
		ImportDtos.Target target = parsed.file().kind() == ImportFile.Kind.SINGLE ? imports.currentBudget(user.id())
				: null;
		return new PreviewResponse(parsed.file().kind(), parsed.file().newer(), parsed.counts(),
				imports.current(user.id()), parsed.file().warnings().list(),
				target);
	}

	@PostMapping("/imports")
	@StrictRateLimit
	public ImportResponse importFile(CurrentUser user, @RequestParam Mode mode, @RequestPart("file") MultipartFile file) {
		ImportParser.Result parsed = parse(user, file);
		return log.get(user.id(), imports.apply(user, parsed, mode, file.getOriginalFilename(), bytes(file)));
	}

	@GetMapping("/imports")
	public List<ImportResponse> list(CurrentUser user) {
		return log.list(user.id());
	}

	private ImportParser.Result parse(CurrentUser user, MultipartFile file) {
		return parser.parse(bytes(file), ZoneId.of(user.timeZone()), Instant.now(clock));
	}

	private static byte[] bytes(MultipartFile file) {
		if (file.getSize() > RequestSizeFilter.MAX_IMPORT_BYTES) {
			throw new ApiException(ErrorCode.SRV_007_E003);
		}
		try {
			return file.getBytes();
		}
		catch (IOException e) {
			throw new ApiException(ErrorCode.SRV_007_E001);
		}
	}
}
