import { OrderHistoryEntry } from '../models/order.model';
import { activityByDay, allocation, executionQuality } from './portfolio-stats';

function order(overrides: Partial<OrderHistoryEntry>): OrderHistoryEntry {
  return {
    orderId: 'x',
    accountId: 1,
    symbol: 'AAPL',
    side: 'BUY',
    quantity: 1,
    price: 100,
    status: 'FILLED',
    createdOn: '2026-09-30T10:00:00',
    executedPrice: 100,
    idempotencyKey: 'k',
    ...overrides
  };
}

describe('portfolio stats', () => {
  describe('allocation', () => {
    it('splits holdings by cost, biggest first, shares adding to 100', () => {
      const slices = allocation([
        { accountId: 1, symbol: 'AAPL', quantity: 50, averageCost: 170.25 },
        { accountId: 1, symbol: 'MSFT', quantity: 20, averageCost: 310.1 },
        { accountId: 1, symbol: 'INFY.NS', quantity: 3, averageCost: 994.32 }
      ]);

      expect(slices.map((slice) => slice.label)).toEqual(['AAPL', 'MSFT', 'INFY.NS']);
      expect(slices.reduce((sum, slice) => sum + slice.share, 0)).toBeCloseTo(100, 6);
      expect(slices[0].cost).toBeCloseTo(8512.5, 2);
    });

    it('folds everything past the top five into Other', () => {
      const positions = ['A', 'B', 'C', 'D', 'E', 'F', 'G'].map((symbol, index) => ({
        accountId: 1,
        symbol,
        quantity: 1,
        averageCost: 100 - index
      }));

      const slices = allocation(positions);

      expect(slices).toHaveLength(6);
      expect(slices[5].label).toBe('Other');
      // Costs run 100 down to 94; the two smallest, 95 and 94, are folded together.
      expect(slices[5].cost).toBe(95 + 94);
    });

    it('returns nothing for no holdings', () => {
      expect(allocation([])).toEqual([]);
    });
  });

  describe('activityByDay', () => {
    const now = new Date(2026, 8, 30, 15, 0);

    it('keeps every day in the window, oldest first, including empty ones', () => {
      const days = activityByDay([], 14, now);

      expect(days).toHaveLength(14);
      expect(days[13].key).toBe('2026-09-30');
      expect(days[0].key).toBe('2026-09-17');
      expect(days.every((day) => day.total === 0)).toBe(true);
    });

    it('counts orders on their local day, split by status', () => {
      const days = activityByDay(
        [
          order({ createdOn: '2026-09-30T09:00:00', status: 'FILLED' }),
          order({ createdOn: '2026-09-30T11:00:00', status: 'REJECTED' }),
          order({ createdOn: '2026-09-29T11:00:00', status: 'NEW' }),
          order({ createdOn: '2026-09-28T11:00:00', status: 'CANCELLED' }),
          order({ createdOn: '2024-06-01T09:00:00', status: 'FILLED' })
        ],
        14,
        now
      );

      const today = days[13];
      expect(today).toMatchObject({ filled: 1, rejected: 1, total: 2 });
      expect(days[12]).toMatchObject({ working: 1, total: 1 });
      expect(days[11]).toMatchObject({ cancelled: 1, total: 1 });
      // Outside the window: not counted anywhere.
      expect(days.reduce((sum, day) => sum + day.total, 0)).toBe(4);
    });
  });

  describe('executionQuality', () => {
    it('rates a buy filled under its limit as an improvement', () => {
      const rows = executionQuality([order({ side: 'BUY', price: 100, executedPrice: 95 })]);

      expect(rows).toEqual([{ symbol: 'AAPL', orders: 1, improvementPct: 5, saved: 5 }]);
    });

    it('rates a sell filled under its limit as worse', () => {
      const rows = executionQuality([order({ side: 'SELL', price: 100, executedPrice: 98 })]);

      expect(rows[0].improvementPct).toBeCloseTo(-2, 6);
    });

    it('averages per symbol and ignores market orders and unfilled orders', () => {
      const rows = executionQuality([
        order({ price: 100, executedPrice: 96 }),
        order({ price: 100, executedPrice: 100 }),
        order({ price: null, executedPrice: 50 }),
        order({ status: 'NEW', executedPrice: null }),
        order({ status: 'REJECTED', executedPrice: null })
      ]);

      expect(rows).toEqual([{ symbol: 'AAPL', orders: 2, improvementPct: 2, saved: 4 }]);
    });
  });
});
