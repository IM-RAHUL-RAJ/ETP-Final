import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** 24 by 24 stroke icons. One path each, drawn in the page's text colour by the global svg.i rule. */
export const ICONS = {
  dashboard: 'M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z',
  orders: 'M9 4h6v3H9z M7 5.5H6a1 1 0 0 0-1 1V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V6.5a1 1 0 0 0-1-1h-1 M9 12h6 M9 16h6',
  ticket: 'M4 6h16v4a2 2 0 0 0 0 4v4H4v-4a2 2 0 0 0 0-4z M13 6v12',
  holdings: 'M3 17l6-6 4 4 8-8 M15 7h6v6',
  portfolio: 'M12 3v9h9 M20.5 14A9 9 0 1 1 10 3.5',
  star: 'M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z',
  bell: 'M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9 M10 21a2 2 0 0 0 4 0',
  inbox: 'M4 13l2-8h12l2 8 M4 13v6h16v-6 M4 13h5l1 2h4l1-2h5',
  settings:
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21a8 8 0 0 1 16 0',
  logout: 'M9 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h4 M16 8l4 4-4 4 M20 12H9',
  refresh: 'M20 11a8 8 0 1 0-2.3 5.7 M20 4v7h-7',
  trash: 'M4 7h16 M10 11v6 M14 11v6 M6 7l1 13h10l1-13 M9 7V4h6v3',
  edit: 'M4 20h4L19 9l-4-4L4 16z M13 7l4 4',
  plus: 'M12 5v14 M5 12h14',
  download: 'M12 4v11 M7 11l5 5 5-5 M5 20h14',
  copy: 'M9 9h10v11H9z M5 15V4h10',
  filter: 'M4 5h16l-6 8v6l-4-2v-4z',
  sort: 'M8 5v14 M4 15l4 4 4-4 M16 19V5 M12 9l4-4 4 4',
  history: 'M3 12a9 9 0 1 0 3-6.7 M3 4v5h5 M12 8v4l3 2',
  chevronLeft: 'M15 6l-6 6 6 6',
  chevronRight: 'M9 6l6 6-6 6',
  chevronDown: 'M6 9l6 6 6-6',
  menu: 'M4 7h16 M4 12h16 M4 17h16',
  close: 'M6 6l12 12 M18 6L6 18',
  check: 'M5 12l5 5 9-10',
  wallet: 'M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M3 7V6a2 2 0 0 1 2-2h12 M16 14h2',
  arrowUpRight: 'M7 17L17 7 M8 7h9v9',
  arrowDownLeft: 'M17 7L7 17 M16 17H7V8',
  shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z',
  sun: 'M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4',
  moon: 'M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z',
  monitor: 'M3 5h18v11H3z M8 20h8 M12 16v4',
  mail: 'M3 6h18v12H3z M3 7l9 7 9-7',
  candles: 'M7 4v3 M7 15v4 M5 7h4v8H5z M17 3v4 M17 17v4 M15 7h4v10h-4z',
  chat: 'M4 5h16v11H11l-5 4v-4H4z M8 10h8',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 7v5l3 2'
} as const;

export type IconName = keyof typeof ICONS;

@Component({
  selector: 'app-icon',
  standalone: true,
  template: `<svg class="i" viewBox="0 0 24 24" aria-hidden="true" focusable="false" [style.width.px]="size()" [style.height.px]="size()"><path [attr.d]="path()" /></svg>`,
  styles: [':host{display:inline-flex;line-height:0}'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input(20);
  protected readonly path = computed(() => ICONS[this.name()]);
}
