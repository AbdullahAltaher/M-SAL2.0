package com.abdullah.msal2.banksms;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.banksms.BankSmsDtos.ApproveRequest;
import com.abdullah.msal2.banksms.BankSmsDtos.IngestRequest;
import com.abdullah.msal2.banksms.BankSmsDtos.IngestResponse;
import com.abdullah.msal2.banksms.BankSmsDtos.KeywordRequest;
import com.abdullah.msal2.banksms.BankSmsDtos.KeywordResponse;
import com.abdullah.msal2.banksms.BankSmsDtos.LinkRequest;
import com.abdullah.msal2.banksms.BankSmsDtos.NewTokenResponse;
import com.abdullah.msal2.banksms.BankSmsDtos.PendingEntryResponse;
import com.abdullah.msal2.banksms.BankSmsDtos.SettingsResponse;
import com.abdullah.msal2.expenses.EntryDtos.EntryResponse;
import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;
import com.abdullah.msal2.system.access.StrictRateLimit;

/**
 * SRV-011: bank messages (BANK-SMS.md 4.3 and 4.5, API.md SRV-011). {@code /sms/ingest} is called
 * by the iPhone shortcut with its personal token ({@link SmsSecurityConfig}); every other path
 * with the app's JWT.
 */
@RestController
@ServiceCode("SRV-011")
public class BankSmsController {

	private final SmsIngestService ingest;
	private final PendingEntryService pending;
	private final SmsSettingsService settings;

	public BankSmsController(SmsIngestService ingest, PendingEntryService pending, SmsSettingsService settings) {
		this.ingest = ingest;
		this.pending = pending;
		this.settings = settings;
	}

	// ── The shortcut ────────────────────────────────────────────

	@PostMapping(SmsSecurityConfig.INGEST_PATH)
	@StrictRateLimit
	public IngestResponse ingest(CurrentUser user, @Valid @RequestBody IngestRequest request) {
		return ingest.ingest(user, request.text());
	}

	// ── Review ──────────────────────────────────────────────────

	@GetMapping("/budgets/{budgetId}/pending-entries")
	public List<PendingEntryResponse> pending(CurrentUser user, @PathVariable UUID budgetId) {
		return pending.list(user.id(), budgetId);
	}

	@PostMapping("/pending-entries/{id}/approve")
	@ResponseStatus(HttpStatus.CREATED)
	public EntryResponse approve(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody ApproveRequest request) {
		return pending.approve(user.id(), id, request);
	}

	@DeleteMapping("/pending-entries/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void ignore(CurrentUser user, @PathVariable UUID id) {
		pending.ignore(user.id(), id);
	}

	// ── Settings ────────────────────────────────────────────────

	@GetMapping("/sms/settings")
	public SettingsResponse settings(CurrentUser user) {
		return settings.settings(user.id());
	}

	@PutMapping("/sms/links/{suffix}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void link(CurrentUser user, @PathVariable String suffix, @Valid @RequestBody LinkRequest request) {
		settings.link(user.id(), suffix, request.accountId());
	}

	@DeleteMapping("/sms/links/{suffix}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unlink(CurrentUser user, @PathVariable String suffix) {
		settings.unlink(user.id(), suffix);
	}

	@PostMapping("/sms/token")
	@StrictRateLimit
	@ResponseStatus(HttpStatus.CREATED)
	public NewTokenResponse newToken(CurrentUser user) {
		return settings.newToken(user.id());
	}

	@DeleteMapping("/sms/token")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revokeToken(CurrentUser user) {
		settings.revokeToken(user.id());
	}

	@PostMapping("/budgets/{budgetId}/merchant-keywords")
	@ResponseStatus(HttpStatus.CREATED)
	public KeywordResponse addKeyword(CurrentUser user, @PathVariable UUID budgetId,
			@Valid @RequestBody KeywordRequest request) {
		return settings.addKeyword(user.id(), budgetId, request);
	}

	@DeleteMapping("/merchant-keywords/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteKeyword(CurrentUser user, @PathVariable UUID id) {
		settings.deleteKeyword(user.id(), id);
	}
}
