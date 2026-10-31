import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { toApiError } from '../api/api-error';
import { Me, SessionStore } from './session-store';

const me: Me = {
  id: 'u1',
  email: 'sara@test.local',
  displayName: null,
  timeZone: 'Asia/Dubai',
  today: '2026-11-11',
  roles: ['USER'],
  services: { 'SRV-001': { read: true, write: true }, 'SRV-006': { read: true, write: false } },
  lastBudgetId: 'b1',
  exportReminder: { due: false, days: 0, never: true },
  dailyReminderTime: null,
  pushPublicKey: null,
  version: 1,
};

describe('SessionStore', () => {
  let store: SessionStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    store = TestBed.inject(SessionStore);
    http = TestBed.inject(HttpTestingController);
  });

  it('loads /me once and answers permissions from its services', async () => {
    const first = store.load();
    const second = store.load();
    http.expectOne(`${environment.apiUrl}/me`).flush(me);
    expect(await first).toEqual(me);
    expect(await second).toEqual(me);
    expect(store.today()).toBe('2026-11-11');
    expect(store.can('SRV-001', true)).toBe(true);
    expect(store.can('SRV-006')).toBe(true);
    expect(store.can('SRV-006', true)).toBe(false);
    expect(store.can('SRV-009')).toBe(false);
  });

  it('turns a refusal into the server error and keeps it for the sign-in screen', async () => {
    const loading = store.load();
    http.expectOne(`${environment.apiUrl}/me`).flush(
      { code: 'SRV-000-E001', message: 'هذا البريد غير مدعو لاستخدام التطبيق.' },
      { status: 403, statusText: 'Forbidden' },
    );
    const error = await loading.catch((e) => e);
    expect(error.code).toBe('SRV-000-E001');
    store.clear(error.message);
    expect(store.lastFailure()).toBe('هذا البريد غير مدعو لاستخدام التطبيق.');
    expect(store.user()).toBeNull();
  });
});

describe('toApiError', () => {
  it('keeps the server message, and explains a lost connection', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).message).toBe('لا يوجد اتصال، لا يمكن الحفظ الآن.');
    expect(toApiError(new Error('boom')).code).toBe('SYS-E008');
  });
});
