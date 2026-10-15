import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

// A magnifier built of pixels: a ring that is the quorum, one red pixel block that is the decision, and a handle.
const MARK = [
  '...######...',
  '..########..',
  '.###....###.',
  '.##......##.',
  '.##..rr..##.',
  '.##..rr..##.',
  '.##......##.',
  '.###....####',
  '..########.##',
  '...######..###',
  '.........####',
  '..........###'
];

/** The Quorum mark, in pixels, in the text colour with one signal-red block. */
@Component({
  selector: 'app-brand-logo',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 14 12" shape-rendering="crispEdges" aria-hidden="true" class="mark">
      @for (cell of cells; track cell.key) {
        <rect [attr.x]="cell.x" [attr.y]="cell.y" width="1" height="1" [attr.fill]="cell.red ? 'var(--signal)' : 'var(--text)'" />
      }
    </svg>
    @if (named()) {
      <span class="name">QUORUM</span>
    }
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        gap: 10px;
      }
      .mark {
        flex: none;
      }
      .name {
        font-family: var(--font-dot);
        font-weight: 900;
        font-size: 1.15em;
        letter-spacing: 0.08em;
        color: var(--logo-name-color, var(--text));
      }
    `
  ]
})
export class BrandLogoComponent {
  readonly size = input(28);
  readonly withName = input(true);
  /** Just the mark, for the collapsed sidebar. */
  readonly compact = input(false);

  protected readonly named = computed(() => this.withName() && !this.compact());

  protected readonly cells = MARK.flatMap((row, y) =>
    [...row].flatMap((ch, x) => (ch === '.' ? [] : [{ key: `${x}-${y}`, x, y, red: ch === 'r' }]))
  );
}
