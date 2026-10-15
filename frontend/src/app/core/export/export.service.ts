import { Injectable } from '@angular/core';

export type ExportFormat = 'csv' | 'xlsx' | 'pdf';
export type ExportCell = string | number | null;

export interface ExportColumn {
  header: string;
  /** Right-align and, in XLSX, store as a number. */
  numeric?: boolean;
}

export interface ExportTable {
  title: string;
  columns: ExportColumn[];
  rows: ExportCell[][];
}

/**
 * A chart drawn natively in the PDF, so it needs no screenshot of the page. Bars by default; kind 'line'
 * draws the values in order (for a value over time).
 */
export interface ExportChart {
  title: string;
  kind?: 'bars' | 'line';
  unit?: string;
  bars: Array<{ label: string; value: number }>;
}

export interface ExportDocument {
  title: string;
  /** Lines under the title: what filter was applied, when it was produced. */
  subtitle: string[];
  tables: ExportTable[];
  /** Included in the PDF only. */
  charts?: ExportChart[];
}

const PALETTE: Array<[number, number, number]> = [
  [37, 99, 235],
  [16, 150, 110],
  [217, 119, 6],
  [124, 58, 237],
  [219, 39, 119],
  [100, 116, 139]
];

/** CSV text for one table. Quotes are doubled, and a leading = + - @ is defused against spreadsheet formula injection. */
export function toCsv(table: ExportTable): string {
  const escape = (cell: ExportCell): string => {
    if (cell === null) {
      return '';
    }
    let text = String(cell);
    if (typeof cell === 'string' && /^[=+\-@\t\r]/.test(text)) {
      text = `'${text}`;
    }
    return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
  };
  return [table.columns.map((column) => escape(column.header)), ...table.rows.map((row) => row.map(escape))]
    .map((line) => line.join(','))
    .join('\r\n');
}

/** A file name safe on every OS: letters, digits, dash. */
export function exportFileName(base: string, extension: string, now: Date = new Date()): string {
  const stamp = now.toISOString().slice(0, 16).replace(/[:T]/g, '-');
  return `${base.replace(/[^A-Za-z0-9-]+/g, '-').toLowerCase()}-${stamp}.${extension}`;
}

/**
 * Turns a document into a downloaded file. The XLSX and PDF libraries are loaded on first use,
 * so they stay out of the main bundle.
 */
@Injectable({ providedIn: 'root' })
export class ExportService {
  async export(format: ExportFormat, document: ExportDocument, fileBase: string): Promise<void> {
    switch (format) {
      case 'csv':
        return this.saveCsv(document, fileBase);
      case 'xlsx':
        return this.saveXlsx(document, fileBase);
      case 'pdf':
        return this.savePdf(document, fileBase);
    }
  }

  private saveCsv(document: ExportDocument, fileBase: string): void {
    // One CSV holds one table; extra tables follow after a blank line with their title.
    const text = document.tables
      .map((table, index) => (index === 0 ? toCsv(table) : `${table.title}\r\n${toCsv(table)}`))
      .join('\r\n\r\n');
    // The BOM makes Excel read the file as UTF-8.
    this.save(new Blob(['﻿', text], { type: 'text/csv;charset=utf-8' }), exportFileName(fileBase, 'csv'));
  }

  private async saveXlsx(document: ExportDocument, fileBase: string): Promise<void> {
    // ExcelJS is a CommonJS package: once bundled, its classes can sit under .default instead of on the module.
    const excel = await import('exceljs');
    const Workbook = excel.Workbook ?? (excel as unknown as { default: typeof excel }).default.Workbook;
    const workbook = new Workbook();
    workbook.created = new Date();
    for (const table of document.tables) {
      const sheet = workbook.addWorksheet(table.title.replace(/[\\/?*[\]:]/g, ' ').slice(0, 31));
      const header = sheet.addRow(table.columns.map((column) => column.header));
      header.font = { bold: true };
      header.eachCell((cell) => {
        cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFE2E8F0' } };
      });
      for (const row of table.rows) {
        sheet.addRow(row.map((cell, index) => (cell !== null && typeof cell === 'string' && table.columns[index]?.numeric ? Number(cell) : cell)));
      }
      table.columns.forEach((column, index) => {
        const sheetColumn = sheet.getColumn(index + 1);
        const longest = Math.max(column.header.length, ...table.rows.map((row) => String(row[index] ?? '').length));
        sheetColumn.width = Math.min(Math.max(longest + 2, 10), 40);
        if (column.numeric) {
          sheetColumn.alignment = { horizontal: 'right' };
          sheetColumn.numFmt = '#,##0.00';
        }
      });
      sheet.views = [{ state: 'frozen', ySplit: 1 }];
    }
    const buffer = await workbook.xlsx.writeBuffer();
    this.save(
      new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
      exportFileName(fileBase, 'xlsx')
    );
  }

