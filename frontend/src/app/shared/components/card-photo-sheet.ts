import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';

import { CardPhotos, PhotoStyle } from '../../core/photos/card-photos';
import { Toasts } from '../toast/toast';

const STYLES: { style: PhotoStyle; label: string }[] = [
  { style: 'dim', label: 'تعتيم' },
  { style: 'glass', label: 'لوح زجاجي' },
  { style: 'tint', label: 'أزرق' },
];

/**
 * The photo behind a big card (DESIGN.md D-15, D-16): choose one from the album, then how it
 * sits (darkened, clear with a frosted panel, or tinted blue), shown on three small cards.
 */
@Component({
  selector: 'app-card-photo-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (photo(); as p) {
      <div class="cp-styles" role="radiogroup" aria-label="شكل الصورة">
        @for (s of styles; track s.style) {
          <button class="cp-opt" type="button" role="radio" [attr.aria-checked]="p.style === s.style" (click)="setStyle(s.style)">
            <span class="cp-mini hero-photo ph-{{ s.style }}">
              <img class="hp-img" [src]="p.url" alt="">
              <span class="hp-shade"></span>
              <span class="hp-body"><span class="cp-l1"></span><span class="cp-l2"></span></span>
            </span>
            <span class="cp-name">{{ s.label }}</span>
          </button>
        }
      </div>
    }
    <label class="g-btn primary cp-pick">
      <input type="file" accept="image/*" (change)="choose($event)" [disabled]="busy()">
      {{ busy() ? 'لحظة…' : photo() ? 'تغيير الصورة' : 'اختر صورة من الألبوم' }}
    </label>
    @if (photo()) {
      <button class="g-btn cp-remove" type="button" (click)="remove()">إزالة الصورة</button>
    }
    <p class="hint" dir="rtl">الصورة تُحفظ على هذا الجهاز فقط، ولا تظهر على جهاز ثاني.</p>
  `,
})
export class CardPhotoSheet {
  private readonly photos = inject(CardPhotos);
  private readonly toasts = inject(Toasts);

  readonly photoKey = input.required<string>();

  protected readonly styles = STYLES;
  protected readonly busy = signal(false);
  protected readonly photo = computed(() => this.photos.get(this.photoKey()));

  protected async choose(event: Event): Promise<void> {
    const field = event.target as HTMLInputElement;
    const file = field.files?.[0];
    field.value = '';
    if (!file) return;
    this.busy.set(true);
    try {
      await this.photos.set(this.photoKey(), file, this.photo()?.style ?? 'dim');
    } catch {
      this.toasts.show('تعذّر قراءة الصورة أو حفظها.');
    } finally {
      this.busy.set(false);
    }
  }

  protected setStyle(style: PhotoStyle): void {
    void this.photos.setStyle(this.photoKey(), style);
  }

  protected remove(): void {
    void this.photos.remove(this.photoKey());
  }
}
