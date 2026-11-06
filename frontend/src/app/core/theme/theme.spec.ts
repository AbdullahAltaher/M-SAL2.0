import { TestBed } from '@angular/core/testing';

import { motionBetween } from './page-motion';
import { Theme } from './theme';

describe('Theme (DESIGN.md D-07)', () => {
  afterEach(() => {
    localStorage.removeItem(Theme.KEY);
    document.documentElement.removeAttribute('data-theme');
  });

  it('follows the device until a choice is made, and keeps the choice on this device', () => {
    const theme = TestBed.inject(Theme);
    TestBed.tick();
    expect(theme.mode()).toBe('auto');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);

    theme.set('light');
    TestBed.tick();
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(theme.effective()).toBe('light');
    expect(localStorage.getItem('msal2.theme')).toBe('light');

    theme.set('auto');
    TestBed.tick();
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    expect(localStorage.getItem('msal2.theme')).toBeNull();
  });

  it('never touches an old M-SAL key (CLAUDE.md rule 15)', () => {
    TestBed.inject(Theme).set('dark');
    expect(Object.keys(localStorage).filter((k) => k.startsWith('mizaniya'))).toEqual([]);
  });
});

describe('Page movement (D-32, D-33)', () => {
  it('slides between the two sections, in the right direction', () => {
    expect(motionBetween('/budget', '/expenses/home')).toBe('to-expenses');
    expect(motionBetween('/expenses/stats', '/budget')).toBe('to-budget');
  });

  it('fades between the Expenses screens', () => {
    expect(motionBetween('/expenses/home', '/expenses/entries')).toBe('fade');
  });

  it('does not move otherwise', () => {
    expect(motionBetween('/expenses/home', '/expenses/home?cycle=2')).toBeNull();
    expect(motionBetween('/login', '/budget')).toBeNull();
    expect(motionBetween('/budget', '/admin')).toBeNull();
  });
});
