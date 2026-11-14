import { Injectable, computed, inject } from '@angular/core';

import { Api } from '../api/api';
import { SessionStore } from '../session/session-store';
import { DevicePush } from './device-push';

/**
 * The daily reminder "سجّلت مصاريف اليوم؟" (SCREENS.md section 7): the time is saved in
 * {@code /me}, and the server sends it only if no expense was added that day. Turning it on also
 * turns on this device's notifications; turning it off leaves them (DESIGN.md D-41).
 */
@Injectable({ providedIn: 'root' })
export class DailyReminder {
  private readonly device = inject(DevicePush);
  private readonly api = inject(Api);
  private readonly session = inject(SessionStore);

  /** "21:00", or null when off */
  readonly time = computed(() => this.session.user()?.dailyReminderTime?.slice(0, 5) ?? null);

  /** Why it cannot be turned on here, or null when it can */
  unavailable(): string | null {
    return this.device.unavailable();
  }

  /** Turns this device's notifications on if needed (must follow a tap), then saves the time. */
  async enable(time: string): Promise<void> {
    await this.device.ensure();
    await this.saveTime(time);
  }

  async changeTime(time: string): Promise<void> {
    await this.saveTime(time);
  }

  /** Off for the account; the device keeps its notifications (the bank messages, B-22) */
  async disable(): Promise<void> {
    await this.saveTime(null);
  }

  private async saveTime(time: string | null): Promise<void> {
    const me = this.session.user();
    if (!me) return;
    const saved = await this.api.patchMe({ dailyReminderTime: time, version: me.version });
    this.session.update(saved);
  }
}
