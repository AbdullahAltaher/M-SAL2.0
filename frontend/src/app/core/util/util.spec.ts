import { parseAllowance, parseEntryAmount, parseLedgerAmount, toLatinDigits } from './amounts';
import { computeTotals } from './budget-totals';
import { dayLabel, fullArabicDate, parseDate, shortArabicDate, weekdayIndex } from './dates';
import { formatMoney, moneyText } from './money';

/** Ported from M-SAL's test/expenses.test.js, with the examples in BUSINESS_RULES.md. */

describe('R-ENT-03: expense amount input', () => {
  it('accepts Latin, Arabic-Indic and Persian digits, decimals and separators', () => {
    const p = parseEntryAmount;
    expect(p('46.25')).toBe('46.25');
    expect(p('٤٦٫٢٥')).toBe('46.25');
    expect(p('٤٦.٢٥')).toBe('46.25');
    expect(p('۴۶٫۲۵')).toBe('46.25');
    expect(p('46,25')).toBe('46.25');
    expect(p('46،5')).toBe('46.50');
    expect(p('1,234.50')).toBe('1234.50');
    expect(p('1,234')).toBe('1234.00');
    expect(p('١٬٢٣٤٫٥')).toBe('1234.50');
    expect(p(' 12 ')).toBe('12.00');
    expect(p('.5')).toBe('0.50');
    expect(p('12.')).toBe('12.00');
    expect(p('0.004')).toBeNull();
    expect(p('0.005')).toBe('0.01');
    expect(p('10.456')).toBe('10.46');
    expect(p(12)).toBe('12.00');
  });

  it('refuses junk, zero, negatives and absurd values', () => {
    for (const bad of ['', '   ', null, undefined, 'abc', '0', '0.00', '-5', '1.2.3', '12e3', '١٢أ', '9999999999', '--', '٫', '٠']) {
      expect(parseEntryAmount(bad), JSON.stringify(bad)).toBeNull();
    }
  });

  it('turns Arabic digits into Latin ones (R-GEN-04)', () => {
    expect(toLatinDigits('٠١٢٣٤٥٦٧٨٩ ۰۱۲۳۴۵۶۷۸۹')).toBe('0123456789 0123456789');
  });
});

describe('R-CYC-03: allowance input', () => {
  it('refuses empty, accepts 0 and follows the expense rules otherwise', () => {
    expect(parseAllowance('')).toBeNull();
    expect(parseAllowance('  ')).toBeNull();
    expect(parseAllowance('0')).toBe('0.00');
    expect(parseAllowance('000')).toBe('0.00');
    expect(parseAllowance('٠')).toBe('0.00');
    expect(parseAllowance('3,000')).toBe('3000.00');
    expect(parseAllowance('-5')).toBeNull();
  });
});

describe('R-BUD-03: budget amount input', () => {
  it('keeps "~", allows negatives, counts text as 0 and leaves empty fields empty', () => {
    expect(parseLedgerAmount('~400')).toEqual({ amount: '400', isApprox: true });
    expect(parseLedgerAmount('～ ١٬٢٣٤٫٥')).toEqual({ amount: '1234.5', isApprox: true });
    expect(parseLedgerAmount('1,234.5')).toEqual({ amount: '1234.5', isApprox: false });
    expect(parseLedgerAmount('-50')).toEqual({ amount: '-50', isApprox: false });
    expect(parseLedgerAmount('abc')).toEqual({ amount: '0', isApprox: false });
    expect(parseLedgerAmount('')).toEqual({ amount: null, isApprox: false });
    expect(parseLedgerAmount('  ')).toEqual({ amount: null, isApprox: false });
    expect(parseLedgerAmount(null)).toEqual({ amount: null, isApprox: false });
    expect(parseLedgerAmount('10.456')).toEqual({ amount: '10.46', isApprox: false });
  });
});

describe('R-BUD-01, R-BUD-02: budget totals', () => {
  const a = (amount: string | null, isApprox = false) => ({ amount, isApprox });

  it('matches the business rules example', () => {
    const t = computeTotals(a('15000'), [[a('5000'), a('400', true)], [a('2500')]]);
    expect(t.groups[0]).toEqual({ subtotal: 5400, subtotalApprox: true, balanceAfter: 9600, balanceApprox: true });
    expect(t.groups[1]).toEqual({ subtotal: 2500, subtotalApprox: false, balanceAfter: 7100, balanceApprox: true });
    expect(t.final).toBe(7100);
    expect(t.finalApprox).toBe(true);
  });

  it('counts empty as 0, allows a negative balance and has no float drift', () => {
    const t = computeTotals(a(null), [[a(null), a('0.1'), a('0.2')], [a('-50')]]);
    expect(t.groups[0].subtotal).toBe(0.3);
    expect(t.final).toBe(-0.3 + 50);
    expect(t.finalApprox).toBe(false);
  });
});

describe('R-GEN-05: money', () => {
  it('uses Latin digits and two decimals only when needed', () => {
    expect(formatMoney(46.25)).toBe('46.25');
    expect(formatMoney(46)).toBe('46');
    expect(formatMoney(46.5)).toBe('46.50');
    expect(formatMoney('46.50')).toBe('46.50');
    expect(formatMoney(1234567.8)).toBe('1,234,567.80');
    expect(formatMoney(-20)).toBe('-20');
    expect(formatMoney(0.1 + 0.2)).toBe('0.30');
    expect(moneyText(46.25)).toBe('46.25 د.إ');
    expect(/[٠-٩]/.test(formatMoney(123456.78))).toBe(false);
  });
});

describe('R-GEN-06: dates', () => {
  it('labels today, yesterday and tomorrow, else the full Arabic date', () => {
    expect(dayLabel('2026-10-02', '2026-10-02')).toBe('اليوم');
    expect(dayLabel('2026-10-01', '2026-10-02')).toBe('أمس');
    expect(dayLabel('2026-10-03', '2026-10-02')).toBe('غدًا');
    expect(dayLabel('2026-09-28', '2026-10-02')).toBe('الاثنين 28 سبتمبر 2026');
    expect(fullArabicDate('2026-10-02')).toBe('الجمعة 2 أكتوبر 2026');
  });

  it('finds yesterday across month and year ends', () => {
    expect(dayLabel('2026-02-28', '2026-03-01')).toBe('أمس');
    expect(dayLabel('2026-12-31', '2027-01-01')).toBe('أمس');
    expect(dayLabel('2028-02-29', '2028-03-01')).toBe('أمس');
  });

  it('knows the weekday and the short date', () => {
    expect(weekdayIndex('1970-01-01')).toBe(4);
    expect(weekdayIndex('2026-10-04')).toBe(0);
    expect(weekdayIndex('1969-12-31')).toBe(3);
    expect(shortArabicDate('2026-09-02')).toBe('2 سبتمبر');
    expect(shortArabicDate('2026-09-02', true)).toBe('2 سبتمبر 2026');
  });

  it('refuses impossible dates', () => {
    for (const bad of ['2026-02-30', '2026-13-01', '2026-1-5', '', null, '2025-02-29']) {
      expect(parseDate(bad), String(bad)).toBeNull();
    }
    expect(parseDate('2028-02-29')).not.toBeNull();
  });
});
