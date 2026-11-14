import { Injectable, inject, signal } from '@angular/core';
import { SwPush } from '@angular/service-worker';

import { Api } from '../api/api';
import { SessionStore } from '../session/session-store';
import { canNotify } from './device';

/**
 * "الإشعارات على هذا الجهاز" (DESIGN.md D-41): this device subscribes to Web Push with the
 * server's key, apart from what the notifications are for (the bank messages' number on the
 * icon, B-22, and the daily reminder), which are the account's settings.
 */
@Injectable({ providedIn: 'root' })
export class DevicePush {
  /** Absent in tests and in development (no worker there) */
  private readonly push = inject(SwPush, { optional: true });
  private readonly api = inject(Api);
  private readonly session = inject(SessionStore);

  /** This device gets the notifications */
  readonly on = signal(false);

  constructor() {
    if (this.push?.isEnabled) this.push.subscription.subscribe((s) => this.on.set(!!s));
  }

  /** Why it cannot be turned on here, or null when it can */
  unavailable(): string | null {
    if (!this.session.user()?.pushPublicKey) return 'الإشعارات غير مفعّلة في السيرفر بعد.';
    if (!canNotify() || !this.push?.isEnabled) return 'الإشعارات تعمل في التطبيق المثبّت على الشاشة الرئيسية.';
    if (Notification.permission === 'denied') return 'الإشعارات مرفوضة لهذا التطبيق من إعدادات الجهاز.';
    return null;
  }

  /** Asks for permission (must follow a tap) and subscribes this device. */
  async enable(): Promise<void> {
    const key = this.session.user()?.pushPublicKey;
    if (!key || !this.push) throw new Error('no key');
    const subscription = await this.push.requestSubscription({ serverPublicKey: key });
    await this.api.subscribePush(subscription.toJSON());
    this.on.set(true);
  }

  /** Turned on for what needs it (the reminder, the bank messages), if it is not yet. */
  async ensure(): Promise<void> {
    if (!this.on()) await this.enable();
  }

  /** This device stops receiving; the account's settings stay for its other devices. */
  async disable(): Promise<void> {
    const subscription = this.push?.isEnabled ? await firstSubscription(this.push) : null;
    if (subscription) {
      await this.api.unsubscribePush(subscription.endpoint).catch(() => undefined);
      await subscription.unsubscribe().catch(() => false);
    }
    this.on.set(false);
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
