import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** M-SAL's ICONS (index.html line 2700): inline SVG paths, no files. */
const ICONS: Record<string, string[]> = {
  home: ['M3.5 11L12 4l8.5 7', 'M5.5 9.8V20h13V9.8', 'M10 20v-5.5h4V20'],
  list: ['M8.5 6.5H20', 'M8.5 12H20', 'M8.5 17.5H20', 'M4 6.5h.01', 'M4 12h.01', 'M4 17.5h.01'],
  plus: ['M12 5v14', 'M5 12h14'],
  chart: ['M5 20v-8', 'M12 20V5', 'M19 20V9'],
  more: ['M5 12h.01', 'M12 12h.01', 'M19 12h.01'],
  search: ['M11 4.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13z', 'M16 16l4.5 4.5'],
  edit: ['M4 20l1-4L16 5a2.1 2.1 0 0 1 3 3L8 19z'],
  copy: ['M9 9h11v11H9z', 'M5 15H4V4h11v1'],
  trash: ['M5 7h14', 'M10 7V4h4v3', 'M7 7l1 13h8l1-13'],
  chevL: ['M15 6l-6 6 6 6'],
  chevR: ['M9 6l6 6-6 6'],
  calendar: ['M4 6h16v14H4z', 'M4 10h16', 'M8 3v4', 'M16 3v4'],
  repeat: ['M17 3l3 3-3 3', 'M4 11V9a3 3 0 0 1 3-3h13', 'M7 21l-3-3 3-3', 'M20 13v2a3 3 0 0 1-3 3H4'],
  wallet: ['M3 7a2 2 0 0 1 2-2h13v4', 'M3 7v10a2 2 0 0 0 2 2h15V9H5a2 2 0 0 1-2-2z', 'M16 14h.01'],
  grid: ['M4 4h6v6H4z', 'M14 4h6v6h-6z', 'M4 14h6v6H4z', 'M14 14h6v6h-6z'],
  receipt: ['M6 3h12v18l-3-2-3 2-3-2-3 2z', 'M9 8h6', 'M9 12h6'],
  sliders: ['M4 7h9', 'M17 7h3', 'M4 17h3', 'M11 17h9', 'M15 4v6', 'M9 14v6'],
  share: ['M12 15V4', 'M8 8l4-4 4 4', 'M5 13v6h14v-6'],
};

@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 24 24" [attr.width]="size()" [attr.height]="size()" fill="none" stroke="currentColor"
         [attr.stroke-width]="sw()" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
      @for (d of paths(); track $index) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
  styles: `:host { display: contents; }`,
})
export class Icon {
  readonly name = input.required<string>();
  readonly size = input(24);
  readonly sw = input(1.8);
  protected readonly paths = computed(() => ICONS[this.name()] ?? []);
}
