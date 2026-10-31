import { LastTab } from './last-tab';

describe('LastTab', () => {
  afterEach(() => localStorage.removeItem(LastTab.KEY));

  it('opens the budget by default and remembers the expenses tab', () => {
    localStorage.removeItem(LastTab.KEY);
    expect(LastTab.path()).toBe('/budget');
    LastTab.remember('expenses');
    expect(LastTab.path()).toBe('/expenses/home');
  });

  it('never uses the old app\'s keys (CLAUDE.md rule 15)', () => {
    expect(LastTab.KEY.startsWith('mizaniya')).toBe(false);
  });
});
