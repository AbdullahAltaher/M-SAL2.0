import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { SwUpdate } from '@angular/service-worker';

/**
 * The app's own state for the banners (SCREENS.md section 7): no connection, and a new
 * version ready. Also clears the number on the app icon when the app is opened.
 */
@Injectable({ providedIn: 'root' })
export class AppStatus {
  /** Absent in tests and in development (no worker there) */
  private readonly updates = inject(SwUpdate, { optional: true });
  private readonly document = inject(DOCUMENT);

  readonly online = signal(navigator.onLine);
  readonly updateReady = signal(false);

  constructor() {
    const online = () => this.online.set(true);
    const offline = () => this.online.set(false);
    const visible = () => {
      if (this.document.visibilityState !== 'visible') return;
      clearBadge();
      if (this.updates?.isEnabled) void this.updates.checkForUpdate().catch(() => undefined);
    };
    window.addEventListener('online', online);
    window.addEventListener('offline', offline);
    this.document.addEventListener('visibilitychange', visible);
    clearBadge();

    if (this.updates?.isEnabled) {
      const sub = this.updates.versionUpdates.subscribe((e) => {
        if (e.type === 'VERSION_READY') this.updateReady.set(true);
      });
      inject(DestroyRef).onDestroy(() => sub.unsubscribe());
    }
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('online', online);
      window.removeEventListener('offline', offline);
      this.document.removeEventListener('visibilitychange', visible);
    });
  }

  /** "تحديث": the new version on the next load */
  async update(): Promise<void> {
    try {
      if (this.updates?.isEnabled) await this.updates.activateUpdate();
    } finally {
      this.document.location.reload();
    }
  }
}

function clearBadge(): void {
  const nav = navigator as Navigator & { clearAppBadge?: () => Promise<void> };
  nav.clearAppBadge?.().catch(() => undefined);
}
