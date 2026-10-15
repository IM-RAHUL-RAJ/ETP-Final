import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { AlertService } from './alert.service';

describe('AlertService', () => {
  let http: HttpTestingController;
  let service: AlertService;
  const base = `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/1/alerts`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AlertService);
  });

  afterEach(() => http.verify());

  it('lists the account\'s alerts', () => {
    service.list(1).subscribe();
    expect(http.expectOne(base).request.method).toBe('GET');
  });

  it('createAlert sends a POST with symbol, condition and price', () => {
    service.createAlert(1, { symbol: 'AAPL', condition: '>=', price: 235 }).subscribe();

    const request = http.expectOne(base);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ symbol: 'AAPL', condition: '>=', price: 235 });
  });

  it('never sends an account id in the body: it comes from the path and the token', () => {
    service.createAlert(1, { symbol: 'AAPL', condition: '<', price: 1 }).subscribe();

    expect(Object.keys(http.expectOne(base).request.body)).not.toContain('accountId');
  });

  it('updateAlert sends a PUT with only what changed', () => {
    service.updateAlert(1, 7, { price: 240 }).subscribe();

    const request = http.expectOne(`${base}/7`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ price: 240 });
  });

  it('deleteAlert sends a DELETE and answers with the removed alert', () => {
    let removed: unknown;
    service.deleteAlert(1, 7).subscribe((alert) => (removed = alert));

    const request = http.expectOne(`${base}/7`);
    expect(request.request.method).toBe('DELETE');
    request.flush({ id: 7 });
    expect(removed).toEqual({ id: 7 });
  });

  it('toggleAlert sends the enabled field to its own route and answers with the switch', () => {
    let enabled: boolean | undefined;
    service.toggleAlert(1, 7, false).subscribe((value) => (enabled = value));

    const request = http.expectOne(`${base}/7/enabled`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ enabled: false });
    request.flush({ enabled: false });
    expect(enabled).toBe(false);
  });

  it('getTriggerHistory reads the alert\'s triggers', () => {
    let triggers: unknown;
    service.getTriggerHistory(1, 7).subscribe((history) => (triggers = history.triggers));

    const request = http.expectOne(`${base}/7/triggers`);
    expect(request.request.method).toBe('GET');
    request.flush({ alertId: 7, triggers: [{ id: 1, triggerPrice: 231.5, notificationStatus: 'SENT' }] });
    expect(triggers).toEqual([{ id: 1, triggerPrice: 231.5, notificationStatus: 'SENT' }]);
  });

  it('passes a 4xx or 5xx through as an error the caller can show', () => {
    const errors: number[] = [];
    service.createAlert(1, { symbol: 'AAPL', condition: '>', price: 1 }).subscribe({ error: (e: HttpErrorResponse) => errors.push(e.status) });
    http.expectOne(base).flush({ errorCode: 'VAL-422' }, { status: 422, statusText: 'Unprocessable' });

    service.getTriggerHistory(1, 7).subscribe({ error: (e: HttpErrorResponse) => errors.push(e.status) });
    http.expectOne(`${base}/7/triggers`).flush(null, { status: 500, statusText: 'Server Error' });

    service.toggleAlert(1, 7, true).subscribe({ error: (e: HttpErrorResponse) => errors.push(e.status) });
    http.expectOne(`${base}/7/enabled`).flush({ errorCode: 'ALR-409' }, { status: 409, statusText: 'Conflict' });

    expect(errors).toEqual([422, 500, 409]);
  });
});
