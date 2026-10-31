import { Directive, ElementRef, effect, inject, input } from '@angular/core';

/**
 * M-SAL's collapsible(): a section that folds to nothing and opens again by animating its
 * height. It clips only while moving (so focus rings are never cut), and a folded section is
 * inert and hidden from screen readers. Use on an element with class "g-col".
 */
@Directive({ selector: '[appCollapse]' })
export class Collapse {
  readonly appCollapse = input.required<boolean>();
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private first = true;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => this.set(this.appCollapse()));
  }

  private set(open: boolean): void {
    const el = this.el;
    if (this.timer) clearTimeout(this.timer);
    el.classList.toggle('open', open);
    el.inert = !open;
    el.setAttribute('aria-hidden', String(!open));
    if (this.first) {
      this.first = false;
      el.style.height = open ? 'auto' : '0px';
      return;
    }
    el.classList.add('anim');
    if (open) {
      el.style.height = (el.firstElementChild as HTMLElement | null)?.scrollHeight + 'px';
    } else {
      el.style.height = el.offsetHeight + 'px';
      void el.offsetHeight;
      el.style.height = '0px';
    }
    this.timer = setTimeout(() => {
      el.classList.remove('anim');
      if (open) el.style.height = 'auto';
    }, 260);
  }
}
