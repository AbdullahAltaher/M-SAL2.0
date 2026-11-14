type BadgeNavigator = Navigator & {
  setAppBadge?: (n?: number) => Promise<void>;
  clearAppBadge?: () => Promise<void>;
};

/**
 * The number on the app icon (BANK-SMS.md B-22): the messages waiting for review, or nothing.
 * Browsers without it (and tests) are left alone.
 */
export function showBadge(n: number): void {
  const nav = navigator as BadgeNavigator;
  const done = n > 0 ? nav.setAppBadge?.(n) : nav.clearAppBadge?.();
  done?.catch(() => undefined);
}
