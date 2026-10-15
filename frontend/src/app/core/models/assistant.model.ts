export type AssistantIntent =
  | 'PORTFOLIO_PERFORMANCE' | 'TOP_MOVERS' | 'RECENT_TRANSACTIONS' | 'BUY_SELL_COUNT' | 'OPEN_ORDERS' | 'STALE_PRICES'
  | 'PNL_SUMMARY' | 'ACTIVITY_TREND' | 'POSITIONS' | 'ALERTS' | 'GENERAL' | 'GREETING' | 'ADVISORY' | 'BLOCKED' | 'OFF_TOPIC';

export interface QuickAction {
  id: string;
  label: string;
}

/** Figures shown under an answer. They come from the account's data, never from the model. */
export interface DataCard {
  kind: 'TABLE' | 'METRICS';
  title: string;
  columns: string[];
  rows: string[][];
}

export interface ChatRequest {
  message: string;
  conversationId?: string;
  selectedQuickAction?: string;
}

export interface ChatResponse {
  answer: string;
  intent: AssistantIntent;
  advisoryBlocked: boolean;
  redirectTo: string | null;
  dataCards: DataCard[];
  quickActions: QuickAction[];
  citations: string[];
  conversationId: string;
  answeredBy: 'GEMINI' | 'RULES';
}

/** One line of the conversation. A bot line has the full response (cards, redirect) or an error. */
export interface ChatMessage {
  id: number;
  role: 'user' | 'bot';
  text: string;
  response?: ChatResponse;
  error?: boolean;
  /** What to send again when an error line's Retry is pressed. */
  retry?: { message: string; quickAction?: string };
}
