// M-SAL2.0's service worker (SCREENS.md section 7): Angular's own (the app offline, updates,
// showing notifications and opening them), plus the number on the app icon with the daily
// reminder. Registered from /M-SAL2.0/ only, never the root (CLAUDE.md rule 15).
importScripts('./ngsw-worker.js');

self.addEventListener('push', (event) => {
  let badge = 0;
  try {
    badge = event.data?.json()?.notification?.data?.badge ?? 0;
  } catch {
    // not JSON: no badge
  }
  if (badge && self.navigator.setAppBadge) {
    event.waitUntil(self.navigator.setAppBadge(badge).catch(() => undefined));
  }
});
