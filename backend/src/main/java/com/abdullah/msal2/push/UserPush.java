package com.abdullah.msal2.push;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.abdullah.msal2.push.PushSubscriptions.Subscription;
import com.abdullah.msal2.push.WebPush.Outcome;

/**
 * Sends one notification to every device of a user: a device the push service no longer knows
 * is forgotten, and one that fails is only logged.
 */
@Component
public class UserPush {

	private static final Logger log = LoggerFactory.getLogger(UserPush.class);

	private final PushSubscriptions subscriptions;
	private final WebPush push;

	public UserPush(PushSubscriptions subscriptions, WebPush push) {
		this.subscriptions = subscriptions;
		this.push = push;
	}

	public boolean enabled() {
		return push.enabled();
	}

	/** Returns true when at least one device got it. */
	public boolean send(UUID userId, String payload, String what) {
		boolean sent = false;
		for (Subscription s : subscriptions.of(userId)) {
			Outcome outcome = push.send(s.endpoint(), s.p256dh(), s.auth(), payload);
			switch (outcome) {
			case SENT -> {
				subscriptions.used(s.id());
				sent = true;
			}
			case GONE -> subscriptions.gone(s.id());
			case FAILED -> log.warn("{} not delivered to one device of user {}", what, userId);
			}
		}
		return sent;
	}
}
