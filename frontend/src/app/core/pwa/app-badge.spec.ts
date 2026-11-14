import { showBadge } from './app-badge';

describe('The number on the app icon (BANK-SMS.md B-22)', () => {
  const nav = navigator as unknown as Record<string, unknown>;
  afterEach(() => {
    delete nav['setAppBadge'];
    delete nav['clearAppBadge'];
  });

  it('shows the messages waiting for review, and nothing when there are none', () => {
    const set = vi.fn(() => Promise.resolve());
    const clear = vi.fn(() => Promise.resolve());
    nav['setAppBadge'] = set;
    nav['clearAppBadge'] = clear;
    showBadge(3);
    expect(set).toHaveBeenCalledWith(3);
    showBadge(0);
    expect(clear).toHaveBeenCalledTimes(1);
    expect(set).toHaveBeenCalledTimes(1);
  });

  it('leaves browsers without it alone', () => {
    expect(() => showBadge(2)).not.toThrow();
  });
});
