import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { AED, formatMoney } from '../../core/util/money';

/**
 * M-SAL's money(): the number and "د.إ" in separate elements, so Arabic and digits never share
 * one text run (R-GEN-05).
 */
@Component({
  selector: 'app-money',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="money {{ cls() }}" dir="ltr"><bdi class="n">{{ text() }}</bdi><span class="u" dir="rtl">{{ unit }}</span></span>`,
  styles: `:host { display: contents; }`,
})
export class MoneyView {
  readonly value = input<string | number | null | undefined>(0);
  readonly cls = input('');
  protected readonly unit = AED;
  protected readonly text = computed(() => formatMoney(this.value()));
}
