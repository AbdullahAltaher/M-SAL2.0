import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';

import { SessionStore } from '../session/session-store';

/**
 * Shows the element only if the user may use the service (SCREENS.md section 5):
 * {@code <a *appIfService="'SRV-009'">الإدارة</a>}.
 */
@Directive({ selector: '[appIfService]' })
export class IfService {
  private readonly session = inject(SessionStore);
  private readonly template = inject(TemplateRef<unknown>);
  private readonly view = inject(ViewContainerRef);

  readonly appIfService = input.required<string>();

  private shown = false;

  constructor() {
    effect(() => {
      this.session.user();
      const allowed = this.session.can(this.appIfService());
      if (allowed && !this.shown) {
        this.view.createEmbeddedView(this.template);
        this.shown = true;
      } else if (!allowed && this.shown) {
        this.view.clear();
        this.shown = false;
      }
    });
  }
}
