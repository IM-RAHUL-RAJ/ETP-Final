import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, input, output, signal } from '@angular/core';
import { ExportFormat } from '../../../../core/export/export.service';

export type ExportScope = 'filtered' | 'all';

export interface DownloadRequest {
  format: ExportFormat;
  scope: ExportScope;
  /** PDF only: draw the charts at the top of the file. */
  includeCharts: boolean;
}

/** A download button with a small menu: what to include, and which file type. */
@Component({
  selector: 'app-download-menu',
  standalone: true,
  templateUrl: './download-menu.component.html',
  styleUrl: './download-menu.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DownloadMenuComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Rows the current filter shows, and rows there are. Both shown so the choice is clear. */
  readonly filteredCount = input.required<number>();
  readonly totalCount = input.required<number>();
  readonly hasCharts = input(false);
  readonly busy = input(false);
  readonly download = output<DownloadRequest>();

  protected readonly open = signal(false);
  protected readonly scope = signal<ExportScope>('filtered');
  protected readonly includeCharts = signal(true);
  protected readonly filterIsNarrower = computed(() => this.filteredCount() !== this.totalCount());
  protected readonly formats: ReadonlyArray<{ id: ExportFormat; label: string; hint: string }> = [
    { id: 'csv', label: 'CSV', hint: 'Plain table' },
    { id: 'xlsx', label: 'Excel', hint: '.xlsx workbook' },
    { id: 'pdf', label: 'PDF', hint: 'Report with charts' }
  ];

  protected toggle(): void {
    this.open.update((value) => !value);
  }

  protected pick(format: ExportFormat): void {
    this.download.emit({
      format,
      scope: this.filterIsNarrower() ? this.scope() : 'all',
      includeCharts: format === 'pdf' && this.hasCharts() && this.includeCharts()
    });
    this.open.set(false);
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('keydown.escape')
  protected onEscape(): void {
    this.open.set(false);
  }
}
