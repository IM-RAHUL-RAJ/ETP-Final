import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, forwardRef, inject, input, output, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { IconComponent } from '../icon/icon.component';

export interface SelectOption {
  value: string;
  label: string;
  /** Quiet text beside the label (a company name, a currency). */
  hint?: string;
  /** A small tag at the end (an asset class). */
  tag?: string;
}

let nextId = 0;

/**
 * A Zard-style select: a trigger that looks like an input, a popover with a search box, rows with a hint and a tag,
 * a check on the chosen one, and full keyboard use (arrows, Enter, Escape, type to filter). It works with
 * forms (value is the option's value) and on its own ([value] and (valueChange)).
 * With allowCustom, text that matches nothing can still be used, which the order ticket needs for symbols we do not list.
 */
@Component({
  selector: 'app-select',
  standalone: true,
  imports: [IconComponent],
  templateUrl: './select.component.html',
  styleUrl: './select.component.css',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SelectComponent), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SelectComponent implements ControlValueAccessor {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly options = input.required<SelectOption[]>();
  readonly placeholder = input('Select...');
  readonly searchPlaceholder = input('Search...');
  readonly emptyText = input('Nothing matches.');
  readonly allowCustom = input(false);
  readonly invalid = input(false);
  readonly ariaLabel = input('');
  readonly inputId = input('');
  readonly testId = input('select');
  /** For use without a form. */
  readonly value = input('');
  readonly valueChange = output<string>();
  /** Disables it from the template; a form can also disable it. */
  readonly disabledInput = input(false, { alias: 'disabled' });

  protected readonly listId = `select-list-${nextId++}`;
  protected readonly open = signal(false);
  protected readonly query = signal('');
  protected readonly active = signal(0);
  private readonly current = signal<string | null>(null);
  protected readonly disabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.disabledInput());

  private readonly searchBox = viewChild<ElementRef<HTMLInputElement>>('search');
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  protected readonly selected = computed(() => this.current() ?? this.value());
  protected readonly selectedOption = computed(() => this.options().find((option) => option.value === this.selected()) ?? null);
  protected readonly shown = computed<SelectOption[]>(() => {
    const term = this.query().trim().toLowerCase();
    const matches = this.options().filter(
      (option) => term === '' || option.label.toLowerCase().includes(term) || (option.hint ?? '').toLowerCase().includes(term) || option.value.toLowerCase().includes(term)
    );
    const typed = this.query().trim().toUpperCase();
    if (this.allowCustom() && typed !== '' && !this.options().some((option) => option.value.toUpperCase() === typed)) {
      return [...matches, { value: typed, label: typed, hint: 'Use this symbol' }];
    }
    return matches;
  });

  writeValue(value: string | null): void {
    this.current.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected toggle(): void {
    if (this.isDisabled()) {
      return;
    }
    this.open() ? this.close() : this.show();
  }

  /** Where the list floats: fixed to the window, so a dialog or a scrolling card never clips it. */
  protected readonly place = signal<{ left: number; width: number; top: number | null; bottom: number | null; list: number }>({ left: 0, width: 260, top: 0, bottom: null, list: 264 });

  private position(): void {
    const rect = this.trigger().nativeElement.getBoundingClientRect();
    const below = window.innerHeight - rect.bottom - 16;
    const above = rect.top - 16;
    // Open upward only when there is clearly more room above than below.
    const up = below < 260 && above > below;
    const room = Math.max(160, (up ? above : below) - 56);
    const width = Math.max(rect.width, 260);
    this.place.set({
      left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
      width,
      top: up ? null : rect.bottom + 6,
      bottom: up ? window.innerHeight - rect.top + 6 : null,
      list: Math.min(264, room)
    });
  }

  @HostListener('window:resize')
  protected onResize(): void {
    if (this.open()) {
      this.position();
    }
  }

  private show(): void {
    this.position();
    this.query.set('');
    const index = this.shown().findIndex((option) => option.value === this.selected());
    this.active.set(Math.max(index, 0));
    this.open.set(true);
    // The box exists once the panel has rendered.
    setTimeout(() => this.searchBox()?.nativeElement.focus(), 0);
  }

  protected close(returnFocus = true): void {
    this.open.set(false);
    this.onTouched();
    if (returnFocus) {
      this.trigger().nativeElement.focus();
    }
  }

  protected choose(option: SelectOption): void {
    this.current.set(option.value);
    this.onChange(option.value);
    this.valueChange.emit(option.value);
    this.close();
  }

  protected onSearch(value: string): void {
    this.query.set(value);
    this.active.set(0);
  }

  protected onTriggerKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!this.open()) {
        this.show();
      }
    }
  }

  protected onSearchKey(event: KeyboardEvent): void {
    const count = this.shown().length;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.active.update((index) => (count === 0 ? 0 : (index + 1) % count));
        this.scrollActive();
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.active.update((index) => (count === 0 ? 0 : (index - 1 + count) % count));
        this.scrollActive();
        break;
      case 'Enter': {
        event.preventDefault();
        const option = this.shown()[this.active()];
        if (option) {
          this.choose(option);
        }
        break;
      }
      case 'Escape':
        event.preventDefault();
        event.stopPropagation();
        this.close();
        break;
      case 'Tab':
        this.close(false);
        break;
    }
  }

  private scrollActive(): void {
    setTimeout(() => this.host.nativeElement.querySelector('[aria-selected="true"][data-active="true"], [data-active="true"]')?.scrollIntoView?.({ block: 'nearest' }), 0);
  }

  @HostListener('document:mousedown', ['$event'])
  protected onOutside(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close(false);
    }
  }
}
