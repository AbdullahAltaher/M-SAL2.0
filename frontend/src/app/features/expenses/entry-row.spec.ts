import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Entry } from '../../core/api/models';
import { CountTo } from '../../shared/components/count-to';
import { EntryRow } from './entry-row';
import { EntrySheets } from './entry-sheets';
import { ExpensesStore } from './expenses-store';

const entry: Entry = {
  id: 'e1', type: 'EXPENSE', amount: '18.00', name: 'قهوة', categoryId: 'c1', entryDate: '2026-11-11', accountId: 'a1',
  createdAt: '2026-11-11T09:00:00Z', updatedAt: '2026-11-11T09:00:00Z', version: 0,
};

@Component({
  imports: [EntryRow],
  template: `<ul><li app-entry-row [entry]="entry"></li></ul>`,
})
class Host {
  entry = entry;
}

describe('EntryRow (DESIGN.md D-21)', () => {
  const calls: string[] = [];
  const store = {
    fresh: signal<string | null>(null),
    categories: signal([{ id: 'c1', code: null, name: 'قهوة', emoji: '☕', isHidden: false, sortOrder: 1, version: 0 }]),
    catOf: () => ({ name: 'قهوة', emoji: '☕' }),
    remove: async () => void calls.push('remove'),
    duplicate: async () => void calls.push('duplicate'),
  };

  beforeEach(async () => {
    calls.length = 0;
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [
        { provide: ExpensesStore, useValue: store },
        { provide: EntrySheets, useValue: { edit: async () => void calls.push('edit') } },
      ],
    }).compileComponents();
  });

  function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const row = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.x-row')!;
    row.setPointerCapture = () => undefined;
    row.style.width = '300px';
    return { fixture, row };
  }

  const drag = (row: HTMLElement, dx: number) => {
    row.dispatchEvent(new PointerEvent('pointerdown', { clientX: 100, clientY: 10, pointerId: 1, bubbles: true }));
    row.dispatchEvent(new PointerEvent('pointermove', { clientX: 100 + dx / 2, clientY: 10, pointerId: 1, bubbles: true }));
    row.dispatchEvent(new PointerEvent('pointermove', { clientX: 100 + dx, clientY: 10, pointerId: 1, bubbles: true }));
    row.dispatchEvent(new PointerEvent('pointerup', { clientX: 100 + dx, clientY: 10, pointerId: 1, bubbles: true }));
  };

  it('opens the entry on a tap', () => {
    const { row } = render();
    row.querySelector<HTMLButtonElement>('.x-front')!.click();
    expect(calls).toEqual(['edit']);
  });

  it('copies when dragged far toward the right edge, and does not open it', async () => {
    const { row } = render();
    drag(row, -140);
    row.querySelector<HTMLButtonElement>('.x-front')!.click();
    expect(calls).toEqual(['duplicate']);
  });

  it('deletes when dragged far toward the left edge', async () => {
    const { row } = render();
    drag(row, 140);
    await new Promise((r) => setTimeout(r, 250));
    expect(calls).toEqual(['remove']);
  });

  it('springs back from a short drag, doing nothing', async () => {
    const { fixture, row } = render();
    drag(row, 40);
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r, 250));
    expect(calls).toEqual([]);
    expect(row.querySelector<HTMLElement>('.x-front')!.style.transform).toBe('');
  });

  it('keeps delete and copy for screen readers', () => {
    const { row } = render();
    expect([...row.querySelectorAll('.x-vh button')].map((b) => b.textContent)).toEqual(['نسخ قهوة بتاريخ اليوم', 'حذف قهوة']);
  });
});

@Component({
  imports: [CountTo],
  template: `<app-count [value]="value()" />`,
})
class CountHost {
  value = signal(100);
}

describe('CountTo (D-10)', () => {
  it('shows the first value at once, then counts to the next one', async () => {
    const fixture = TestBed.createComponent(CountHost);
    fixture.detectChanges();
    const el = () => (fixture.nativeElement as HTMLElement).querySelector('app-count')!.textContent;
    expect(el()).toBe('100');
    fixture.componentInstance.value.set(75);
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r, 1100));
    fixture.detectChanges();
    expect(el()).toBe('75');
  });
});