  private async savePdf(document: ExportDocument, fileBase: string): Promise<void> {
    const [{ jsPDF }, { default: autoTable }] = await Promise.all([import('jspdf'), import('jspdf-autotable')]);
    const wide = document.tables.some((table) => table.columns.length > 6);
    const pdf = new jsPDF({ orientation: wide ? 'landscape' : 'portrait', unit: 'pt', format: 'a4' });
    const pageWidth = pdf.internal.pageSize.getWidth();
    const margin = 40;
    let y = margin;

    pdf.setFont('helvetica', 'bold').setFontSize(18).text(document.title, margin, y + 8);
    y += 28;
    pdf.setFont('helvetica', 'normal').setFontSize(9).setTextColor(100);
    for (const line of document.subtitle) {
      pdf.text(line, margin, y);
      y += 13;
    }
    pdf.setTextColor(0);
    y += 8;

    for (const chart of document.charts ?? []) {
      y = chart.kind === 'line' ? this.drawLine(pdf, chart, margin, y, pageWidth - margin * 2) : this.drawBars(pdf, chart, margin, y, pageWidth - margin * 2);
    }

    for (const table of document.tables) {
      pdf.setFont('helvetica', 'bold').setFontSize(12).text(table.title, margin, y);
      autoTable(pdf, {
        startY: y + 8,
        margin: { left: margin, right: margin },
        head: [table.columns.map((column) => column.header)],
        body: table.rows.map((row) => row.map((cell) => (cell === null ? '' : String(cell)))),
        styles: { fontSize: 8, cellPadding: 4 },
        headStyles: { fillColor: [30, 41, 59] },
        columnStyles: Object.fromEntries(
          table.columns.map((column, index) => [index, { halign: column.numeric ? 'right' : 'left' }])
        )
      });
      y = (pdf as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 24;
    }

    this.save(pdf.output('blob'), exportFileName(fileBase, 'pdf'));
  }

  /** Horizontal bars with the value at the end. Returns the next free y. */
  private drawBars(pdf: import('jspdf').jsPDF, chart: ExportChart, x: number, top: number, width: number): number {
    const bars = chart.bars.filter((bar) => bar.value > 0);
    const rowHeight = 18;
    const needed = 24 + bars.length * rowHeight + 16;
    let y = top;
    if (y + needed > pdf.internal.pageSize.getHeight() - 40) {
      pdf.addPage();
      y = 40;
    }
    pdf.setFont('helvetica', 'bold').setFontSize(12).text(chart.title, x, y);
    y += 16;
    const total = bars.reduce((sum, bar) => sum + bar.value, 0);
    const max = Math.max(...bars.map((bar) => bar.value), 1);
    const labelWidth = 70;
    const valueWidth = 110;
    const trackWidth = width - labelWidth - valueWidth;
    pdf.setFont('helvetica', 'normal').setFontSize(9);
    bars.forEach((bar, index) => {
      const [r, g, b] = PALETTE[index % PALETTE.length];
      pdf.setTextColor(0).text(bar.label, x, y + 9);
      pdf.setFillColor(r, g, b).rect(x + labelWidth, y, Math.max((bar.value / max) * trackWidth, 1), 11, 'F');
      const share = total > 0 ? ` (${((bar.value / total) * 100).toFixed(1)}%)` : '';
      pdf.text(
        `${bar.value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}${share}`,
        x + labelWidth + trackWidth + 6,
        y + 9
      );
      y += rowHeight;
    });
    return y + 16;
  }

  /** A simple line through the values in order, with the first and last labelled. Returns the next free y. */
  private drawLine(pdf: import('jspdf').jsPDF, chart: ExportChart, x: number, top: number, width: number): number {
    const points = chart.bars;
    const height = 120;
    let y = top;
    if (y + height + 50 > pdf.internal.pageSize.getHeight() - 40) {
      pdf.addPage();
      y = 40;
    }
    pdf.setFont('helvetica', 'bold').setFontSize(12).setTextColor(0).text(chart.title, x, y);
    y += 14;
    if (points.length < 2) {
      pdf.setFont('helvetica', 'normal').setFontSize(9).setTextColor(100).text('Not enough points yet to draw a line.', x, y + 12);
      return y + 36;
    }
    const values = points.map((point) => point.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    pdf.setDrawColor(200).rect(x, y, width, height);
    const [r, g, b] = PALETTE[0];
    pdf.setDrawColor(r, g, b).setLineWidth(1.5);
    points.forEach((point, index) => {
      if (index === 0) {
        return;
      }
      const x1 = x + ((index - 1) / (points.length - 1)) * width;
      const x2 = x + (index / (points.length - 1)) * width;
      const y1 = y + height - ((points[index - 1].value - min) / span) * (height - 8) - 4;
      const y2 = y + height - ((point.value - min) / span) * (height - 8) - 4;
      pdf.line(x1, y1, x2, y2);
    });
    pdf.setLineWidth(0.2).setFont('helvetica', 'normal').setFontSize(8).setTextColor(100);
    pdf.text(points[0].label, x, y + height + 11);
    pdf.text(points[points.length - 1].label, x + width, y + height + 11, { align: 'right' });
    pdf.text(`${min.toFixed(2)} to ${max.toFixed(2)}${chart.unit ? ' ' + chart.unit : ''}`, x + width / 2, y + height + 11, { align: 'center' });
    pdf.setTextColor(0).setDrawColor(0);
    return y + height + 28;
  }

  private save(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
