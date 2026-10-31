import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { isIosSafari, isStandalone } from './device';

const SKIPPED = 'msal2.install.skipped';

/**
 * Opened in Safari on an iPhone, not from the home screen, and not skipped before: the install
 * steps come first (SCREENS.md S-02). Elsewhere the page is only reached from settings.
 */
export const installGuard: CanActivateFn = () => {
  const router = inject(Router);
  return isIosSafari() && !isStandalone() && !installSkipped() ? router.parseUrl('/install') : true;
};

export function installSkipped(): boolean {
  try {
    return localStorage.getItem(SKIPPED) === '1';
  } catch {
    return true;
  }
}

/** "متابعة في المتصفح": not asked again on this device */
export function skipInstall(): void {
  try {
    localStorage.setItem(SKIPPED, '1');
  } catch {
    // private browsing: shown again next time
  }
}
