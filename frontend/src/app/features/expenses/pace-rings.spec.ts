import { CycleSummary } from '../../core/api/models';
import { daysLeftLabel, pace, salarySoon } from './pace-rings';

const summary = (over: Partial<CycleSummary>): CycleSummary => ({
  start: '2026-11-01', lastDay: null, open: true, allowance: '3000.00', extra: '0.00', budget: '3000.00',
  spent: '0.00', remaining: '3000.00', spentToday: '0.00', progress: 0, daysElapsed: 12, totalDays: 30,
  daysLeft: 19, startOfToday: '3000.00', todayBudget: '100.00', canSpendToday: '100.00', overToday: '0.00',
  tomorrow: null, dailyAverage: '0.00', over: false, entryCount: 0, ...over,
});

describe('Time against money (DESIGN.md D-40)', () => {
  it('compares the days gone with the money spent, in whole percents', () => {
    expect(pace(summary({ progress: 0.36 }))).toEqual({ time: 40, money: 36, fast: false });
    // ahead by 5 points is still on plan, more than 5 is too fast
    expect(pace(summary({ progress: 0.45 }))!.fast).toBe(false);
    expect(pace(summary({ progress: 0.46 }))).toEqual({ time: 40, money: 46, fast: true });
  });

  it('stays within 0 and 100', () => {
    expect(pace(summary({ daysElapsed: 31, progress: 1.2 }))).toEqual({ time: 100, money: 100, fast: false });
  });

  it('is only for an open cycle with a next salary date', () => {
    expect(pace(summary({ totalDays: null }))).toBeNull();
    expect(pace(summary({ open: false }))).toBeNull();
  });

  it('says the days to the salary in Emirati Arabic', () => {
    const today = '2026-11-12';
    expect(daysLeftLabel(19, '2026-12-01', today)).toBe('باقي 19 يوم');
    expect(daysLeftLabel(11, '2026-11-23', today)).toBe('باقي 11 يوم');
    expect(daysLeftLabel(10, '2026-11-22', today)).toBe('باقي 10 أيام');
    expect(daysLeftLabel(3, '2026-11-15', today)).toBe('باقي 3 أيام');
    expect(daysLeftLabel(2, '2026-11-14', today)).toBe('باقي يومين');
    // daysLeft 1: today is the last day, the salary comes tomorrow
    expect(daysLeftLabel(1, '2026-11-13', today)).toBe('الراتب باجر');
    expect(daysLeftLabel(1, '2026-11-12', today)).toBe('الراتب اليوم');
    expect(daysLeftLabel(1, '2026-11-10', today)).toBe('الراتب اليوم');
  });

  it('warms the last three days', () => {
    expect([4, 3, 2, 1].map(salarySoon)).toEqual([false, true, true, true]);
  });
});
