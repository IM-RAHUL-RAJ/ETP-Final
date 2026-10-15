export interface CurrencyConversionRequest {
  fromCurrency: string;
  toCurrency: string;
  amount: number;
}

export interface CurrencyConversionResponse {
  fromCurrency: string;
  toCurrency: string;
  amount: number;
  convertedAmount: number;
  rate: number;
}


export interface InstrumentInfo {
  symbol: string;
  name: string;
  exchange: string;
  assetClass: string;
  currency: string;
  tradable: boolean;
}

export interface WatchlistEntry extends InstrumentInfo {
  addedAt: string;
}

/** The latest quote the executor published to the market-data Kafka topic, relayed by the trade API. */
export interface MarketQuote {
  symbol: string;
  price: number | null;
  bid: number | null;
  ask: number | null;
  currency: string | null;
  change: number | null;
  changePercent: number | null;
  previousClose: number | null;
  marketState: string | null;
  stale: boolean | null;
  quoteAsOf: string | null;
  receivedAt: string | null;
}
