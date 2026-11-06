import { Budget, Receipt } from '../../core/api/models';
import { dayLabel, parseDate, shortArabicDate } from '../../core/util/dates';
import { AED, formatMoney } from '../../core/util/money';

/**
 * The two receipts as images (R-RCP-01, R-RCP-02), with M-SAL's content in the glass identity
 * (DESIGN.md D-36): a dark glass card, always dark, the title and a small wallet on top, then a
 * blue box with the main number (the expenses total, or the budget's remaining), the lists, and
 * "ميزانية الشهر" small at the bottom.
 */

const W = 560;
const PAD = 36;
const RADIUS = 34;
const AR_FONT = '"IBM Plex Sans Arabic", -apple-system, Arial, sans-serif';
const NUM_FONT = '-apple-system, system-ui, "Helvetica Neue", Arial, sans-serif';
const INK = '#F3F6FF';
const DIMK = 'rgba(243,246,255,.62)';
const RULE = 'rgba(120,160,255,.24)';
const NEG = '#FF8F85';
const GRN = '#5CDC9F';
const ON_BLUE = 'rgba(255,255,255,.92)';
const BOX_H = 118;

const NF = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });
/** M-SAL's formatValue: "~" before approximate amounts */
const value = (v: number, approx: boolean) => (approx ? '~' : '') + NF.format(v);

type Draw = (ctx: CanvasRenderingContext2D, draw: boolean) => number;

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}

/** The dark card with its soft circles, then the content drawn by layout (which returns the height). */
function glassCard(layout: Draw): HTMLCanvasElement {
  const probe = document.createElement('canvas').getContext('2d')!;
  const H = Math.ceil(layout(probe, false));
  let scale = (window.devicePixelRatio || 1) * 2;
  // Very long receipts stay inside the browser's canvas limits
  scale = Math.max(1, Math.min(scale, Math.sqrt(14_000_000 / (W * H)), 16_000 / H, 16_000 / W));
  const canvas = document.createElement('canvas');
  canvas.width = Math.floor(W * scale);
  canvas.height = Math.floor(H * scale);
  const ctx = canvas.getContext('2d')!;
  ctx.scale(scale, scale);
  roundRect(ctx, 0, 0, W, H, RADIUS);
  ctx.save();
  ctx.clip();
  const bg = ctx.createLinearGradient(0, 0, 0, H);
  bg.addColorStop(0, '#0B1426');
  bg.addColorStop(1, '#131C33');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);
  const glow = (x: number, y: number, r: number, color: string) => {
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, color);
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(x - r, y - r, r * 2, r * 2);
  };
  glow(40, 30, 300, 'rgba(61,120,235,.38)');
  glow(W - 30, H - 40, 260, 'rgba(43,196,141,.16)');
  layout(ctx, true);
  ctx.restore();
  ctx.strokeStyle = 'rgba(120,160,255,.28)';
  ctx.lineWidth = 1.5;
  roundRect(ctx, 0.75, 0.75, W - 1.5, H - 1.5, RADIUS);
  ctx.stroke();
  return canvas;
}

