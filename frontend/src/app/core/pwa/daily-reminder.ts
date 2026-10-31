import { Injectable, computed, inject } from '@angular/core';
import { SwPush } from '@angular/service-worker';

import { Api } from '../api/api';
import { SessionStore } from '../session/session-store';
import { canNotify } from './device';

/**
 * The daily reminder "سجّلت مصاريف اليوم؟" (SCREENS.md section 7): this device subscribes to
 * Web Push with the server's key, and the time is saved in {@code /me}. The server sends it only
 * if no expense was added that day.
 */
@Injectable({ providedIn: 'root' })
export class DailyReminder {
  /** Absent in tests and in development (no worker there) */
  private readonly push = inject(SwPush, { optional: true });
  private readonly api = inject(Api);
  private readonly session = inject(SessionStore);

  /** "21:00", or null when off */
  readonly time = computed(() => this.session.user()?.dailyReminderTime?.slice(0, 5) ?? null);

  /** Why it cannot be turned on here, or null when it can */
  unavailable(): string | null {
    if (!this.session.user()?.pushPublicKey) return 'الإشعارات غير مفعّلة في السيرفر بعد.';
    if (!canNotify() || !this.push?.isEnabled) return 'التذكير يعمل في التطبيق المثبّت على الشاشة الرئيسية.';
    if (Notification.permission === 'denied') return 'الإشعارات مرفوضة لهذا التطبيق من إعدادات الجهاز.';
    return null;
  }

  /** Asks for permission (must follow a tap), subscribes this device, then saves the time. */
  async enable(time: string): Promise<void> {
    const key = this.session.user()?.pushPublicKey;
    if (!key || !this.push) throw new Error('no key');
    const subscription = await this.push.requestSubscription({ serverPublicKey: key });
    await this.api.subscribePush(subscription.toJSON());
    await this.saveTime(time);
  }

  async changeTime(time: string): Promise<void> {
    await this.saveTime(time);
  }

  /** Off for the account; this device also stops receiving */
  async disable(): Promise<void> {
    await this.saveTime(null);
    const subscription = this.push?.isEnabled ? await firstSubscription(this.push) : null;
    if (subscription) {
      await this.api.unsubscribePush(subscription.endpoint).catch(() => undefined);
      await subscription.unsubscribe().catch(() => false);
    }
  }

  private async saveTime(time: string | null): Promise<void> {
    const me = this.session.user();
    if (!me) return;
    const saved = await this.api.patchMe({ dailyReminderTime: time, version: me.version });
    this.session.update(saved);
  }
}

function firstSubscription(push: SwPush): Promise<PushSubscription | null> {
  return new Promise((resolve) => {
    const sub = push.subscription.subscribe((s) => {
      resolve(s);
      queueMicrotask(() => sub.unsubscribe());
    });
  });
}
