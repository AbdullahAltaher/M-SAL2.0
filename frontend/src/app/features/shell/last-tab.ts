/**
 * The last section used, remembered on this device (SCREENS.md section 2), so "/" opens it.
 * The key starts with "msal2.", never "mizaniya" (CLAUDE.md rule 15). Storage can be missing
 * or blocked, so every access is guarded.
 */
export class LastTab {
  static readonly KEY = 'msal2.tab';

  static remember(tab: 'budget' | 'expenses'): void {
    try {
      localStorage.setItem(LastTab.KEY, tab);
    } catch {
      // Private mode or blocked storage: the default tab is fine
    }
  }

  /** The path to open at "/". */
  static path(): string {
    try {
      return localStorage.getItem(LastTab.KEY) === 'expenses' ? '/expenses/home' : '/budget';
    } catch {
      return '/budget';
    }
  }
}
