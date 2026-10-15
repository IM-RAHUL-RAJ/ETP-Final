import { describe, expect, it } from 'vitest';
import { ExportDocument, ExportService, exportFileName, toCsv } from './export.service';

describe('toCsv', () => {
  const columns = [{ header: 'Symbol' }, { header: 'Qty', numeric: true }, { header: 'Note' }];

  it('writes a header, then one line per row, with blanks for null', () => {
    expect(toCsv({ title: 't', columns, rows: [['AAPL', 10, null]] })).toBe('Symbol,Qty,Note\r\nAAPL,10,');
  });

  it('quotes commas, quotes and line breaks', () => {
    const csv = toCsv({ title: 't', columns, rows: [['A,B', 1, 'say "hi"\nnow']] });
    expect(csv).toContain('"A,B",1,"say ""hi""\nnow"');
  });

  it('defuses spreadsheet formulas but leaves negative numbers alone', () => {
    const csv = toCsv({ title: 't', columns, rows: [['=SUM(A1)', -5, '+1']] });
    expect(csv).toContain("'=SUM(A1),-5,'+1");
  });
});

describe('exportFileName', () => {
  it('is lower case, dated and safe', () => {
    expect(exportFileName('Order blotter', 'csv', new Date('2026-10-07T14:05:00Z'))).toBe('order-blotter-2026-10-07-14-05.csv');
  });
});

describe('ExportService files', () => {
  const document: ExportDocument = {
    title: 'Test',
    subtitle: ['Generated now', 'Filters: none'],
    tables: [
      { title: 'Holdings', columns: [{ header: 'Symbol' }, { header: 'Qty', numeric: true }], rows: [['AAPL', 10], ['=EVIL()', null]] }
    ],
    charts: [
      { title: 'Allocation', bars: [{ label: 'AAPL', value: 10 }, { label: 'MSFT', value: 5 }] },
      { title: 'Value', kind: 'line', bars: [{ label: 'a', value: 1 }, { label: 'b', value: 3 }, { label: 'c', value: 2 }] }
    ]
  };

  let blobs: Blob[];
  let names: string[];

  beforeEach(() => {
    blobs = [];
    names = [];
    URL.createObjectURL = ((blob: Blob) => {
      blobs.push(blob);
      return 'blob:test';
    }) as typeof URL.createObjectURL;
    URL.revokeObjectURL = (() => undefined) as typeof URL.revokeObjectURL;
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      names.push(this.download);
    });
  });

  afterEach(() => vi.restoreAllMocks());

  const head = async (blob: Blob, bytes: number) => new TextDecoder('latin1').decode(new Uint8Array(await blob.arrayBuffer()).subarray(0, bytes));
  const whole = async (blob: Blob) => new TextDecoder().decode(await blob.arrayBuffer());

  it('writes a CSV with a byte-order mark, and defuses a formula', async () => {
    await new ExportService().export('csv', document, 'portfolio');

    expect(names[0]).toMatch(/^portfolio-[\d-]+\.csv$/);
    expect(await whole(blobs[0])).toContain("'=EVIL()");
    // EF BB BF: the UTF-8 byte-order mark that makes Excel read the file as UTF-8.
    expect(Array.from(new Uint8Array(await blobs[0].arrayBuffer()).subarray(0, 3))).toEqual([0xef, 0xbb, 0xbf]);
  });

  it('writes a real Excel workbook (a zip file) that is bundled-code safe', async () => {
    await new ExportService().export('xlsx', document, 'portfolio');

    expect(names[0]).toMatch(/\.xlsx$/);
    expect(await head(blobs[0], 2)).toBe('PK');
    // The first call loads the whole library; give it room.
  }, 60_000);

  it('writes a real PDF, with bar and line charts', async () => {
    await new ExportService().export('pdf', document, 'portfolio');

    expect(names[0]).toMatch(/\.pdf$/);
    expect(await head(blobs[0], 5)).toBe('%PDF-');
    expect(blobs[0].size).toBeGreaterThan(1500);
  });
});
