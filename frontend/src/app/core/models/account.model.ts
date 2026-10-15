import { OrderStatus } from './order.model';

export interface AccountResponse {
  id: number;
  accountId: string;
  holderName: string;
  cashBalance: number;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  version: number;
  lastUpdated: string;
}

export interface BalanceResponse {
  accountId: number;
  cashBalance: number;
  currency: string;
  asOf: string;
}

export interface PositionResponse {
  accountId: number;
  symbol: string;
  quantity: number;
  averageCost: number;
}

export interface DashboardStats {
  availableCash: number;
  positions: number;
  workingOrders: number;
  accountStatus: string;
}

export interface ServiceStatus {
  name: string;
  status: 'ONLINE' | 'UNAVAILABLE' | 'NOT_CONFIGURED';
  detail: string;
}

export interface MarketInstrumentRow {
  symbol: string;
  assetClass: string;
  currency: string;
  price: number | null;
  status: OrderStatus | 'ACTIVE' | 'UNKNOWN';
}

