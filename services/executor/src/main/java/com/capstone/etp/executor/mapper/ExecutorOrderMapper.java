package com.capstone.etp.executor.mapper;

import com.capstone.etp.executor.entity.Account;
import com.capstone.etp.executor.entity.Instrument;
import com.capstone.etp.executor.entity.Order;
import com.capstone.etp.executor.entity.PortfolioHolding;
import com.capstone.etp.executor.enums.OrderStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ExecutorOrderMapper {

   
    @Select("""
            SELECT id, account_id, instrument_id, idempotency_key,
                   order_type AS side, quantity, limit_price, status, created_at, terminal_at
            FROM orders
            WHERE id = #{orderId}
            """)
    @Results({
        @Result(property = "id",             column = "id"),
        @Result(property = "accountId",      column = "account_id"),
        @Result(property = "instrumentId",   column = "instrument_id"),
        @Result(property = "idempotencyKey", column = "idempotency_key"),
        @Result(property = "side",           column = "side"),
        @Result(property = "quantity",       column = "quantity"),
        @Result(property = "limitPrice",     column = "limit_price"),
        @Result(property = "status",         column = "status"),
        @Result(property = "createdAt",      column = "created_at"),
        @Result(property = "terminalAt",     column = "terminal_at")
    })
    Order findOrderById(@Param("orderId") UUID orderId);

    @Select("""
            SELECT id, account_ref, currency, cash_balance, status, version, updated_at
            FROM accounts
            WHERE id = #{accountId}
            """)
    @Results({
        @Result(property = "id",          column = "id"),
        @Result(property = "accountRef",  column = "account_ref"),
        @Result(property = "currency",    column = "currency"),
        @Result(property = "cashBalance", column = "cash_balance"),
        @Result(property = "status",      column = "status"),
        @Result(property = "version",     column = "version"),
        @Result(property = "updatedAt",   column = "updated_at")
    })
    Account findAccountById(@Param("accountId") Long accountId);

    @Select("""
            SELECT id, symbol, currency, is_tradable AS tradable
            FROM instruments
            WHERE id = #{instrumentId}
            """)
    @Results({
        @Result(property = "id",      column = "id"),
        @Result(property = "symbol",  column = "symbol"),
        @Result(property = "currency", column = "currency"),
        @Result(property = "tradable", column = "tradable")
    })
    Instrument findInstrumentById(@Param("instrumentId") Long instrumentId);


    @Select("""
            SELECT ph.portfolio_id, ph.instrument_id, ph.quantity, ph.avg_price
            FROM portfolio_holdings ph
            JOIN portfolios p ON ph.portfolio_id = p.id
            WHERE p.account_id = #{accountId}
              AND ph.instrument_id = #{instrumentId}
            """)
    @Results({
        @Result(property = "portfolioId",   column = "portfolio_id"),
        @Result(property = "instrumentId",  column = "instrument_id"),
        @Result(property = "quantity",      column = "quantity"),
        @Result(property = "avgPrice",      column = "avg_price")
    })
    PortfolioHolding findHolding(@Param("accountId") Long accountId,
                                 @Param("instrumentId") Long instrumentId);


    @Select("SELECT id FROM portfolios WHERE account_id = #{accountId}")
    Long findPortfolioIdByAccountId(@Param("accountId") Long accountId);

                @Select("""
                                                SELECT DISTINCT s.symbol
                                                FROM (
                                                                SELECT i.symbol
                                                                FROM watchlist w
                                                                JOIN instruments i ON i.id = w.instrument_id
                                                                WHERE i.is_tradable = true

                                                                UNION

                                                                SELECT i.symbol
                                                                FROM portfolio_holdings ph
                                                                JOIN instruments i ON i.id = ph.instrument_id
                                                                WHERE i.is_tradable = true
                                                                        AND ph.quantity > 0

                                                                UNION

                                                                SELECT i.symbol
                                                                FROM orders o
                                                                JOIN instruments i ON i.id = o.instrument_id
                                                                WHERE i.is_tradable = true
                                                                        AND o.status = 'NEW'
                                                ) s
                                                ORDER BY s.symbol
                                                """)
                List<String> findSymbolsForMarketDataPoller();


    @Update("""
            UPDATE orders
               SET status      = #{status}::order_status_enum,
                   terminal_at = #{terminalAt}
             WHERE id     = #{orderId}
               AND status = 'NEW'
            """)
    int settleOrderStatus(@Param("orderId")    UUID orderId,
                          @Param("status")     OrderStatus status,
                          @Param("terminalAt") Instant terminalAt);

    
    @Update("""
            UPDATE accounts
               SET cash_balance = #{newCashBalance},
                   version      = version + 1,
                   updated_at   = CURRENT_TIMESTAMP
             WHERE id      = #{accountId}
               AND version = #{expectedVersion}
            """)
    int updateAccountCashAndVersion(@Param("accountId")       Long accountId,
                                    @Param("newCashBalance")  BigDecimal newCashBalance,
                                    @Param("expectedVersion") Long expectedVersion);


    @Insert("""
            INSERT INTO portfolio_holdings
                        (portfolio_id, instrument_id, quantity, avg_price, updated_at)
                 VALUES (#{portfolioId}, #{instrumentId}, #{quantity}, #{avgPrice}, CURRENT_TIMESTAMP)
            ON CONFLICT (portfolio_id, instrument_id)
            DO UPDATE SET
                avg_price   = (portfolio_holdings.avg_price * portfolio_holdings.quantity
                              + EXCLUDED.avg_price * EXCLUDED.quantity)
                            / (portfolio_holdings.quantity + EXCLUDED.quantity),
                quantity    = portfolio_holdings.quantity + EXCLUDED.quantity,
                updated_at  = CURRENT_TIMESTAMP
            """)
    void upsertHolding(@Param("portfolioId")   Long portfolioId,
                       @Param("instrumentId")  Long instrumentId,
                       @Param("quantity")      int quantity,
                       @Param("avgPrice")      BigDecimal avgPrice);


    @Update("""
            UPDATE portfolio_holdings
               SET quantity   = quantity - #{quantity},
                   updated_at = CURRENT_TIMESTAMP
             WHERE portfolio_id = (SELECT id FROM portfolios WHERE account_id = #{accountId})
               AND instrument_id = #{instrumentId}
            """)
    int decreaseHolding(@Param("accountId")   Long accountId,
                        @Param("instrumentId") Long instrumentId,
                        @Param("quantity")     int quantity);

    @Insert("""
            INSERT INTO transactions
                        (order_id, quantity, executed_price, status, reason, executed_at)
                 VALUES (#{orderId}, #{quantity}, #{executedPrice},
                         #{status}::transaction_status_enum, #{reason}, #{executedAt})
            """)
    void insertTransaction(@Param("orderId")        UUID orderId,
                           @Param("quantity")       int quantity,
                           @Param("executedPrice")  BigDecimal executedPrice,
                           @Param("status")         String status,
                           @Param("reason")         String reason,
                           @Param("executedAt")     Instant executedAt);
}
