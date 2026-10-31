/** What this device and browser can do (SCREENS.md section 7). */

/** Opened from the home screen, not in a browser tab */
export function isStandalone(): boolean {
  const nav = navigator as Navigator & { standalone?: boolean };
  return nav.standalone === true
    || (typeof matchMedia === 'function' && matchMedia('(display-mode: standalone)').matches);
}

/** Safari on an iPhone or iPad (also iPadOS, which says it is a Mac), not Chrome or Firefox there */
export function isIosSafari(): boolean {
  const ua = navigator.userAgent;
  const ios = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
  return ios && /Safari/.test(ua) && !/CriOS|FxiOS|EdgiOS|OPiOS/.test(ua);
}

/** Web Push in this browser (on the iPhone, only once installed on the home screen) */
export function canNotify(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

/** Face ID, Touch ID or the device's own screen lock, through WebAuthn */
export async function canLock(): Promise<boolean> {
  try {
    return typeof PublicKeyCredential !== 'undefined'
      && await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
  } catch {
    return false;
  }
}
