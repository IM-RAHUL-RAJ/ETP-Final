import { ChangeDetectionStrategy, Component, DestroyRef, ViewEncapsulation, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ANIMATIONS, AnimationName, MASCOT_LABEL, MascotType, gridToSvg, renderGrid } from '../../../../core/mascot/pixel-mascot';
import { MascotService } from '../../../../core/mascot/mascot.service';

export type MascotState = 'idle' | 'loading' | 'happy' | 'empty';

const THOUGHTS: Record<MascotType, readonly string[]> = {
  owl: ['Hoo knows? Check the news first.', 'Patience is a position too.', 'Wise owls diversify.'],
  rabbit: ['Hop in slowly. Small steps add up.', 'Carrots grow. So can a plan.', 'No rush. Markets reopen tomorrow.'],
  cat: ['Purr-fect time to review your alerts.', 'Nap first, trade later.', 'Curious is fine. Impulsive is not.']
};

/** Rendering a frame is pure, and the same few frames repeat forever: keep them. */
const SVG_CACHE = new Map<string, string>();

function svgOf(type: MascotType, name: AnimationName, index: number): string {
  const key = `${type}|${name}|${index}`;
  let svg = SVG_CACHE.get(key);
  if (svg === undefined) {
    svg = gridToSvg(type, renderGrid(type, ANIMATIONS[name][index]), MASCOT_LABEL[type]);
    SVG_CACHE.set(key, svg);
  }
  return svg;
}

/**
 * The customer's pixel companion. It is never still: it breathes and blinks when idle, and plays an animation
 * for what is happening (thumbs up, dance, wave, cheer, think, sad). Tap it and it dances and says something.
 * Frames are stepped, like a sprite sheet. Under reduced motion it holds its first frame.
 * The artwork is generated from our own constants, so it is the one place innerHTML is trusted.
 */
@Component({
  selector: 'app-mascot',
  standalone: true,
  templateUrl: './mascot.component.html',
  styleUrl: './mascot.component.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MascotComponent {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly preferred = inject(MascotService);
  private readonly destroyRef = inject(DestroyRef);

  /** Leave out to use the companion the customer picked. */
  readonly type = input<MascotType | null>(null);
  /** The older, coarser way to ask for a mood. `action` wins when both are given. */
  readonly state = input<MascotState>('idle');
  readonly action = input<AnimationName | null>(null);
  /** Height in pixels. */
  readonly size = input(120);
  readonly interactive = input(true);

  protected readonly thought = signal<string | null>(null);
  private readonly poked = signal<AnimationName | null>(null);
  private readonly frame = signal(0);
  private timer: ReturnType<typeof setTimeout> | null = null;
  private pokeTimer: ReturnType<typeof setTimeout> | null = null;
  private thoughtTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

  protected readonly resolvedType = computed(() => this.type() ?? this.preferred.type());
  protected readonly label = computed(() => MASCOT_LABEL[this.resolvedType()]);

  /** What it is doing right now. */
  readonly animation = computed<AnimationName>(() => {
    const requested = this.poked() ?? this.action();
    if (requested) {
      return requested;
    }
    switch (this.state()) {
      case 'happy':
        return 'happy';
      case 'empty':
      case 'loading':
        return 'think';
      default:
        return 'idle';
    }
  });

  protected readonly svg = computed<SafeHtml>(() => {
    const frames = ANIMATIONS[this.animation()];
    return this.sanitizer.bypassSecurityTrustHtml(svgOf(this.resolvedType(), this.animation(), this.frame() % frames.length));
  });

  constructor() {
    // A new animation starts from its first frame.
    effect(() => {
      this.animation();
      untracked(() => {
        this.frame.set(0);
        this.schedule();
      });
    });
    this.destroyRef.onDestroy(() => {
      for (const timer of [this.timer, this.pokeTimer, this.thoughtTimer]) {
        if (timer !== null) {
          clearTimeout(timer);
        }
      }
    });
  }

  private schedule(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (this.reduced) {
      return;
    }
    const frames = ANIMATIONS[this.animation()];
    this.timer = setTimeout(() => {
      this.frame.update((index) => (index + 1) % frames.length);
      this.schedule();
    }, frames[this.frame() % frames.length].ms);
  }

  protected poke(): void {
    if (!this.interactive()) {
      return;
    }
    const lines = THOUGHTS[this.resolvedType()];
    this.thought.set(lines[Math.floor(Math.random() * lines.length)]);
    this.poked.set('dance');
    if (this.pokeTimer !== null) {
      clearTimeout(this.pokeTimer);
    }
    if (this.thoughtTimer !== null) {
      clearTimeout(this.thoughtTimer);
    }
    this.pokeTimer = setTimeout(() => this.poked.set(null), 2600);
    this.thoughtTimer = setTimeout(() => this.thought.set(null), 3400);
  }
}
