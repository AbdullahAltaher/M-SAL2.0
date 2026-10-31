import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { ApiError } from '../api/api-error';
import { SupabaseAuth } from '../auth/supabase-auth';
import { SessionStore } from '../session/session-store';

/**
 * Opens a screen only with read access to its service (SCREENS.md section 2), such as
 * {@code serviceGuard('SRV-009')} for the admin screen. The server checks again on every call.
 */
export function serviceGuard(code: string): CanActivateFn {
  return async () => {
    const router = inject(Router);
    const session = inject(SessionStore);
    const auth = inject(SupabaseAuth);
    // Guards on one route run together, so this one cannot rely on authGuard having run first
    await auth.whenReady();
    if (!auth.isSignedIn()) {
      return router.parseUrl('/login');
    }
    try {
      await session.load();
    } catch (error) {
      // Not invited, disabled, or no connection: back to sign-in, which shows why
      session.clear((error as ApiError).message);
      await auth.signOut();
      return router.parseUrl('/login');
    }
    return session.can(code) ? true : router.parseUrl('/');
  };
}
