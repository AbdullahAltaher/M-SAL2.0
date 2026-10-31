package com.abdullah.msal2.system.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * Adds {@code version} for optimistic locking (DATABASE.md D-11).
 * <p>
 * The version is {@code null} until the first save, which is how Spring Data tells a new
 * entity with an assigned id apart from an existing one.
 */
@MappedSuperclass
public abstract class VersionedEntity extends TimestampedEntity {

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public Integer getVersion() {
		return version;
	}
}
