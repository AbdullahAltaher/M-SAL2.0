package com.abdullah.msal2.system.access;

import java.util.Set;
import java.util.UUID;

/**
 * The signed-in user of the current request. The id comes from the JWT only, never from the
 * request body (CLAUDE.md rule 4). Controllers receive it as a method parameter.
 */
public record CurrentUser(UUID id, String email, String timeZone, boolean disabled, Set<String> roles) {
}
