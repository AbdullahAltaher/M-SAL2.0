import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

export type ThemeMode = 'auto' | 'light' | 'dark';

/** The status bar takes the background's top colour (tokens.scss --x-bg0). */
const BAR = { light: '#E3EBF8', dark: '#0B1426' } as const;

/**
 * Light and dark (DESIGN.md D-07): the device decides, unless the person picks one in Settings.
 * The choice stays on this device under "msal2.theme", never a "mizaniya" key (CLAUDE.md rule 15),
 * and shows as html[data-theme], which tokens.scss reads.
 */
@Injectable({ providedIn: 'root' })
export class Theme {
  static readonly KEY = 'msal2.theme';

  private readonly document = inject(DOCUMENT);
  private readonly query = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: light)') : null;
  private readonly systemLight = signal(this.query?.matches ?? false);

  readonly mode = signal<ThemeMode>(Theme.read());
  /** What is on screen now */
  readonly effective = computed(() => {
    const mode = this.mode();
    return mode === 'auto' ? (this.systemLight() ? 'light' : 'dark') : mode;
  });

  constructor() {
    this.query?.addEventListener('change', (e) => this.systemLight.set(e.matches));
    effect(() => {
      const mode = this.mode();
      const root = this.document.documentElement;
      if (mode === 'auto') root.removeAttribute('data-theme');
      else root.setAttribute('data-theme', mode);
      this.document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]')
        .forEach((m) => (m.content = BAR[this.effective()]));
    });
  }

  set(mode: ThemeMode): void {
    try {
      if (mode === 'auto') localStorage.removeItem(Theme.KEY);
      else localStorage.setItem(Theme.KEY, mode);
    } catch {
      // private browsing: the choice lasts for this visit only
    }
    this.mode.set(mode);
  }

  private static read(): ThemeMode {
    try {
      const saved = localStorage.getItem(Theme.KEY);
      return saved === 'light' || saved === 'dark' ? saved : 'auto';
    } catch {
      return 'auto';
    }
  }
}
