import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { serviceGuard } from '../permissions/service-guard';
import { SessionStore } from '../session/session-store';
import { authGuard, guestGuard } from './guards';
import { SupabaseAuth } from './supabase-auth';

/** The guards await the saved session, so they must inject everything first (Angular NG0203). */
describe('guards', () => {
  const signedIn = { value: false };
  const fakeAuth = { whenReady: async () => undefined, isSignedIn: () => signedIn.value, signOut: async () => undefined };
  const run = (guard: typeof authGuard) =>
    TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)) as Promise<
      boolean | UrlTree
    >;
  const url = (result: boolean | UrlTree) => (result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : result);

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: SupabaseAuth, useValue: fakeAuth }] });
  });

  it('sends visitors to /login and lets signed-in users in', async () => {
    signedIn.value = false;
    expect(url(await run(authGuard))).toBe('/login');
    expect(await run(guestGuard)).toBe(true);
    signedIn.value = true;
    expect(await run(authGuard)).toBe(true);
    expect(url(await run(guestGuard))).toBe('/');
  });

  it('checks the service only for signed-in users, without calling /me for visitors', async () => {
    signedIn.value = false;
    const session = TestBed.inject(SessionStore);
    const load = vi.spyOn(session, 'load');
    expect(url(await run(serviceGuard('SRV-002')))).toBe('/login');
    expect(load).not.toHaveBeenCalled();
  });
});
