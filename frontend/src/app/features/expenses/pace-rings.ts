import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { CycleSummary } from '../../core/api/models';

/** Up to this many points ahead of the days is "a little" (yellow); more is orange (D-40) */
const A_LITTLE = 5;

/** ok: spent no faster than the days; ahead: 1 to 5 points ahead; fast: more than 5 */
export type PaceLevel = 'ok' | 'ahead' | 'fast';

/** What the card says under the rings, by level (D-40) */
export const PACE_SAYS: Record<PaceLevel, string> = {
  ok: 'ماشي على الخطة',
  ahead: 'صرفك أسرع بشوي',
  fast: 'صرفك أسرع من الوقت، خفّف شوي',
};

export interface Pace {
  /** Share of the cycle's days gone, today included (0 to 100) */
  time: number;
  /** Share of the cycle's money spent (0 to 100) */
  money: number;
  level: PaceLevel;
}

/**
 * Time against money for an open cycle with a next salary date (DESIGN.md D-40): the days gone
 * (daysElapsed of totalDays) and the money spent (progress), both rounded to whole percents.
 */
export function pace(s: CycleSummary): Pace | null {
  if (!s.open || !s.totalDays) return null;
  const time = Math.round(Math.min(1, s.daysElapsed / s.totalDays) * 100);
  const money = Math.round(Math.max(0, Math.min(1, s.progress)) * 100);
  const ahead = money - time;
  return { time, money, level: ahead <= 0 ? 'ok' : ahead <= A_LITTLE ? 'ahead' : 'fast' };
}

/**
 * The days to the next salary, in Emirati Arabic (D-40). daysLeft counts today up to the day
 * before the salary (R-CYC-04), so 1 means the salary comes tomorrow. A salary date that is
 * today or already passed reads "الراتب اليوم".
 */
export function daysLeftLabel(daysLeft: number, salaryDate: string | null, today: string): string {
  if (salaryDate !== null && salaryDate <= today) return 'الراتب اليوم';
  if (daysLeft <= 1) return 'الراتب باجر';
  if (daysLeft === 2) return 'باقي يومين';
  if (daysLeft <= 10) return 'باقي ' + daysLeft + ' أيام';
  return 'باقي ' + daysLeft + ' يوم';
}

/** The last three days before the salary get the warm colour (D-40) */
export function salarySoon(daysLeft: number): boolean {
  return daysLeft <= 3;
}

const OUT = 2 * Math.PI * 36;
const IN = 2 * Math.PI * 25;

/**
 * Two rings (D-40): outside, the days gone of the cycle; inside, the money spent, green while
 * on plan and amber when it runs ahead. They draw themselves when shown and move with the
 * numbers' count (0.9s) after a save; with "reduce motion" they just show (base.css).
 */
@Component({
  selector: 'app-pace-rings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 84 84" role="img" [attr.aria-label]="label()">
      <circle class="trk" cx="42" cy="42" r="36" />
      <circle class="arc time" cx="42" cy="42" r="36" transform="rotate(-90 42 42)"
              [style.--c]="out" [style.stroke-dasharray]="out" [style.stroke-dashoffset]="out * (1 - time() / 100)" />
      <circle class="trk" cx="42" cy="42" r="25" />
      <circle class="arc money" [class.ahead]="level() === 'ahead'" [class.fast]="level() === 'fast'" cx="42" cy="42" r="25" transform="rotate(-90 42 42)"
              [style.--c]="inner" [style.stroke-dasharray]="inner" [style.stroke-dashoffset]="inner * (1 - money() / 100)" />
    </svg>
  `,
  styles: `
    :host { display: block; flex: none; width: 76px; height: 76px; }
    svg { display: block; width: 100%; height: 100%; overflow: visible; }
    circle { fill: none; stroke-width: 7; }
    .trk { stroke: var(--x-track); }
    .arc { stroke-linecap: round; animation: ringIn .9s cubic-bezier(.23, 1, .32, 1) both;
           transition: stroke-dashoffset .9s cubic-bezier(.33, 1, .68, 1), stroke .3s; }
    .time { stroke: var(--x-accent); }
    .money { stroke: var(--x-inc); animation-delay: .12s; }
    .money.ahead { stroke: var(--x-pace-ahead); }
    .money.fast { stroke: var(--x-pace-fast); }
    @keyframes ringIn { from { stroke-dashoffset: var(--c); } }
    @media (max-width: 359px) { :host { width: 64px; height: 64px; } }
  `,
})
export class PaceRings {
  readonly time = input.required<number>();
  readonly money = input.required<number>();
  readonly level = input<PaceLevel>('ok');

  protected readonly out = OUT;
  protected readonly inner = IN;
  protected readonly label = computed(() => `مضى ${this.time()}% من الدورة، وصُرف ${this.money()}% من الفلوس`);
}
