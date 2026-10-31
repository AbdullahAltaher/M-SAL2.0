import { Budget, Receipt } from '../../core/api/models';
import { dayLabel } from '../../core/util/dates';
import { AED, formatMoney } from '../../core/util/money';

/**
 * The two receipts as paper-style images (R-RCP-01, R-RCP-02), ported from M-SAL's
 * receiptLayout, drawReceipt, expReceiptLayout and drawExpReceipt with the same sizes and colors.
 */

const W = 560;
const PAD = 40;
const TEETH = 12;
const TOOTH_W = 16;
const AR_FONT = '"IBM Plex Sans Arabic", -apple-system, Arial, sans-serif';
const NUM_FONT = '-apple-system, system-ui, "Helvetica Neue", Arial, sans-serif';
const INK = '#24231F';
const DIMK = '#77736A';
const RULE = '#B5AE9F';
const NEG = '#B8473F';
const GRN = '#38594A';

const NF = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });
/** M-SAL's formatValue: "~" before approximate amounts */
const value = (v: number, approx: boolean) => (approx ? '~' : '') + NF.format(v);

/** The torn-paper shape, then the content drawn by layout (which returns the height). */
function paper(layout: (ctx: CanvasRenderingContext2D, draw: boolean) => number): HTMLCanvasElement {
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
  ctx.beginPath();
  ctx.moveTo(0, TEETH);
  for (let x = 0; x < W; x += TOOTH_W) {
    ctx.lineTo(x + TOOTH_W / 2, 0);
    ctx.lineTo(Math.min(x + TOOTH_W, W), TEETH);
  }
  ctx.lineTo(W, H - TEETH);
  for (let x = W; x > 0; x -= TOOTH_W) {
    ctx.lineTo(x - TOOTH_W / 2, H);
    ctx.lineTo(Math.max(x - TOOTH_W, 0), H - TEETH);
  }
  ctx.closePath();
  const g = ctx.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, '#FFFCF5');
  g.addColorStop(1, '#F5F2EA');
  ctx.fillStyle = g;
  ctx.fill();
  ctx.save();
  ctx.clip();
  layout(ctx, true);
  ctx.restore();
  return canvas;
}

function rule(ctx: CanvasRenderingContext2D, draw: boolean, y: number, dashed: boolean): void {
  if (!draw) return;
  ctx.strokeStyle = RULE;
  ctx.lineWidth = 1;
  ctx.setLineDash(dashed ? [2, 4] : []);
  ctx.beginPath();
  ctx.moveTo(PAD, y);
  ctx.lineTo(W - PAD, y);
  ctx.stroke();
  ctx.setLineDash([]);
}

/** R-RCP-02: the monthly budget (M-SAL's receiptLayout). */
export function drawBudgetReceipt(budget: Budget, printedOn: string): HTMLCanvasElement {
  return paper((ctx, draw) => {
    let y = TEETH + 44;
    const arabic = (text: string, size: number, weight: number, color: string, align: CanvasTextAlign = 'right', x = W - PAD) => {
      if (!draw) return;
      ctx.font = `${weight} ${size}px ${AR_FONT}`;
      ctx.fillStyle = color;
      ctx.direction = 'rtl';
      ctx.textAlign = align;
      ctx.fillText(text, x, y);
    };
    const number = (text: string, size: number, weight: number, color: string) => {
      if (!draw) return;
      ctx.font = `${weight} ${size}px ${NUM_FONT}`;
      ctx.fillStyle = color;
      ctx.direction = 'ltr';
      ctx.textAlign = 'left';
      ctx.fillText(text, PAD, y);
    };

    arabic(budget.name, 26, 700, INK, 'center', W / 2);
    y += 30;
    arabic(budget.monthLabel, 19, 500, INK, 'center', W / 2);
    y += 24;
    if (draw) {
      ctx.font = `400 14px ${NUM_FONT}`;
      ctx.fillStyle = DIMK;
      ctx.direction = 'ltr';
      ctx.textAlign = 'center';
      ctx.fillText(printedOn, W / 2, y);
    }
    y += 24;
    rule(ctx, draw, y, true);
    y += 30;

    arabic(budget.salaryLabel || 'الراتب', 19, 600, INK);
    number(value(Number(budget.salary.amount) || 0, budget.salary.isApprox), 19, 600, INK);
    y += 20;
    rule(ctx, draw, y, true);
    y += 32;

    for (const b of budget.groups) {
      arabic(b.title || 'مجموعة', 18, 700, INK);
      y += 28;
      for (const r of b.items) {
        const c = r.isDone ? DIMK : INK;
        arabic(r.name || '—', 16, 400, c);
        if (draw && r.isDone && r.name) {
          ctx.font = `400 16px ${AR_FONT}`;
          const w = ctx.measureText(r.name).width;
          ctx.strokeStyle = DIMK;
          ctx.lineWidth = 1.2;
          ctx.setLineDash([]);
          ctx.beginPath();
          ctx.moveTo(W - PAD - w, y - 5);
          ctx.lineTo(W - PAD, y - 5);
          ctx.stroke();
        }
        number(r.amount === null ? '-' : value(Number(r.amount), r.isApprox), 16, r.isDone ? 400 : 500, c);
        y += 26;
      }
      y += 4;
      arabic('المجموع', 15, 600, INK);
      number(value(Number(b.totals.subtotal), b.totals.subtotalApprox), 16, 600, INK);
      y += 22;
      arabic('المتبقي بعده', 14, 400, DIMK);
      number(value(Number(b.totals.balanceAfter), b.totals.balanceApprox), 15, 500, Number(b.totals.balanceAfter) < 0 ? NEG : DIMK);
      y += 18;
      rule(ctx, draw, y, true);
      y += 32;
    }

    y += 6;
    arabic('المتبقي', 20, 700, INK);
    number(value(Number(budget.totals.final), budget.totals.finalApprox), 34, 600, Number(budget.totals.final) < 0 ? NEG : GRN);
    y += 22;
    rule(ctx, draw, y, false);
    y += 2;
    rule(ctx, draw, y, false);
    y += 34;
    return y + TEETH + 20;
  });
}