/** The app's wallet, small, top left (the icon's glass wallet, as in floating-wallet.ts). */
function wallet(ctx: CanvasRenderingContext2D, x: number, y: number): void {
  ctx.save();
  ctx.translate(x, y);
  ctx.save();
  ctx.rotate(-0.18);
  ctx.fillStyle = '#3D78EB';
  roundRect(ctx, 6, 4, 30, 22, 5);
  ctx.fill();
  ctx.restore();
  ctx.fillStyle = 'rgba(43,196,141,.9)';
  roundRect(ctx, 30, 8, 22, 16, 4);
  ctx.fill();
  ctx.fillStyle = 'rgba(150,185,255,.5)';
  ctx.strokeStyle = 'rgba(205,222,255,.75)';
  ctx.lineWidth = 1.2;
  roundRect(ctx, 0, 18, 56, 34, 10);
  ctx.fill();
  ctx.stroke();
  roundRect(ctx, 38, 27, 24, 15, 7.5);
  ctx.fill();
  ctx.stroke();
  ctx.fillStyle = '#E8EEFF';
  ctx.beginPath();
  ctx.arc(46, 34.5, 3, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
}

function rule(ctx: CanvasRenderingContext2D, draw: boolean, y: number, dashed: boolean): void {
  if (!draw) return;
  ctx.strokeStyle = RULE;
  ctx.lineWidth = 1;
  ctx.setLineDash(dashed ? [3, 5] : []);
  ctx.beginPath();
  ctx.moveTo(PAD, y);
  ctx.lineTo(W - PAD, y);
  ctx.stroke();
  ctx.setLineDash([]);
}

/** Text helpers shared by both receipts. */
function writer(ctx: CanvasRenderingContext2D, draw: boolean, at: () => number) {
  return {
    arabic(text: string, size: number, weight: number, color: string, align: CanvasTextAlign = 'right', x = W - PAD, maxW?: number) {
      if (!draw) return;
      ctx.font = `${weight} ${size}px ${AR_FONT}`;
      ctx.fillStyle = color;
      ctx.direction = 'rtl';
      ctx.textAlign = align;
      let t = text;
      if (maxW) {
        while (t.length > 1 && ctx.measureText(t).width > maxW) t = t.slice(0, -1);
        if (t !== text) t = t.replace(/\s+$/, '') + '…';
      }
      ctx.fillText(t, x, at());
    },
    plain(text: string, size: number, weight: number, color: string, align: CanvasTextAlign, x: number) {
      if (!draw) return;
      ctx.font = `${weight} ${size}px ${NUM_FONT}`;
      ctx.fillStyle = color;
      ctx.direction = 'ltr';
      ctx.textAlign = align;
      ctx.fillText(text, x, at());
    },
  };
}

/** The title, the subtitle and the date, with the wallet on the left. */
function header(ctx: CanvasRenderingContext2D, draw: boolean, y: number, title: string, subtitle: string, printedOn: string): number {
  let at = y;
  const w = writer(ctx, draw, () => at);
  if (draw) wallet(ctx, PAD, y - 34);
  w.arabic(title, 24, 700, INK, 'right', W - PAD, W - 2 * PAD - 90);
  at += 28;
  w.arabic(subtitle, 15, 500, DIMK, 'right', W - PAD, W - 2 * PAD - 90);
  at += 22;
  w.plain(printedOn, 12, 400, DIMK, 'right', W - PAD);
  return at + 22;
}

/** The blue box with the main number. */
function blueBox(ctx: CanvasRenderingContext2D, draw: boolean, y: number, label: string, big: string, bigColor: string, line: string): number {
  if (draw) {
    const g = ctx.createLinearGradient(PAD, y, W - PAD, y + BOX_H);
    g.addColorStop(0, '#2F6BE0');
    g.addColorStop(1, '#1D47A8');
    ctx.fillStyle = g;
    roundRect(ctx, PAD, y, W - 2 * PAD, BOX_H, 22);
    ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,.22)';
    ctx.lineWidth = 1;
    ctx.stroke();
  }
  let at = y + 34;
  const w = writer(ctx, draw, () => at);
  w.arabic(label, 15, 500, ON_BLUE, 'right', W - PAD - 20);
  at += 44;
  w.plain(big, 38, 700, bigColor, 'left', PAD + 20);
  at += 26;
  w.arabic(line, 14, 500, ON_BLUE, 'right', W - PAD - 20, W - 2 * PAD - 40);
  return y + BOX_H + 30;
}

function footer(ctx: CanvasRenderingContext2D, draw: boolean, y: number): number {
  let at = y + 4;
  writer(ctx, draw, () => at).arabic('ميزانية الشهر', 13, 500, DIMK, 'center', W / 2);
  at += 26;
  return at;
}

