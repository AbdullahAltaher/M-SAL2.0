import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { SessionStore } from '../session/session-store';
import { testMe } from '../../testing/test-me';
import { AppLock, RELOCK_AFTER_MS } from './app-lock';
import { DailyReminder } from './daily-reminder';
import { installGuard, skipInstall } from './install-guard';

const IPHONE_SAFARI = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 '
  + '(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1';
const IPHONE_CHROME = IPHONE_SAFARI.replace('Version/18.0', 'CriOS/130.0');
const DESKTOP = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36';

function userAgent(ua: string): void {
  Object.defineProperty(navigator, 'userAgent', { value: ua, configurable: true });
}

describe('installGuard (S-02)', () => {
  const original = navigator.userAgent;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });
  afterEach(() => userAgent(original));

  const run = () => TestBed.runInInjectionContext(() => installGuard({} as never, {} as never));

  it('sends Safari on an iPhone to the install steps first', () => {
    userAgent(IPHONE_SAFARI);
    const result = run() as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/install');
  });

  it('lets it through once skipped, and never stops other browsers', () => {
    userAgent(IPHONE_SAFARI);
    skipInstall();
    expect(run()).toBe(true);
    localStorage.clear();
    userAgent(IPHONE_CHROME);
    expect(run()).toBe(true);
    userAgent(DESKTOP);
    expect(run()).toBe(true);
  });
});

describe('AppLock (S-03)', () => {
  const credentials = { create: vi.fn(), get: vi.fn() };

  beforeEach(() => {
    localStorage.clear();
    Object.defineProperty(navigator, 'credentials', { value: credentials, configurable: true });
    credentials.create.mockResolvedValue({ rawId: new Uint8Array([1, 2, 3]).buffer });
    credentials.get.mockResolvedValue({});
    TestBed.configureTestingModule({});
  });
  afterEach(() => vi.useRealTimers());

  it('keeps only the passkey id, under msal2 and never mizaniya', async () => {
    const lock = TestBed.inject(AppLock);
    await lock.enable('sara@test.local');
    expect(lock.enabled()).toBe(true);
    expect(JSON.parse(localStorage.getItem('msal2.lock')!)).toEqual({ credentialId: 'AQID' });
    expect(Object.keys(localStorage).some((k) => k.startsWith('mizaniya'))).toBe(false);
    const options = credentials.create.mock.calls[0][0].publicKey;
    expect(options.authenticatorSelection.userVerification).toBe('required');
  });

  it('opens only when the device confirms the owner', async () => {
    localStorage.setItem('msal2.lock', JSON.stringify({ credentialId: 'AQID' }));
    const lock = TestBed.inject(AppLock);
    expect(lock.locked()).toBe(true);

    credentials.get.mockRejectedValueOnce(new DOMException('cancelled', 'NotAllowedError'));
    expect(await lock.unlock()).toBe(false);
    expect(lock.locked()).toBe(true);

    expect(await lock.unlock()).toBe(true);
    expect(lock.locked()).toBe(false);
    const options = credentials.get.mock.calls[1][0].publicKey;
    expect([...new Uint8Array(options.allowCredentials[0].id)]).toEqual([1, 2, 3]);
  });

  it('locks again after 5 minutes in the background, not after a moment', async () => {
    vi.useFakeTimers();
    localStorage.setItem('msal2.lock', JSON.stringify({ credentialId: 'AQID' }));
    const lock = TestBed.inject(AppLock);
    await lock.unlock();
    const doc = TestBed.inject(DOCUMENT);
    const show = (state: 'hidden' | 'visible') => {
      Object.defineProperty(doc, 'visibilityState', { value: state, configurable: true });
      doc.dispatchEvent(new Event('visibilitychange'));
    };

    show('hidden');
    vi.advanceTimersByTime(60_000);
    show('visible');
    expect(lock.locked()).toBe(false);

    show('hidden');
    vi.advanceTimersByTime(RELOCK_AFTER_MS);
    show('visible');
    expect(lock.locked()).toBe(true);
  });
});

describe('DailyReminder', () => {
  async function signedIn(pushPublicKey: string | null, dailyReminderTime: string | null = null) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const loading = TestBed.inject(SessionStore).load();
    TestBed.inject(HttpTestingController).expectOne(`${environment.apiUrl}/me`)
      .flush({ ...testMe(['SRV-000']), pushPublicKey, dailyReminderTime });
    await loading;
    return TestBed.inject(DailyReminder);
  }

  it('says why it cannot be turned on', async () => {
    expect((await signedIn(null)).unavailable()).toBe('الإشعارات غير مفعّلة في السيرفر بعد.');
  });

  it('needs the installed app when the browser has no worker', async () => {
    expect((await signedIn('BKey')).unavailable()).toBe('التذكير يعمل في التطبيق المثبّت على الشاشة الرئيسية.');
  });

  it('shows the saved time without seconds', async () => {
    expect((await signedIn('BKey', '21:30:00')).time()).toBe('21:30');
  });
});
