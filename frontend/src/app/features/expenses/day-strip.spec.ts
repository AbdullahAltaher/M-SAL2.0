import { Day } from '../../core/api/models';
import { stripDays } from './day-strip';

const cycle = (startDate: string, lastDay: string | null, expectedNextSalaryDate: string | null) =>
  ({ id: 'c1', startDate, lastDay, expectedNextSalaryDate, isOpen: lastDay === null });
const day = (date: string, spent: string): Day => ({ date, spent, extra: '0.00', entries: [] });

describe('The days strip (R-ENT-16)', () => {
  it('an open cycle runs to the day before the next salary, later days cannot be chosen', () => {
    const days = stripDays(cycle('2026-11-01', null, '2026-12-01'), '2026-11-06', []);
    expect(days.length).toBe(30);
    expect(days[0].date).toBe('2026-11-01');
    expect(days.at(-1)!.date).toBe('2026-11-30');
    expect(days.filter((d) => !d.future).map((d) => d.num)).toEqual([1, 2, 3, 4, 5, 6]);
    expect(days[5].label).toBe('اليوم');
    // 2026-11-01 is a Sunday
    expect(days[0].label).toBe('أحد');
  });

  it('an open cycle without a next salary date stops at today, a closed one at its last day', () => {
    expect(stripDays(cycle('2026-11-01', null, null), '2026-11-06', []).at(-1)!.date).toBe('2026-11-06');
    // a next salary date already passed: up to today
    expect(stripDays(cycle('2026-10-01', null, '2026-10-31'), '2026-11-06', []).at(-1)!.date).toBe('2026-11-06');
    const closed = stripDays(cycle('2026-10-01', '2026-10-31', '2026-11-01'), '2026-11-06', []);
    expect(closed.length).toBe(31);
    expect(closed.some((d) => d.future)).toBe(false);
  });

  it('marks the days with an expense, not those with income only', () => {
    const days = stripDays(cycle('2026-11-01', null, null), '2026-11-03',
      [day('2026-11-02', '30.25'), day('2026-11-03', '0.00')]);
    expect(days.map((d) => d.spent)).toEqual([false, true, false]);
  });
});
