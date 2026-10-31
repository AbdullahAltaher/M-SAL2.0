import { DOCUMENT, NgComponentOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, effect, inject, signal, viewChild } from '@angular/core';

import { Sheets } from './sheets';

const OPEN_MS = 400;
const CLOSE_MS = 260;

const reducedMotion = () => typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

/**
 * Draws the open sheet with M-SAL's markup and classes (sheet-back, sheet, sheet-grab...), so
 * the copied CSS animates it the same way: slide up, drag the grabber down to close (past 30%
 * of its height or a fast flick), tap outside or press Escape to close (enableSheetDrag).
 */
@Component({
  selector: 'app-sheet-host',
  imports: [NgComponentOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (sheets.current(); as s) {
      <div class="sheet-back" [class.on]="on()" [class.settled]="settled()" [class.closing]="sheets.closing()"
           [class.drag]="dragging()" (click)="backdrop($event)" #back>
        <div class="sheet" role="dialog" aria-modal="true" [attr.aria-label]="s.title()" tabindex="-1" #sheet>
          <div class="sheet-grab" aria-hidden="true" (pointerdown)="dragStart($event)" (pointermove)="dragMove($event)"
               (pointerup)="dragEnd($event, false)" (pointercancel)="dragEnd($event, true)"></div>
          <div class="sheet-head">
            <h2 class="sheet-title" dir="rtl">{{ s.title() }}</h2>
            <button class="icon-btn sheet-x" type="button" aria-label="إغلاق" (click)="sheets.close()">
              <span class="sx-glyph" aria-hidden="true">×</span><span class="sx-text">إلغاء</span>
            </button>
          </div>
          <div class="sheet-body">
            <ng-container *ngComponentOutlet="s.component; inputs: s.inputs" />
          </div>
        </div>
      </div>
    }
  `,
})
export class SheetHost {
  protected readonly sheets = inject(Sheets);
  private readonly document = inject(DOCUMENT);

  protected readonly on = signal(false);
  protected readonly settled = signal(false);
  protected readonly dragging = signal(false);

  private readonly back = viewChild<ElementRef<HTMLElement>>('back');
  private readonly sheet = viewChild<ElementRef<HTMLElement>>('sheet');

  private opener: Element | null = null;
  private y0: number | null = null;
  private last = 0;
  private lastT = 0;
  private velocity = 0;

  constructor() {
    // Open: paint below the screen first, then slide in
    effect(() => {
      const s = this.sheets.current();
      if (!s || this.sheets.closing()) return;
      this.opener = this.document.activeElement;
      this.on.set(false);
      this.settled.set(false);
      this.document.documentElement.classList.add('noscroll');
      requestAnimationFrame(() => requestAnimationFrame(() => {
        this.on.set(true);
        if (!s.keepFocus) this.sheet()?.nativeElement.focus({ preventScroll: true });
      }));
      setTimeout(() => this.settled.set(true), reducedMotion() ? 160 : OPEN_MS + 30);
    });
    // Close: slide down, then remove
    effect(() => {
      if (!this.sheets.closing()) return;
      const closing = this.sheets.current();
      this.on.set(false);
      setTimeout(() => {
        // Another sheet may have opened meanwhile (such as "new category" from "categories")
        if (this.sheets.current() !== closing || !this.sheets.closing()) return;
        this.sheets.finishClose();
        this.document.documentElement.classList.remove('noscroll');
        if (this.opener instanceof HTMLElement && this.document.contains(this.opener)) {
          this.opener.focus({ preventScroll: true });
        }
      }, reducedMotion() ? 140 : CLOSE_MS);
    });
  }

  protected backdrop(event: MouseEvent): void {
    if (event.target === this.back()?.nativeElement) this.sheets.close();
  }

  @HostListener('document:keydown.escape')
  protected escape(): void {
    if (this.sheets.current()) this.sheets.close();
  }

  protected dragStart(event: PointerEvent): void {
    if (!this.settled()) return;
    this.y0 = event.clientY;
    this.last = event.clientY;
    this.lastT = performance.now();
    this.velocity = 0;
    (event.target as HTMLElement).setPointerCapture?.(event.pointerId);
    this.dragging.set(true);
  }

  protected dragMove(event: PointerEvent): void {
    if (this.y0 === null) return;
    const dy = Math.max(0, event.clientY - this.y0);
    const now = performance.now();
    if (now > this.lastT) {
      this.velocity = (event.clientY - this.last) / (now - this.lastT);
      this.last = event.clientY;
      this.lastT = now;
    }
    const sheet = this.sheet()?.nativeElement;
    if (sheet) {
      sheet.style.transform = `translateY(${dy}px)`;
      this.back()?.nativeElement.style.setProperty('--dim', String(Math.max(0, 1 - dy / (sheet.offsetHeight || 1))));
    }
  }

  protected dragEnd(event: PointerEvent, cancelled: boolean): void {
    if (this.y0 === null) return;
    const dy = cancelled ? 0 : Math.max(0, event.clientY - this.y0);
    this.y0 = null;
    this.dragging.set(false);
    const sheet = this.sheet()?.nativeElement;
    const height = sheet?.offsetHeight || 1;
    if (sheet) {
      void sheet.offsetHeight;
      sheet.style.transform = '';
    }
    if (!cancelled && (dy > height * 0.3 || (this.velocity > 0.6 && dy > 8))) this.sheets.close();
    else this.back()?.nativeElement.style.removeProperty('--dim');
  }
}
