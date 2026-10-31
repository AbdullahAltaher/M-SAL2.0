import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SupabaseAuth } from './supabase-auth';

// inject() only works before the first await, so every guard injects first.

/** Only for signed-in users; others go to /login (SCREENS.md section 2). */
export const authGuard: CanActivateFn = async () => {
  const auth = inject(SupabaseAuth);
  const router = inject(Router);
  await auth.whenReady();
  return auth.isSignedIn() ? true : router.parseUrl('/login');
};

/** Only for visitors: a signed-in user skips the sign-in screen. */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(SupabaseAuth);
  const router = inject(Router);
  await auth.whenReady();
  return auth.isSignedIn() ? router.parseUrl('/') : true;
};
