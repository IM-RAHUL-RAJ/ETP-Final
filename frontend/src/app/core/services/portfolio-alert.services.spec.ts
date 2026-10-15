import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { PortfolioService } from './portfolio.service';

describe('PortfolioService', () => {
  let http: HttpTestingController;
  const trade = API_ENDPOINTS.tradeApiBase;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the contract routes under the portfolio prefix', () => {
    const portfolio = TestBed.inject(PortfolioService);

    portfolio.getSummary(1).subscribe();
    expect(http.expectOne(`${trade}/api/v1/portfolio/1`).request.method).toBe('GET');

    portfolio.getPositions(1).subscribe();
    http.expectOne(`${trade}/api/v1/portfolio/1/positions`);

    portfolio.getPnl(1).subscribe();
    const bare = http.expectOne(`${trade}/api/v1/portfolio/1/pnl`);
    expect(bare.request.params.keys()).toEqual([]);
  });

  it('passes the date range and the breakdown flag only when given', () => {
    TestBed.inject(PortfolioService).getPnl(1, '2026-09-01', '2026-10-19', true).subscribe();

    const request = http.expectOne((r) => r.url === `${trade}/api/v1/portfolio/1/pnl`);
    expect(request.request.params.get('from')).toBe('2026-09-01');
    expect(request.request.params.get('to')).toBe('2026-10-19');
    expect(request.request.params.get('bySymbol')).toBe('true');
  });

  it('asks for the value history over the chosen range', () => {
    TestBed.inject(PortfolioService).getHistory(1, '3m').subscribe();

    const request = http.expectOne((r) => r.url === `${trade}/api/v1/portfolio/1/history`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('range')).toBe('3m');
  });

  it('never calls a quote provider: every request goes to the platform', () => {
    const portfolio = TestBed.inject(PortfolioService);
    portfolio.getSummary(1).subscribe();
    portfolio.getPositions(1).subscribe();
    portfolio.getHistory(1).subscribe();

    http.match(() => true).forEach((request) => expect(request.request.url.startsWith(trade)).toBe(true));
  });
});
