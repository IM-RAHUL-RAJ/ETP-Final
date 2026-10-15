import { AlertChannel, ChannelSource } from './preferences.model';

export type NotificationKind = 'ORDER_FILLED' | 'ORDER_REJECTED' | 'ORDER_CANCELLED' | 'PRICE_ALERT' | 'FUNDS';

/** The groups the inbox can be filtered by; the value is what the API takes as ?type=. */
export type NotificationType = 'all' | 'price_alert' | 'order' | 'funds';

/** QUEUED waiting to be sent; HELD no usable contact; SENT handed to the channel; FAILED gave up; SKIPPED the customer switched it off. */
export type NotificationStatus = 'QUEUED' | 'HELD' | 'SENT' | 'FAILED' | 'SKIPPED';

export interface NotificationItem {
  id: number;
  kind: NotificationKind;
  subject: string;
  body: string;
  status: NotificationStatus;
  /** NO_CONTACT, PREFERENCES_UNAVAILABLE, PROVIDER_ERROR, STUB_DELIVERY or OPTED_OUT. */
  statusReason: string | null;
  /** The channel the platform resolved when it sent (or tried to send) this message. Null while it is waiting. */
  channel: AlertChannel | null;
  channelSource: ChannelSource | null;
  createdAt: string;
  sentAt: string | null;
  read: boolean;
}

/** How many messages each tab holds. */
export interface NotificationCounts {
  all: number;
  priceAlert: number;
  order: number;
  funds: number;
}

/** Which group a kind belongs to, for the badge on each card. */
export function typeOf(kind: NotificationKind): Exclude<NotificationType, 'all'> {
  if (kind === 'PRICE_ALERT') {
    return 'price_alert';
  }
  return kind === 'FUNDS' ? 'funds' : 'order';
}
