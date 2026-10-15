import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { DownloadMenuComponent, DownloadRequest } from './download-menu.component';

describe('DownloadMenuComponent', () => {
  let fixture: ComponentFixture<DownloadMenuComponent>;
  let requests: DownloadRequest[];

  const el = (id: string) => fixture.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;

  beforeEach(() => {
    fixture = TestBed.createComponent(DownloadMenuComponent);
    fixture.componentRef.setInput('filteredCount', 3);
    fixture.componentRef.setInput('totalCount', 10);
    fixture.componentRef.setInput('hasCharts', true);
    requests = [];
    fixture.componentInstance.download.subscribe((request) => requests.push(request));
    fixture.detectChanges();
  });

  it('offers the filtered rows by default', () => {
    el('download-toggle')!.click();
    fixture.detectChanges();
    el('download-pdf')!.click();
    expect(requests).toEqual([{ format: 'pdf', scope: 'filtered', includeCharts: true }]);
  });

  it('exports everything when asked', () => {
    el('download-toggle')!.click();
    fixture.detectChanges();
    el('scope-all')!.click();
    fixture.detectChanges();
    el('download-xlsx')!.click();
    expect(requests).toEqual([{ format: 'xlsx', scope: 'all', includeCharts: false }]);
  });

  it('hides the scope choice when the filter shows everything', () => {
    fixture.componentRef.setInput('filteredCount', 10);
    fixture.detectChanges();
    el('download-toggle')!.click();
    fixture.detectChanges();
    expect(el('scope-all')).toBeNull();
    el('download-csv')!.click();
    expect(requests[0].scope).toBe('all');
  });
});
