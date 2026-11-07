import { Day, Entry } from '../../core/api/models';
import { categoryDays } from './stats-view';

const entry = (id: string, type: Entry['type'], amount: string, entryDate: string): Entry => ({
  id, type, amount, name: id, categoryId: 'food', entryDate, accountId: 'a1',
  createdAt: '', updatedAt: '', version: 0,
});
const day = (date: string, spent: string, entries: Entry[]): Day => ({ date, spent, extra: '0.00', entries });

describe("A category's expenses in the stats (D-39)", () => {
  it('lists the days newest first', () => {
    const days = categoryDays([
      day('2026-11-02', '20.00', [entry('a', 'EXPENSE', '20.00', '2026-11-02')]),
      day('2026-11-06', '45.00', [entry('b', 'EXPENSE', '30.00', '2026-11-06'), entry('c', 'EXPENSE', '15.00', '2026-11-06')]),
      day('2026-11-04', '10.00', [entry('d', 'EXPENSE', '10.00', '2026-11-04')]),
    ]);
    expect(days.map((d) => d.date)).toEqual(['2026-11-06', '2026-11-04', '2026-11-02']);
    expect(days[0].entries.map((e) => e.id)).toEqual(['b', 'c']);
  });

  it('shows expenses only: income leaves its day, and a day with income only is dropped', () => {
    const days = categoryDays([
      day('2026-11-06', '30.00', [entry('a', 'EXPENSE', '30.00', '2026-11-06'), entry('i', 'INCOME', '100.00', '2026-11-06')]),
      day('2026-11-05', '0.00', [entry('j', 'INCOME', '50.00', '2026-11-05')]),
    ]);
    expect(days.map((d) => d.date)).toEqual(['2026-11-06']);
    expect(days[0].entries.map((e) => e.id)).toEqual(['a']);
    expect(days[0].spent).toBe('30.00');
  });

  it('nothing when the category has no expenses', () => {
    expect(categoryDays([])).toEqual([]);
  });
});