/** R-RCP-01: the expenses of one cycle (M-SAL's expReceiptLayout). */
export function drawExpensesReceipt(m: Receipt, today: string, printedOn: string): HTMLCanvasElement {
  const COL = PAD + 190;
  return paper((ctx, draw) => {
    let y = TEETH + 44;
    const font = (w: number, s: number, fam: string) => `${w} ${s}px ${fam}`;
    const arabic = (text: string, size: number, weight: number, color: string, align: CanvasTextAlign = 'right', x = W - PAD, maxW?: number) => {
      if (!draw) return;
      ctx.font = font(weight, size, AR_FONT);
      ctx.fillStyle = color;
      ctx.direction = 'rtl';
      ctx.textAlign = align;
      let t = text;
      if (maxW) {
        while (t.length > 1 && ctx.measureText(t).width > maxW) t = t.slice(0, -1);
        if (t !== text) t = t.replace(/\s+$/, '') + '…';
      }
      ctx.fillText(t, x, y);
    };
    const amount = (v: string | number, size: number, weight: number, color: string) => {
      if (!draw) return;
      ctx.fillStyle = color;
      ctx.textAlign = 'right';
      let x = COL;
      ctx.font = font(weight, Math.round(size * 0.8), AR_FONT);
      ctx.direction = 'rtl';
      ctx.fillText(AED, x, y);
      x -= ctx.measureText(AED).width + 5;
      ctx.direction = 'ltr';
      ctx.font = font(weight, size, NUM_FONT);
      ctx.fillText(typeof v === 'string' ? v : formatMoney(v), x, y);
    };
    const plain = (text: string, size: number, color: string, align: CanvasTextAlign, x: number) => {
      if (!draw) return;
      ctx.font = font(400, size, NUM_FONT);
      ctx.fillStyle = color;
      ctx.direction = 'ltr';
      ctx.textAlign = align;
      ctx.fillText(text, x, y);
    };
    const nameW = W - 2 * PAD - 230;

    arabic(m.accountName, 26, 700, INK, 'center', W / 2, W - 2 * PAD);
    y += 30;
    arabic('إيصال المصاريف', 17, 500, DIMK, 'center', W / 2);
    y += 26;
    if (m.start) {
      arabic('الفترة', 15, 500, DIMK);
      plain(`${m.start}  →  ${m.end}`, 15, INK, 'left', PAD);
    } else {
      arabic('مصاريف بدون دورة', 15, 500, DIMK);
    }
    y += 24;
    plain(printedOn, 13, DIMK, 'center', W / 2);
    y += 22;
    rule(ctx, draw, y, true);
    y += 28;
    const extra = Number(m.extra);
    if (m.start) {
      arabic('المخصَّص', 18, 600, INK);
      amount(Number(m.allowance), 18, 600, INK);
      y += 26;
    }
    if (extra > 0) {
      arabic('دخل إضافي', 16, 500, INK);
      amount(extra, 16, 500, GRN);
      y += 26;
    }
    if (m.start || extra > 0) {
      y += 2;
      rule(ctx, draw, y, true);
      y += 28;
    }
    if (m.categories.length) {
      arabic('حسب الفئة', 18, 700, INK);
      y += 28;
      for (const c of m.categories) {
        arabic(c.name, 16, 400, INK, 'right', W - PAD, nameW);
        amount(Number(c.total), 16, 500, INK);
        plain(c.pct + '%', 13, DIMK, 'left', PAD);
        y += 25;
      }
      y += 2;
      rule(ctx, draw, y, true);
      y += 28;
    }
    for (const d of m.days) {
      arabic(dayLabel(d.date, today), 16, 700, INK, 'right', W - PAD, nameW);
      amount(Number(d.spent), 16, 700, INK);
      y += 25;
      for (const e of d.entries) {
        const inc = e.type === 'INCOME';
        arabic(e.name || '—', 15, 400, inc ? GRN : INK, 'right', W - PAD, nameW);
        amount((inc ? '+' : '') + formatMoney(e.amount), 15, 500, inc ? GRN : INK);
        y += 23;
      }
      y += 6;
    }
    y += 4;
    rule(ctx, draw, y, false);
    y += 32;
    arabic('إجمالي المصروف', 18, 700, INK);
    amount(Number(m.spent), 20, 700, INK);
    y += 36;
    arabic('المتبقي', 20, 700, INK);
    amount(Number(m.remaining), 28, 700, Number(m.remaining) < 0 ? NEG : GRN);
    y += 18;
    y += 8;
    rule(ctx, draw, y, false);
    y += 2;
    rule(ctx, draw, y, false);
    return y + 34 + TEETH;
  });
}
