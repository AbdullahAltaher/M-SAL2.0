package com.abdullah.msal2.users;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import com.abdullah.msal2.system.persistence.VersionedEntity;

/**
 * A user of the app. The id is the Supabase Auth user id ({@code sub} in the JWT, DATABASE.md D-03).
 * Users are never deleted, only disabled, so {@code audit_log} stays intact.
 */
@Entity
@Table(name = "app_users")
public class AppUser extends VersionedEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String email;

	@Column(name = "display_name")
	private String displayName;

	@Column(name = "time_zone", nullable = false)
	private String timeZone = "Asia/Dubai";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status = UserStatus.ACTIVE;

	@Column(name = "last_budget_id")
	private UUID lastBudgetId;

	@Column(name = "last_export_at")
	private Instant lastExportAt;

	/** In the user's time zone; null when the daily reminder is off (V8) */
	@Column(name = "daily_reminder_time")
	private LocalTime dailyReminderTime;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "role_code", nullable = false)
	private Set<String> roles = new HashSet<>();

	protected AppUser() {
	}

	public AppUser(UUID id, String email, Set<String> roles) {
		this.id = id;
		this.email = email;
		this.roles = new HashSet<>(roles);
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public UUID getLastBudgetId() {
		return lastBudgetId;
	}

	public void setLastBudgetId(UUID lastBudgetId) {
		this.lastBudgetId = lastBudgetId;
	}

	public Instant getLastExportAt() {
		return lastExportAt;
	}

	public LocalTime getDailyReminderTime() {
		return dailyReminderTime;
	}

	public void setDailyReminderTime(LocalTime dailyReminderTime) {
		this.dailyReminderTime = dailyReminderTime;
	}

	public Set<String> getRoles() {
		return Set.copyOf(roles);
	}

	public void setRoles(Set<String> roles) {
		this.roles.clear();
		this.roles.addAll(roles);
	}
}
