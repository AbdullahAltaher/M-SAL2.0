import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Api } from '../../core/api/api';
import { Category, PendingEntry } from '../../core/api/models';
import { SessionStore } from '../../core/session/session-store';
import { ExpensesStore } from './expenses-store';
import { cleanMerchant, pendingLabel } from './pending-label';
import { CardDone, PendingCard } from './pending-view';

describe('pendingLabel (BANK-SMS.md B-04)', () => {
  it('counts in Arabic', () => {
    expect(pendingLabel(1)).toBe('طلب واحد');
    expect(pendingLabel(2)).toBe('طلبان');
    expect(pendingLabel(3)).toBe('3 طلبات');
    expect(pendingLabel(10)).toBe('10 طلبات');
    expect(pendingLabel(11)).toBe('11 طلبًا');
  });
});

describe('cleanMerchant (as the server cleans it)', () => {
  it('makes capitals and turns signs into single spaces', () => {
    expect(cleanMerchant('Al-Sarab  cafeteria.')).toBe('AL SARAB CAFETERIA');
    expect(cleanMerchant('AL SARAB CAFETERIA')).toBe('AL SARAB CAFETERIA');
  });
});

/** Made-up merchants and amounts (the repository is public). */
const categories: Category[] = [
  { id: 'c-food', code: 'cat_food', name: 'أكل ومطاعم', emoji: '🍔', isHidden: false, sortOrder: 0, version: 0 },
  { id: 'c-shop', code: 'cat_shopping', name: 'ملابس وتسوق', emoji: '🛍️', isHidden: false, sortOrder: 1, version: 0 },
];

function pending(over: Partial<PendingEntry> = {}): PendingEntry {
  return {
    id: 'p1', accountId: 'a1', amount: '12.00', merchant: 'BLUE FALCON CAFETERIA', categoryId: 'c-food', askEachTime: false,
    receivedAt: '2026-11-11T08:42:00Z', entryDate: '2026-11-11', smsText: 'Transaction of AED 12.00 debited ...', ...over,
  };
}

@Component({
  imports: [PendingCard],
  template: `<app-pending-card [entry]="entry()" [categories]="cats" today="2026-11-11" (done)="done.push($event)" />`,
})
class Host {
  entry = signal(pending());
  cats = categories;
  done: CardDone[] = [];
}

describe('PendingCard (BANK-SMS.md B-18)', () => {
  const approved: unknown[] = [];

  beforeEach(async () => {
    approved.length = 0;
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [
        {
          provide: ExpensesStore,
          useValue: { categories: signal(categories), accounts: signal([]), budgetId: signal('b1'), catOf: () => categories[0] },
        },
        { provide: SessionStore, useValue: { user: signal({ timeZone: 'Asia/Dubai' }) } },
        {
          provide: Api,
          useValue: {
            suggestions: async () => ({ suggestions: [] }),
            approvePending: async (id: string, body: unknown) => {
              approved.push({ id, body });
              return { id: 'e1', amount: '12.00', accountId: 'a1', cycleKey: 'cy1' };
            },
            ignorePending: async () => undefined,
          },
        },
      ],
    }).compileComponents();
  });

  function render(entry = pending()) {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.entry.set(entry);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, host: fixture.componentInstance };
  }

  const settle = async (fixture: { whenStable(): Promise<unknown>; detectChanges(): void }) => {
    await fixture.whenStable();
    await new Promise((r) => setTimeout(r, 300));
    fixture.detectChanges();
  };

  it('shows the merchant, the time in the user’s zone and the suggested category', () => {
    const { el } = render();
    expect(el.querySelector('.pc-merchant')?.textContent).toBe('BLUE FALCON CAFETERIA');
    expect(el.querySelector('.pc-when')?.textContent).toContain('اليوم 12:42');
    expect(el.querySelector('.pc-chip')?.textContent).toContain('أكل ومطاعم');
    expect(el.querySelector('.pc-remember')).toBeNull();
  });

  it('needs a description before approving', async () => {
    const { el, fixture } = render();
    el.querySelector<HTMLButtonElement>('.pc-acts .primary')!.click();
    fixture.detectChanges();
    expect(el.querySelector('.pc-err')?.textContent).toBe('اكتب وصف');
    expect(approved).toEqual([]);
  });

  it('needs a category before approving, and opens the picker', () => {
    const { el, fixture } = render(pending({ categoryId: null }));
    el.querySelector<HTMLButtonElement>('.pc-acts .primary')!.click();
    fixture.detectChanges();
    expect(el.querySelector('.pc-err')?.textContent).toBe('اختر فئة قبل الاعتماد.');
    expect(el.querySelectorAll('.pc-cats .g-fchip').length).toBe(2);
    expect(approved).toEqual([]);
  });

  it('offers "remember" only after the category changed, and sends it', async () => {
    const { el, fixture, host } = render();
    el.querySelector<HTMLButtonElement>('.pc-chip')!.click();
    fixture.detectChanges();
    [...el.querySelectorAll<HTMLButtonElement>('.pc-cats .g-fchip')].find((b) => b.textContent?.includes('ملابس'))!.click();
    fixture.detectChanges();
    expect(el.querySelector('.pc-remember')).not.toBeNull();
    el.querySelector<HTMLButtonElement>('.pc-remember .st-switch')!.click();
    const name = el.querySelector<HTMLInputElement>('.pc-name')!;
    name.value = 'هدية';
    name.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('.pc-acts .primary')!.click();
    await settle(fixture);
    expect(approved).toEqual([{ id: 'p1', body: { categoryId: 'c-shop', name: 'هدية', remember: true } }]);
    expect(host.done.map((d) => d.remembered)).toEqual(['c-shop']);
  });

  it('never offers "remember" for a merchant picked every time (B-15)', () => {
    const { el, fixture } = render(pending({ categoryId: null, askEachTime: true }));
    expect(el.querySelector('.pc-hint')?.textContent).toContain('تختار فئتها كل مرة');
    el.querySelector<HTMLButtonElement>('.pc-chip')!.click();
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('.pc-cats .g-fchip')!.click();
    fixture.detectChanges();
    expect(el.querySelector('.pc-remember')).toBeNull();
  });

  it('shows the bank message as it came, on request (B-03)', () => {
    const { el, fixture } = render();
    expect(el.querySelector('.pc-sms')).toBeNull();
    el.querySelector<HTMLButtonElement>('.pc-smstog')!.click();
    fixture.detectChanges();
    expect(el.querySelector('.pc-sms')?.textContent).toBe('Transaction of AED 12.00 debited ...');
  });

  it('ignoring tells the page, without an entry', async () => {
    const { el, fixture, host } = render();
    el.querySelector<HTMLButtonElement>('.pc-acts .g-btn:not(.primary)')!.click();
    await settle(fixture);
    expect(host.done).toEqual([{ id: 'p1' }]);
  });
});
