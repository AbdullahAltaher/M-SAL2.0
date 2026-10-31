import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Stats } from '../../core/api/models';
import { chartNum, chartX, smoothPath, sliceVar } from '../../core/util/chart';
import { parseDate } from '../../core/util/dates';
import { AED, formatMoney } from '../../core/util/money';
import { Icon } from '../../shared/components/icon';
import { MoneyView } from '../../shared/components/money';
import { Toasts } from '../../shared/toast/toast';
import { EntrySheets } from '../expenses/entry-sheets';
import { ExpensesStore } from '../expenses/expenses-store';

const R = 42;
const CIRC = 2 * Math.PI * R;

/**
 * S-22: statistics of the selected cycle (M-SAL's renderStatsView, R-STA-01 to R-STA-06).
 * The figures come from the server; the charts are hand-written SVG, as in M-SAL.
 */
@Component({
  selector: 'app-stats-view',
  imports: [MoneyView, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stats-view.html',
})
export class StatsView {
  protected readonly store = inject(ExpensesStore);
  protected readonly sheets = inject(EntrySheets);
  private readonly api = inject(Api);
  private readonly toasts = inject(Toasts);

  protected readonly unit = AED;
  protected readonly stats = signal<Stats | null>(null);
  protected readonly today = computed(() => this.store.home()?.today ?? '');
  protected readonly isCycle = computed(() => this.store.selectedKey() !== 'none');

  /** Donut slices (donutCard) */
  protected readonly slices = computed(() => {
    const s = this.stats();
    if (!s) return [];
    const total = Number(s.total) || 1;
    const gap = s.categories.length > 1 ? 1.4 : 0;
    let off = 0;
    return s.categories.map((c, i) => {
      const len = CIRC * (Number(c.total) / total);
      const dash = Math.max(0.5, len - gap);
      const slice = { color: sliceVar(i), dash: `${dash.toFixed(2)} ${(CIRC - dash).toFixed(2)}`, offset: (-off).toFixed(2) };
      off += len;
      return slice;
    });
  });

  /** Last 7 days bars (barsChart) */
  protected readonly bars = computed(() => {
    const days = this.stats()?.lastDays ?? [];
    const W = 300, base = 112, maxH = 78, n = days.length, bw = W / Math.max(1, n);
    const maxV = Math.max(1, ...days.map((d) => Number(d.total)));
    return days.map((d, i) => {
      const total = Number(d.total);
      const w = Math.min(28, bw * 0.62);
      const x = chartX(i, n, bw / 2, W - bw / 2) - w / 2;
      const ht = total > 0 ? Math.max(6, Math.round((maxH * total) / maxV)) : 0;
      return {
        x: x.toFixed(1), w: w.toFixed(1), cx: (x + w / 2).toFixed(1), ht, y: base - ht, trackY: base - maxH, maxH,
        valY: Math.max(14, base - ht - 7), labY: base + 22, val: total > 0 ? chartNum(total) : '',
        day: String(parseDate(d.date)?.d ?? ''), today: d.date === this.today(),
      };
    });
  });

  /** Last 6 cycles curve (trendChart) */
  protected readonly trend = computed(() => {
    const tr = this.stats()?.trend ?? [];
    const left = 28, right = 272, top = 38, base = 126;
    const mx = Math.max(1, ...tr.map((p) => Number(p.total)));
    const pts = tr.map((p, i) => [chartX(i, tr.length, left, right), base - Math.round(((base - top) * Number(p.total)) / mx)] as [number, number]);
    const line = pts.length > 1 ? smoothPath(pts, top, base) : '';
    const area = line ? `${line} L${pts[pts.length - 1][0]} ${base} L${pts[0][0]} ${base} Z` : '';
    return {
      base, line, area,
      points: tr.map((p, i) => {
        const pd = parseDate(p.start);
        return { x: pts[i][0], y: pts[i][1], last: i === tr.length - 1, val: chartNum(p.total),
          valY: Math.max(14, pts[i][1] - 12), lab: pd ? `${pd.d}/${pd.m}` : '' };
      }),
    };
  });

  protected readonly sliceVar = sliceVar;

  constructor() {
    effect(() => {
      this.store.changed();
      const accountId = this.store.accountId();
      const key = this.store.selectedKey();
      if (!accountId || !key) return;
      void this.api.stats(accountId, key).then((s) => this.stats.set(s)).catch((e: ApiError) => this.toasts.show(e.message));
    });
  }

  protected fmt(v: string | number): string {
    return formatMoney(v);
  }

  protected delta(v: string): string {
    return (Number(v) > 0 ? '+' : '') + formatMoney(v);
  }

  protected kind(k: string): string {
    return k === 'NEW' ? 'جديد' : k === 'UP' ? 'ارتفاع' : 'انخفاض';
  }
}
