/** SMS exists on the server for older accounts, but the app no longer offers it. */
export type AlertChannel = 'EMAIL' | 'SMS' | 'PUSH';

export type ChannelSource = 'STORED' | 'DEFAULT';

export type MascotChoice = 'OWL' | 'RABBIT' | 'CAT';

/** The kinds of message a customer can switch on or off. */
export type NotificationKindKey = 'ORDER_FILLED' | 'ORDER_REJECTED' | 'ORDER_CANCELLED' | 'PRICE_ALERT' | 'FUNDS';

export const NOTIFICATION_KIND_OPTIONS: ReadonlyArray<{ key: NotificationKindKey; label: string; hint: string }> = [
  { key: 'ORDER_FILLED', label: 'Order placed', hint: 'When an order is filled' },
  { key: 'ORDER_REJECTED', label: 'Order rejected', hint: 'When an order could not be carried out' },
  { key: 'ORDER_CANCELLED', label: 'Order cancelled', hint: 'When an order is cancelled' },
  { key: 'PRICE_ALERT', label: 'Price alert triggered', hint: 'When one of your alerts fires' },
  { key: 'FUNDS', label: 'Funds added or withdrawn', hint: 'When your cash balance changes' }
];

//One channel the customer could pick
export interface ChannelOption {
  channel: AlertChannel;
  available: boolean;
  /** A masked destination ("j***@example.com"), or why the channel is unavailable. */
  hint: string;
}

// The customer's default account, alert channel, companion and notification choices. The contact detail is masked by the server
export interface PreferencesResponse {
  accountId: number;
  defaultAccountId: number;
  alertChannel: AlertChannel;
  contactDetail: string | null;
  source: ChannelSource;
  channels: ChannelOption[];
  updatedAt: string | null;
  mascot: MascotChoice;
  notificationsEnabled: boolean;
  notificationKinds: NotificationKindKey[];
}

/** Mascot, the switch and the kinds are optional: what is left out stays as it was. */
export interface UpdatePreferencesRequest {
  defaultAccountId: number;
  alertChannel: AlertChannel;
  mascot?: MascotChoice;
  notificationsEnabled?: boolean;
  notificationKinds?: NotificationKindKey[];
}
