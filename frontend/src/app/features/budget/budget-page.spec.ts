import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { Budget } from '../../core/api/models';
import { BudgetsStore } from '../../core/data/budgets-store';
import { BudgetPage } from './budget-page';

const item = (id: string, name: string, amount: string, isDone: boolean) =>
  ({ id, name, amount, isApprox: false, isDone, sortOrder: 1, version: 0 });

const budget: Budget = {
  id: 'b1', name: 'سارة', monthLabel: 'نوفمبر 2026', salaryLabel: 'الراتب', salary: { amount: '1000.00', isApprox: false },
  groups: [
    { id: 'g1', title: 'التزامات', sortOrder: 1, items: [item('i1', 'إيجار', '400.00', true), item('i2', 'كهرباء', '100.00', false)],
      totals: { subtotal: '500.00', subtotalApprox: false, balanceAfter: '500.00', balanceApprox: false }, version: 0 },
    { id: 'g2', title: 'ادخار', sortOrder: 2, items: [item('i3', 'حصالة', '200.00', false)],
      totals: { subtotal: '200.00', subtotalApprox: false, balanceAfter: '300.00', balanceApprox: false }, version: 0 },
  ],
  totals: { final: '300.00', finalApprox: false }, lastAccountId: null, version: 0,
};

describe('BudgetPage (DESIGN.md D-05, D-14)', () => {
  afterEach(() => localStorage.removeItem('msal2.budget.folded'));

  async function render() {
    await TestBed.configureTestingModule({
      imports: [BudgetPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    TestBed.inject(BudgetsStore).currentId.set('b1');
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r)); // the load waits for pending saves first
    TestBed.inject(HttpTestingController).expectOne(`${environment.apiUrl}/budgets/b1`).flush(budget);
    await new Promise((r) => setTimeout(r));
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows the remaining, the salary and the paid items in the card', async () => {
    const { el } = await render();
    expect(el.querySelector('#finalBalance')?.textContent?.trim()).toBe('300');
    expect(el.querySelector<HTMLInputElement>('#salaryAmount')?.value).toBe('1,000');
    expect(el.querySelector('.b-done')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('صُرف 1 من 3 بنود');
  });

  it('folds a group, and keeps it folded on this device', async () => {
    const { fixture, el } = await render();
    const group = () => el.querySelector('[data-block="g2"]')!;
    expect(group().classList.contains('open')).toBe(true);
    group().querySelector<HTMLButtonElement>('.b-chev')!.click();
    fixture.detectChanges();
    expect(group().classList.contains('open')).toBe(false);
    expect(group().querySelector<HTMLElement>('.g-col')!.inert).toBe(true);
    expect(JSON.parse(localStorage.getItem('msal2.budget.folded')!)).toEqual(['g2']);
  });

  it('keeps the tools in the ⋯ menu', async () => {
    const { fixture, el } = await render();
    expect(el.querySelector('.b-tools-menu')).toBeNull();
    el.querySelector<HTMLButtonElement>('.b-tools')!.click();
    fixture.detectChanges();
    expect([...el.querySelectorAll('.b-tools-menu .nm')].map((n) => n.textContent)).toEqual(['شهر جديد', 'إيصال', 'تصدير', 'استيراد']);
  });
});