/** R-RCP-02: the monthly budget (M-SAL's receiptLayout): salary, groups and items, remaining. */
export function drawBudgetReceipt(budget: Budget, printedOn: string): HTMLCanvasElement {
  return glassCard((ctx, draw) => {
    let y = header(ctx, draw, 58, budget.name, budget.monthLabel, printedOn);
    const final = Number(budget.totals.final);
    y = blueBox(ctx, draw, y, 'المتبقي', value(final, budget.totals.finalApprox), final < 0 ? '#FFD2CC' : '#FFFFFF',
      (budget.salaryLabel || 'الراتب') + ' ' + value(Number(budget.salary.amount) || 0, budget.salary.isApprox));
    const w = writer(ctx, draw, () => y);
    const number = (text: string, size: number, weight: number, color: string) => w.plain(text, size, weight, color, 'left', PAD);

    for (const b of budget.groups) {
      w.arabic(b.title || 'مجموعة', 17, 700, INK);
      number(value(Number(b.totals.subtotal), b.totals.subtotalApprox), 16, 700, INK);
      y += 28;
      for (const r of b.items) {
        const c = r.isDone ? DIMK : INK;
        w.arabic((r.isDone ? '✓ ' : '') + (r.name || '—'), 15, 400, c);
        number(r.amount === null ? '-' : value(Number(r.amount), r.isApprox), 15, r.isDone ? 400 : 500, c);
        y += 25;
      }
      y += 2;
      w.arabic('المتبقي بعده', 13, 400, DIMK);
      number(value(Number(b.totals.balanceAfter), b.totals.balanceApprox), 14, 500, Number(b.totals.balanceAfter) < 0 ? NEG : DIMK);
      y += 16;
      rule(ctx, draw, y, true);
      y += 30;
    }
    return footer(ctx, draw, y);
  });
}

/** R-RCP-01: the expenses of one cycle (M-SAL's expReceiptLayout). */
export function drawExpensesReceipt(m: Receipt, today: string, printedOn: string): HTMLCanvasElement {
  const COL = PAD + 190;
  return glassCard((ctx, draw) => {
    const date = (d: string | null) => (d && parseDate(d) ? shortArabicDate(d) : d ?? '');
    const period = m.start ? date(m.start) + ' ← ' + date(m.end) : 'مصاريف بدون دورة';
    let y = header(ctx, draw, 58, 'إيصال المصاريف', m.accountName + ' · ' + period, printedOn);
    const extra = Number(m.extra);
    const remaining = Number(m.remaining);
    const parts = [];
    if (m.start) parts.push('المخصَّص ' + formatMoney(Number(m.allowance)));
    if (extra > 0) parts.push('دخل إضافي ' + formatMoney(extra));
    parts.push('المتبقي ' + formatMoney(remaining));
    y = blueBox(ctx, draw, y, 'إجمالي المصروف', formatMoney(Number(m.spent)) + ' ' + AED, '#FFFFFF', parts.join(' · '));

    const w = writer(ctx, draw, () => y);
    const amount = (v: string | number, size: number, weight: number, color: string) => {
      if (!draw) return;
      ctx.fillStyle = color;
      ctx.textAlign = 'right';
      let x = COL;
      ctx.font = `${weight} ${Math.round(size * 0.8)}px ${AR_FONT}`;
      ctx.direction = 'rtl';
      ctx.fillText(AED, x, y);
      x -= ctx.measureText(AED).width + 5;
      ctx.direction = 'ltr';
      ctx.font = `${weight} ${size}px ${NUM_FONT}`;
      ctx.fillText(typeof v === 'string' ? v : formatMoney(v), x, y);
    };
    const nameW = W - 2 * PAD - 230;

    if (m.categories.length) {
      w.arabic('حسب الفئة', 17, 700, INK);
      y += 28;
      for (const c of m.categories) {
        w.arabic(c.name, 15, 400, INK, 'right', W - PAD, nameW);
        amount(Number(c.total), 15, 500, INK);
        w.plain(c.pct + '%', 13, 400, DIMK, 'left', PAD);
        y += 25;
      }
      y += 2;
      rule(ctx, draw, y, true);
      y += 28;
    }
    for (const d of m.days) {
      w.arabic(dayLabel(d.date, today), 15, 700, INK, 'right', W - PAD, nameW);
      amount(Number(d.spent), 15, 700, INK);
      y += 25;
      for (const e of d.entries) {
        const inc = e.type === 'INCOME';
        w.arabic(e.name || '—', 14, 400, inc ? GRN : DIMK, 'right', W - PAD, nameW);
        amount((inc ? '+' : '') + formatMoney(e.amount), 14, 500, inc ? GRN : INK);
        y += 22;
      }
      y += 8;
    }
    if (remaining < 0) {
      w.arabic('تجاوزت المتاح', 14, 600, NEG, 'center', W / 2);
      y += 24;
    }
    rule(ctx, draw, y, false);
    y += 26;
    return footer(ctx, draw, y);
  });
}
