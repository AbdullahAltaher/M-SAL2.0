package com.abdullah.msal2.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.abdullah.msal2.system.ServiceCode;
import com.abdullah.msal2.system.access.CurrentUser;

/**
 * SRV-000: this device's notifications for the daily reminder (API.md SRV-000). The body is
 * the browser's own {@code PushSubscription.toJSON()}.
 */
@RestController
@ServiceCode("SRV-000")
@RequestMapping("/me/push-subscriptions")
public class PushController {

	public record Keys(String p256dh, String auth) {
	}

	public record SubscribeRequest(@NotNull String endpoint, @NotNull Keys keys) {
	}

	private final PushSubscriptions subscriptions;

	public PushController(PushSubscriptions subscriptions) {
		this.subscriptions = subscriptions;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void subscribe(CurrentUser user, @Valid @RequestBody SubscribeRequest request,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		subscriptions.save(user.id(), request.endpoint(), request.keys().p256dh(), request.keys().auth(), userAgent);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unsubscribe(CurrentUser user, @RequestParam String endpoint) {
		subscriptions.delete(user.id(), endpoint);
	}
}
