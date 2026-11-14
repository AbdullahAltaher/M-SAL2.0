import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, viewChild } from '@angular/core';

import { Icon } from '../../shared/components/icon';
import { MoneyView } from '../../shared/components/money';
import { Gratitude } from './gratitude';

/**
 * The cards of D-42: after adding, in the middle of a blurred screen (a tap anywhere closes it,
 * it goes by itself after a few seconds; "تراجع" stays sharp above it); for a new cycle, a full
 * card closed by "الحمد لله".
 */
@Component({
  selector: 'app-gratitude-host',
  imports: [Icon, MoneyView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (g.card(); as c) {
      @for (k of [c.key]; track k) {
        @if (c.kind === 'added') {
          <div class="gr-float" (click)="g.close()">
            <button class="gr-card" type="button" aria-live="polite" aria-label="إغلاق" (click)="g.close()">
              <span class="gr-kind" dir="rtl"><span class="gr-star" aria-hidden="true">✦</span>{{ c.text.type }}</span>
              <span class="gr-text" [class.verse]="c.text.type === 'آية'" dir="rtl">{{ c.text.type === 'آية' ? '﴿' + c.text.text + '﴾' : c.text.text }}</span>
              <span class="gr-src" dir="rtl">{{ c.text.source }}</span>
              <span class="gr-added" dir="rtl">{{ c.note }}@if (c.amount) {<app-money [value]="c.amount" />}</span>
            </button>
          </div>
        } @else {
          <div class="gr-back">
            <div class="gr-sheet" role="dialog" aria-modal="true" aria-labelledby="gr-title">
              <div class="gr-wallet"><app-icon name="wallet" [size]="30" /></div>
              <h2 class="gr-title" id="gr-title" dir="rtl">وصل الراتب</h2>
              @if (c.amount) {
                <div class="gr-amount"><app-money [value]="c.amount" /></div>
              } @else {
                <div class="gr-sub" dir="rtl">بدأت دورة جديدة</div>
              }
              @if (c.verse; as v) {
                <div class="gr-frame">
                  <div class="gr-text verse" dir="rtl">﴿{{ v.text }}﴾</div>
                  <div class="gr-src" dir="rtl">{{ v.source }}</div>
                </div>
              }
              @if (c.dua; as d) {
                <div class="gr-dua" dir="rtl">{{ d.text }}</div>
                <div class="gr-src" dir="rtl">{{ d.source }}</div>
              }
              <button #thanks class="g-btn primary gr-thanks" type="button" (click)="g.close()">الحمد لله</button>
            </div>
          </div>
        }
      }
    }
  `,
})
export class GratitudeHost {
  protected readonly g = inject(Gratitude);
  private readonly thanks = viewChild<ElementRef<HTMLButtonElement>>('thanks');

  constructor() {
    // The salary card is a dialog: its one button takes the focus
    effect(() => this.thanks()?.nativeElement.focus({ preventScroll: true }));
  }
}
