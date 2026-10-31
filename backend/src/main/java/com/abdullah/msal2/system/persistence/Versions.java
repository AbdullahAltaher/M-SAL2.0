package com.abdullah.msal2.system.persistence;

import java.util.Objects;

import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;

/**
 * Optimistic locking from the client's side (API.md section 1): every change sends the
 * {@code version} it has, and a changed record is a conflict.
 */
public final class Versions {

	private Versions() {
	}

	/** Throws {@code SYS-E005} if the record changed since the client read it. */
	public static void check(VersionedEntity entity, Integer clientVersion) {
		if (!Objects.equals(entity.getVersion(), clientVersion)) {
			throw new ApiException(ErrorCode.SYS_E005);
		}
	}
}
