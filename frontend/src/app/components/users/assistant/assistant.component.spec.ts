import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ChatResponse } from '../../../core/models/assistant.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AssistantService } from '../../../core/services/assistant.service';
import { AssistantComponent } from './assistant.component';

describe('AssistantComponent', () => {
  const chat = vi.fn();
  const quickActions = vi.fn();

  beforeEach(() => sessionStorage.clear());

  const reply = (overrides: Partial<ChatResponse> = {}): ChatResponse => ({
    answer: 'Here you go.', intent: 'GENERAL', advisoryBlocked: false, redirectTo: null, dataCards: [], quickActions: [],
    citations: [], conversationId: 'c1', answeredBy: 'RULES', ...overrides
  });

  async function build(result: unknown = of(reply())) {
    chat.mockReset().mockReturnValue(result);
    quickActions.mockReset().mockReturnValue(of([]));
    await TestBed.configureTestingModule({
      imports: [AssistantComponent],
      providers: [
        provideRouter([]),
        { provide: AssistantService, useValue: { chat, quickActions } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 4 }) } }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(AssistantComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  const el = (f: { nativeElement: HTMLElement }, id: string) => f.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const all = (f: { nativeElement: HTMLElement }, id: string) => Array.from(f.nativeElement.querySelectorAll(`[data-testid="${id}"]`)) as HTMLElement[];
  const type = (f: { nativeElement: HTMLElement; detectChanges(): void }, text: string) => {
    const input = el(f, 'assistant-input') as HTMLTextAreaElement;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    f.detectChanges();
  };
  const mood = (f: { nativeElement: HTMLElement }) => el(f, 'assistant-mascot')?.querySelector('[data-testid="mascot"]')?.getAttribute('data-action');

  it('offers the eight quick questions and a friendly hello, and sends nothing yet', async () => {
    const fixture = await build();

    expect(all(fixture, 'assistant-chips')[0].querySelectorAll('.chip').length).toBe(8);
    expect(el(fixture, 'assistant-hello')?.textContent).toContain('I do not give investment advice');
    expect(chat).not.toHaveBeenCalled();
  });

  it('asks a quick question for the own account, sending the action id, and shows the answer', async () => {
    const fixture = await build(of(reply({ answer: 'You have 3 orders.', intent: 'RECENT_TRANSACTIONS' })));

    el(fixture, 'chip-last_transactions')!.click();
    fixture.detectChanges();

    expect(chat).toHaveBeenCalledWith(4, { message: 'Show my last 10 transactions', conversationId: undefined, selectedQuickAction: 'last_transactions' });
    expect(all(fixture, 'message-user')[0].textContent).toContain('Show my last 10 transactions');
    expect(all(fixture, 'message-bot')[0].textContent).toContain('You have 3 orders.');
  });

  it('sends a typed question with Send and with Enter, keeps the conversation id, and clears the box', async () => {
    const fixture = await build(of(reply({ conversationId: 'conv-9' })));

    type(fixture, 'what do I own?');
    el(fixture, 'assistant-send')!.click();
    fixture.detectChanges();
    expect(chat).toHaveBeenLastCalledWith(4, { message: 'what do I own?', conversationId: undefined, selectedQuickAction: undefined });
    expect((el(fixture, 'assistant-input') as HTMLTextAreaElement).value).toBe('');

    type(fixture, 'and my alerts?');
    el(fixture, 'assistant-input')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();
    expect(chat).toHaveBeenLastCalledWith(4, { message: 'and my alerts?', conversationId: 'conv-9', selectedQuickAction: undefined });
  });

  it('does not send an empty question', async () => {
    const fixture = await build();

    type(fixture, '   ');

    expect((el(fixture, 'assistant-send') as HTMLButtonElement).disabled).toBe(true);
    expect(chat).not.toHaveBeenCalled();
  });

  it('shows the transactions as a table with one row per order', async () => {
    const fixture = await build(of(reply({
      intent: 'RECENT_TRANSACTIONS',
      citations: ['orders'],
      dataCards: [{ kind: 'TABLE', title: 'Last 2 transactions', columns: ['When', 'Symbol', 'Side', 'Qty', 'Price', 'Status'], rows: [
        ['7 Oct, 11:55', 'MSFT', 'SELL', '10', '100.00', 'NEW'], ['7 Oct, 10:00', 'AAPL', 'BUY', '10', '232.71', 'FILLED']] }]
    })));

    el(fixture, 'chip-last_transactions')!.click();
    fixture.detectChanges();

    expect(el(fixture, 'assistant-card')?.textContent).toContain('Last 2 transactions');
    expect(all(fixture, 'assistant-row').length).toBe(2);
    expect(all(fixture, 'assistant-row')[1].textContent).toContain('232.71');
    expect(el(fixture, 'assistant-source')?.textContent).toContain('orders');
  });

  it('shows measures as label and value pairs', async () => {
    const fixture = await build(of(reply({
      dataCards: [{ kind: 'METRICS', title: 'Portfolio performance (USD)', columns: ['Measure', 'Value'], rows: [['Total value', '1100.00 USD'], ['Cash', '100.00 USD']] }]
    })));

    el(fixture, 'chip-portfolio_performance')!.click();
    fixture.detectChanges();

    const card = el(fixture, 'assistant-card')!;
    expect(card.querySelector('dl')?.textContent).toContain('Total value');
    expect(card.querySelector('dl')?.textContent).toContain('1100.00 USD');
    expect(card.querySelector('table')).toBeNull();
  });

  it('says plainly when there is nothing to show instead of an empty table', async () => {
    const fixture = await build(of(reply({ dataCards: [{ kind: 'TABLE', title: 'Stale or unpriced holdings', columns: ['Symbol'], rows: [] }] })));

    el(fixture, 'chip-stale_prices')!.click();
    fixture.detectChanges();

    expect(el(fixture, 'assistant-card')?.textContent).toContain('Nothing to show');
  });

  it('turns an advice refusal into a clear message with a link to the Advice page', async () => {
    const fixture = await build(of(reply({
      answer: 'I cannot provide investment advice. Please use the Advice page for research signals.',
      intent: 'ADVISORY', advisoryBlocked: true, redirectTo: '/advice'
    })));

    type(fixture, 'What should I buy now?');
    el(fixture, 'assistant-send')!.click();
    fixture.detectChanges();

    expect(all(fixture, 'message-bot')[0].textContent).toContain('I cannot provide investment advice');
    const link = el(fixture, 'assistant-redirect-link') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/advice');
    expect(mood(fixture)).toBe('think');
  });

  it('shows a loading indicator while waiting, disables sending, then removes it', async () => {
    const pending = new Subject<ChatResponse>();
    const fixture = await build(pending);

    el(fixture, 'chip-open_orders')!.click();
    fixture.detectChanges();
    expect(el(fixture, 'assistant-loading')).not.toBeNull();
    expect((el(fixture, 'chip-top_movers') as HTMLButtonElement).disabled).toBe(true);
    expect(mood(fixture)).toBe('think');

    pending.next(reply());
    pending.complete();
    fixture.detectChanges();
    expect(el(fixture, 'assistant-loading')).toBeNull();
    expect(mood(fixture)).toBe('happy');
  });

  it('shows an error line with a retry that asks the same thing again', async () => {
    const fixture = await build(throwError(() => ({ status: 503 })));

    el(fixture, 'chip-top_movers')!.click();
    fixture.detectChanges();
    expect(all(fixture, 'message-bot')[0].classList.contains('bubble--error')).toBe(true);
    expect(mood(fixture)).toBe('sad');

    chat.mockReturnValue(of(reply({ answer: 'Back again.' })));
    el(fixture, 'assistant-retry')!.click();
    fixture.detectChanges();

    expect(chat).toHaveBeenLastCalledWith(4, { message: 'Show my top 5 gainers and losers in current holdings', conversationId: undefined, selectedQuickAction: 'top_movers' });
    expect(all(fixture, 'message-user').length).toBe(1);
    expect(all(fixture, 'message-bot')[0].textContent).toContain('Back again.');
    expect(el(fixture, 'assistant-retry')).toBeNull();
  });

  it('keeps the conversation when the page is left and opened again, and after a refresh', async () => {
    const first = await build(of(reply({ answer: 'You have 3 orders.', conversationId: 'conv-1' })));
    el(first, 'chip-last_transactions')!.click();
    first.detectChanges();
    first.destroy();

    // Back on the page: a new component, the same conversation, and it carries on with the same conversation id.
    const second = TestBed.createComponent(AssistantComponent);
    second.detectChanges();
    expect(all(second, 'message-user')[0].textContent).toContain('Show my last 10 transactions');
    expect(all(second, 'message-bot')[0].textContent).toContain('You have 3 orders.');
    expect(el(second, 'assistant-hello')).toBeNull();
    type(second, 'and my alerts?');
    el(second, 'assistant-send')!.click();
    expect(chat).toHaveBeenLastCalledWith(4, { message: 'and my alerts?', conversationId: 'conv-1', selectedQuickAction: undefined });

    // A refresh: the store is rebuilt from the tab's session storage.
    expect(JSON.parse(sessionStorage.getItem('quorum-assistant-4')!).messages.length).toBeGreaterThan(2);
  });

  it('starts a new chat', async () => {
    const fixture = await build();
    el(fixture, 'chip-open_orders')!.click();
    fixture.detectChanges();

    el(fixture, 'assistant-clear')!.click();
    fixture.detectChanges();

    expect(all(fixture, 'message-user').length).toBe(0);
    expect(el(fixture, 'assistant-hello')).not.toBeNull();
  });

  it('uses the server list of quick questions when it arrives', async () => {
    quickActions.mockReset();
    chat.mockReset();
    quickActions.mockReturnValue(of([{ id: 'only_one', label: 'Only one question' }]));
    await TestBed.configureTestingModule({
      imports: [AssistantComponent],
      providers: [
        provideRouter([]),
        { provide: AssistantService, useValue: { chat, quickActions } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 4 }) } }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(AssistantComponent);
    fixture.detectChanges();

    expect(all(fixture, 'chip-only_one').length).toBe(1);
  });
});
