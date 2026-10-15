import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostListener, OnDestroy, inject, input, output, viewChild } from '@angular/core';
import { AnimationName } from '../../../../core/mascot/pixel-mascot';
import { IconComponent } from '../icon/icon.component';
import { MascotComponent } from '../mascot/mascot.component';

let nextId = 0;

/**
 * A centred dialog over a dimmed, blurred backdrop, with the customer's companion in the corner doing something
 * that fits (waving hello on a form, thinking on a question, cheering on good news). Escape and a click on the
 * backdrop close it; focus moves in on open and Tab stays inside. Put the form in the body and the buttons in an
 * element with class modal__actions.
 */
@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [IconComponent, MascotComponent],
  template: `
    <div class="modal-backdrop" (mousedown)="onBackdrop($event)" data-testid="modal-backdrop">
      <div class="modal" [class.modal--wide]="size() === 'wide'" role="dialog" aria-modal="true" [attr.aria-labelledby]="titleId" #panel>
        <div class="modal__head">
          <app-mascot class="modal__mascot" [action]="mascot()" [size]="72" [interactive]="false" />
          <h2 [id]="titleId">{{ title() }}</h2>
          <button type="button" class="icon-btn" (click)="closed.emit()" aria-label="Close" data-tip="Close" data-testid="modal-close">
            <app-icon name="close" />
          </button>
        </div>
        <ng-content />
      </div>
    </div>
  `,
  styles: [
    `
      .modal__head {
        display: flex;
        align-items: center;
        gap: 14px;
        margin-bottom: 8px;
      }
      .modal__head h2 {
        flex: 1;
      }
      .modal__mascot {
        flex: none;
        margin: -8px 0 -4px -6px;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ModalComponent implements AfterViewInit, OnDestroy {
  readonly title = input.required<string>();
  /** What the companion does while the dialog is open. */
  readonly mascot = input<AnimationName>('wave');
  /** A form with room to breathe asks for the wide one. */
  readonly size = input<'normal' | 'wide'>('normal');
  readonly closed = output<void>();

  protected readonly titleId = `modal-title-${nextId++}`;
  private readonly panel = viewChild.required<ElementRef<HTMLElement>>('panel');
  private readonly previouslyFocused = typeof document === 'undefined' ? null : (document.activeElement as HTMLElement | null);

  ngAfterViewInit(): void {
    const first = this.focusable()[0];
    (first ?? this.panel().nativeElement).focus();
  }

  protected onBackdrop(event: MouseEvent): void {
    // Only a press on the backdrop itself, not one that started inside the dialog.
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }

  @HostListener('document:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.stopPropagation();
      this.closed.emit();
      return;
    }
    if (event.key !== 'Tab') {
      return;
    }
    const items = this.focusable();
    if (items.length === 0) {
      return;
    }
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  /** Hands focus back to what opened the dialog. */
  ngOnDestroy(): void {
    this.previouslyFocused?.focus?.();
  }

  private focusable(): HTMLElement[] {
    return Array.from(
      this.panel().nativeElement.querySelectorAll<HTMLElement>('button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])')
    );
  }
}
