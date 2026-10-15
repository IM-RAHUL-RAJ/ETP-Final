/** How the price is compared with the level, as the symbol the API speaks. */
export type AlertCondition = '>' | '>=' | '<' | '<=' | '==';

export const ALERT_CONDITIONS: ReadonlyArray<{ value: AlertCondition; label: string; short: string }> = [
  { value: '>', label: 'Greater than (>)', short: 'greater than' },
  { value: '>=', label: 'Greater or equal (>=)', short: 'at or above' },
  { value: '<', label: 'Less than (<)', short: 'less than' },
  { value: '<=', label: 'Less or equal (<=)', short: 'at or below' },
  { value: '==', label: 'Equals (==)', short: 'equal to' }
];

/** "≥ 230.50" for the list. The unicode forms read better than the ASCII the API uses. */
export function conditionText(condition: AlertCondition, price: number): string {
  const symbol = { '>': '>', '>=': '≥', '<': '<', '<=': '≤', '==': '=' }[condition];
  return `${symbol} ${price.toFixed(2)}`;
}

export type AlertStatus = 'ACTIVE' | 'TRIGGERED' | 'CANCELLED';

export type DeliveryState = 'NONE' | 'PENDING_HANDOVER' | 'HANDED_OVER' | 'HANDOVER_FAILED';

export interface PriceAlert {
  id: number;
  symbol: string;
  condition: AlertCondition;
  price: number;
  /** A switched-off alert is kept but never fires. */
  enabled: boolean;
  status: AlertStatus;
  deliveryState: DeliveryState;
  triggeredPrice: number | null;
  triggeredAt: string | null;
  createdAt: string;
  /** How many times it has fired. */
  triggerCount: number;
}

export interface CreateAlertRequest {
  symbol: string;
  condition: AlertCondition;
  price: number;
  enabled?: boolean;
}

/** Any field may be left out: only what is present changes. */
export type UpdateAlertRequest = Partial<CreateAlertRequest>;

export type NotificationDeliveryStatus = 'SENT' | 'QUEUED' | 'HELD' | 'FAILED' | 'SKIPPED' | 'UNKNOWN';

export interface AlertTrigger {
  id: number;
  triggeredAt: string;
  triggerPrice: number;
  notificationStatus: NotificationDeliveryStatus;
  notificationId: number | null;
}

export interface AlertTriggersResponse {
  alertId: number;
  triggers: AlertTrigger[];
}

// The server's cap per account; shown as a hint, enforced by the server.
export const MAX_ALERTS_PER_ACCOUNT = 25;
