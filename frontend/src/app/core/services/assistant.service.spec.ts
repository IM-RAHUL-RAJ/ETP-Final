import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { AssistantService } from './assistant.service';

describe('AssistantService', () => {
  let service: AssistantService;
  let http: HttpTestingController;
  const base = (id: number) => `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${id}/assistant`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AssistantService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the question to the trade API for the given account, never to the model', () => {
    service.chat(4, { message: 'hi', selectedQuickAction: 'pnl_summary' }).subscribe();

    const request = http.expectOne(`${base(4)}/chat`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'hi', selectedQuickAction: 'pnl_summary' });
    expect(request.request.url).not.toContain('googleapis');
    expect(request.request.headers.has('x-goog-api-key')).toBe(false);
    request.flush({});
  });

  it('reads the quick actions for the account', () => {
    let actions: unknown;
    service.quickActions(4).subscribe((value) => (actions = value));

    http.expectOne(`${base(4)}/quick-actions`).flush([{ id: 'a', label: 'A' }]);

    expect(actions).toEqual([{ id: 'a', label: 'A' }]);
  });
});
