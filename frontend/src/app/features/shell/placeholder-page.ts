import { Component, input } from '@angular/core';

/**
 * A screen that is built in phase 5 (PLAN.md section 14). The route gives its title.
 */
@Component({
  selector: 'app-placeholder-page',
  template: `
    <section class="panel">
      <h2>{{ title() }}</h2>
      <p>هذه الشاشة تُبنى في المرحلة 5.</p>
    </section>
  `,
  styles: `
    h2 { font: 600 20px var(--ar); margin-bottom: 6px; }
    p { color: var(--sec); }
  `,
})
export class PlaceholderPage {
  readonly title = input('');
}
