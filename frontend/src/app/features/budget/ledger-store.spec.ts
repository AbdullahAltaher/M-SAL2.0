import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { Budget } from '../../core/api/models';
import { BudgetsStore } from '../../core/data/budgets-store';
import { LedgerStore, displayRaw } from './ledger-store';

const budget: Budget = {
  id: 'b1', name: 'الأساسي', monthLabel: 'نوفمبر 2026', salaryLabel: 'الراتب',
  salary: { amount: '15000.00', isApprox: false }, lastAccountId: null, version: 3,
  totals: { final: '9600.00', finalApprox: true },
  groups: [{
    id: 'g1', title: 'التزامات ثابتة', sortOrder: 1, version: 1,
    totals: { subtotal: '5400.00', subtotalApprox: true, balanceAfter: '9600.00', balanceApprox: true },
    items: [
      { id: 'i1', name: 'إيجار', amount: '5000.00', isApprox: false, isDone: false, sortOrder: 1, version: 2 },
      { id: 'i2', name: 'كهرباء', amount: '400.00', isApprox: true, isDone: false, sortOrder: 2, version: 0 },
    ],
  }],
};

/** API.md section 5: auto-save after 600ms, one request at a time per record, with the latest version. */
describe('LedgerStore', () => {
  let store: LedgerStore;
  let http: HttpTestingController;
  const api = environment.apiUrl;

  beforeEach(async () => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    store = TestBed.inject(LedgerStore);
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(BudgetsStore).currentId.set('b1');
    const loading = store.load();
    await vi.advanceTimersByTimeAsync(0);
    http.expectOne(`${api}/budgets/b1`).flush(budget);
    await loading;
  });

  afterEach(() => vi.useRealTimers());

  it('computes totals while typing, with "~" spreading down (R-BUD-01, R-BUD-02)', () => {
    expect(store.totals()?.groups[0]).toEqual({ subtotal: 5400, subtotalApprox: true, balanceAfter: 9600, balanceApprox: true });
    store.editAmount('i1', '5500');
    expect(store.totals()?.final).toBe(9100);
    expect(displayRaw('~1234.5')).toBe('~1,234.5');
  });

  it('saves 600ms after typing stops, as one request with the latest version', async () => {
    store.editAmount('i1', '51');
    store.editAmount('i1', '510');
    await vi.advanceTimersByTimeAsync(599);
    http.expectNone(`${api}/budget-items/i1`);
    await vi.advanceTimersByTimeAsync(1);
    const req = http.expectOne(`${api}/budget-items/i1`);
    expect(req.request.body).toEqual({ amount: '510', isApprox: false, version: 2 });
    req.flush({ ...budget.groups[0].items[0], amount: '510.00', version: 3 });
    await vi.advanceTimersByTimeAsync(0);

    // The next save uses the new version
    store.editName('i1', 'إيجار البيت', true);
    await vi.advanceTimersByTimeAsync(0);
    expect(http.expectOne(`${api}/budget-items/i1`).request.body).toEqual({ name: 'إيجار البيت', version: 3 });
  });

  it('waits for the request in flight before sending the next one for the same record', async () => {
    store.toggleDone('i2');
    await vi.advanceTimersByTimeAsync(0);
    const first = http.expectOne(`${api}/budget-items/i2`);
    expect(first.request.body).toEqual({ isDone: true, version: 0 });
    store.editName('i2', 'كهرباء ومي', true);
    await vi.advanceTimersByTimeAsync(0);
    http.expectNone(`${api}/budget-items/i2`);
    first.flush({ ...budget.groups[0].items[1], isDone: true, version: 1 });
    await vi.advanceTimersByTimeAsync(0);
    expect(http.expectOne(`${api}/budget-items/i2`).request.body).toEqual({ name: 'كهرباء ومي', version: 1 });
  });

  it('sends an empty salary as null', async () => {
    store.editSalary('', true);
    await vi.advanceTimersByTimeAsync(0);
    expect(http.expectOne(`${api}/budgets/b1`).request.body).toEqual({ salary: null, version: 3 });
  });
});
