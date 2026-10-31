import { chartNum, smoothPath } from './chart';

/** Ported from M-SAL's test/expenses.test.js (chartNum, smoothPath). */
describe('chart helpers', () => {
  it('chartNum: compact Latin labels', () => {
    expect(chartNum(0)).toBe('0');
    expect(chartNum(46.25)).toBe('46');
    expect(chartNum(7.5)).toBe('8');
    expect(chartNum(999)).toBe('999');
    expect(chartNum(999.6)).toBe('1K');
    expect(chartNum(1000)).toBe('1K');
    expect(chartNum(1250)).toBe('1.3K');
    expect(chartNum(12400)).toBe('12.4K');
    expect(chartNum(10000)).toBe('10K');
    expect(chartNum(20000)).toBe('20K');
    expect(chartNum(100000)).toBe('100K');
    expect(chartNum(-20)).toBe('-20');
    expect(chartNum(-0.2)).toBe('0');
    expect(chartNum(undefined)).toBe('0');
    expect(/[٠-٩]/.test(chartNum(123456))).toBe(false);
  });

  it('smoothPath: starts and ends on the points, one curve per gap, stays inside the plot', () => {
    expect(smoothPath([], 0, 100)).toBe('');
    expect(smoothPath([[10, 50]], 0, 100)).toBe('M10 50');
    const pts: [number, number][] = [[0, 90], [50, 10], [100, 95], [150, 20], [200, 60]];
    const d = smoothPath(pts, 10, 95);
    expect(d.startsWith('M0 90')).toBe(true);
    expect(d.endsWith(' 200 60')).toBe(true);
    expect((d.match(/C/g) || []).length).toBe(pts.length - 1);
    const nums = d.replace(/[MC]/g, ' ').trim().split(/\s+/).map(Number);
    for (let i = 1; i < nums.length; i += 2) {
      expect(nums[i]).toBeGreaterThanOrEqual(10);
      expect(nums[i]).toBeLessThanOrEqual(95);
    }
    expect(smoothPath([[0, 5], [10, 5]], 0, 10)).toBe('M0 5 C1.67 5 8.33 5 10 5');
  });
});
