import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { SwUpdate } from '@angular/service-worker';

import { showBadge } from './app-badge';

/**
 * The app's own state for the banners (SCREENS.md section 7): no connection, and a new
 * version ready. Also clears the number on the app icon when the app is opened (the daily
 * reminder's), and says so in {@link back}: the Expenses section then reloads and puts back the
 * messages waiting for review (BANK-SMS.md B-22).
 */
@Injectable({ providedIn: 'root' })
export class AppStatus {
  /** Absent in tests and in development (no worker there) */
  private readonly updates = inject(SwUpdate, { optional: true });
  private readonly document = inject(DOCUMENT);

  readonly online = signal(navigator.onLine);
  readonly updateReady = signal(false);
  /** Bumped each time the app comes back on screen */
  readonly back = signal(0);

  constructor() {
    const online = () => this.online.set(true);
    const offline = () => this.online.set(false);
    const visible = () => {
      if (this.document.visibilityState !== 'visible') return;
      showBadge(0);
      this.back.update((n) => n + 1);
      if (this.updates?.isEnabled) void this.updates.checkForUpdate().catch(() => undefined);
    };
    window.addEventListener('online', online);
    window.addEventListener('offline', offline);
    this.document.addEventListener('visibilitychange', visible);
    showBadge(0);

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
