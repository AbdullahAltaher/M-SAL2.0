import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';

import { formatMoney } from '../../core/util/money';

const DURATION = 900;
const reduceMotion = () => typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

/**
 * A number that counts to its new value instead of jumping (DESIGN.md D-10, D-24): when the
 * amount changes it runs there in 0.9s, slowing at the end, with a small pulse. The first value
 * shows at once, and with "reduce motion" every value does (D-27).
 */
@Component({
  selector: 'app-count',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `{{ text() }}`,
  host: { '[class.bump]': 'bump()' },
  styles: `
    :host { display: inline-block; font-variant-numeric: tabular-nums; }
    :host(.bump) { animation: countBump .45s cubic-bezier(.23, 1, .32, 1); }
    @keyframes countBump { 40% { transform: scale(1.08); } }
  `,
})
export class CountTo {
  readonly value = input.required<number>();
  readonly format = input<(n: number) => string>(formatMoney);

  private readonly shown = signal<number | null>(null);
  protected readonly bump = signal(false);
  protected readonly text = computed(() => this.format()(this.shown() ?? this.value()));

  private frame = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private landing: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    let previous: number | null = null;
    effect(() => {
      const to = this.value();
      untracked(() => {
        const from = this.shown() ?? previous;
        previous = to;
        if (from === null || from === to || reduceMotion()) {
          this.stop();
          this.shown.set(to);
          return;
        }
        this.run(from, to);
      });
    });
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  private run(from: number, to: number): void {
    this.stop();
    this.bump.set(false);
    requestAnimationFrame(() => this.bump.set(true));
    this.timer = setTimeout(() => this.bump.set(false), 500);
    const start = performance.now();
    const step = (now: number) => {
      const k = Math.min(1, (now - start) / DURATION);
      const eased = 1 - Math.pow(1 - k, 3);
      this.shown.set(k === 1 ? to : Math.round((from + (to - from) * eased) * 100) / 100);
      if (k < 1) this.frame = requestAnimationFrame(step);
    };
    this.frame = requestAnimationFrame(step);
    // Frames stop while the app is in the background: the number still ends on its value
    this.landing = setTimeout(() => {
      cancelAnimationFrame(this.frame);
      this.shown.set(to);
    }, DURATION + 100);
  }

  private stop(): void {
    cancelAnimationFrame(this.frame);
    if (this.timer) clearTimeout(this.timer);
    if (this.landing) clearTimeout(this.landing);
  }
}
