import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked
} from '@angular/core';
import { DecimalPipe } from '@angular/common';

/** Counts up to its value so a figure arriving feels alive. Snaps straight there under reduced motion. */
@Component({
  selector: 'app-animated-number',
  standalone: true,
  imports: [DecimalPipe],
  template: `{{ shown() | number: format() }}`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AnimatedNumberComponent {
  readonly value = input.required<number>();
  readonly decimals = input(0);
  readonly duration = input(700);

  protected readonly shown = signal(0);
  protected readonly format = computed(() => `1.${this.decimals()}-${this.decimals()}`);

  private frame: number | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.cancel());

    effect(() => {
      const target = this.value();
      untracked(() => this.animateTo(target));
    });
  }

  private animateTo(target: number): void {
    this.cancel();

    const from = this.shown();
    const reduceMotion =
      typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (reduceMotion || typeof requestAnimationFrame !== 'function' || from === target) {
      this.shown.set(target);
      return;
    }

    const start = performance.now();
    const duration = this.duration();
    const step = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      this.shown.set(from + (target - from) * eased);
      this.frame = progress < 1 ? requestAnimationFrame(step) : null;
    };
    this.frame = requestAnimationFrame(step);
  }

  private cancel(): void {
    if (this.frame !== null && typeof cancelAnimationFrame === 'function') {
      cancelAnimationFrame(this.frame);
    }
    this.frame = null;
  }
}
