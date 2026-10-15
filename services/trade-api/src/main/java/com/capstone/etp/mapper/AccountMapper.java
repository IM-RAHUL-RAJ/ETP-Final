package com.capstone.etp.mapper;

import com.capstone.etp.dto.AccountResponse;
import com.capstone.etp.dto.BalanceResponse;
import com.capstone.etp.dto.InstrumentResponse;
import com.capstone.etp.dto.WatchlistEntry;
import com.capstone.etp.dto.OrderHistoryEntry;
import com.capstone.etp.dto.PositionResponse;
import com.capstone.etp.enums.OrderStatus;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.time.Instant;
import java.util.List;

@Mapper
public interface AccountMapper {

    
    //Get account information from its account ID
    
    @Select("""
        SELECT 
           a.id AS id,
           a.account_ref AS account_id,
           c.name AS holder_name,
           a.cash_balance AS cash_balance,
           a.status AS status,
           a.version AS version,
           a.updated_at AS last_updated
        FROM accounts a
        JOIN clients c ON a.client_id = c.id
        WHERE a.id = #{accountId}""")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "accountId", column = "account_id"),
        @Result(property = "holderName", column = "holder_name"),
        @Result(property = "cashBalance", column = "cash_balance"),
        @Result(property = "status", column = "status"),
        @Result(property = "version", column = "version"),
        @Result(property = "lastUpdated", column = "last_updated")
    })
    AccountResponse getAccountById(@Param("accountId") Long accountId);

    
    //Get account balance by account ID
    
    @Select("""
        SELECT
           id AS account_id,
           cash_balance,
           currency,
           updated_at AS as_of
        FROM accounts
        WHERE id = #{accountId}""")
    @Results({
        @Result(property = "accountId", column = "account_id"),
        @Result(property = "cashBalance", column = "cash_balance"),
        @Result(property = "currency", column = "currency"),
        @Result(property = "asOf", column = "as_of")
    })
    BalanceResponse getBalance(@Param("accountId") Long accountId);


    //Get all positions for an account by account ID
    
    @Select("""
        SELECT
           p.account_id AS account_id,
           i.symbol AS symbol,
           ph.quantity AS quantity,
           ph.avg_price AS average_cost
        FROM portfolio_holdings ph
        JOIN portfolios p ON ph.portfolio_id = p.id
        JOIN instruments i ON ph.instrument_id = i.id
        WHERE p.account_id = #{accountId}""")
    @Results({
        @Result(property = "accountId", column = "account_id"),
        @Result(property = "symbol", column = "symbol"),
        @Result(property = "quantity", column = "quantity"),
        @Result(property = "averageCost", column = "average_cost")
    })
    List<PositionResponse> getPositionsByAccountId(@Param("accountId") Long accountId);

    //Retrieve order history for a given account with optional filtering by status and date range
    @Select("""
        <script>
        SELECT
            o.id AS order_id,
            o.account_id AS account_id,
            i.symbol AS symbol,
            o.order_type AS side,
            o.quantity AS quantity,
            o.limit_price AS price,
            o.status AS status,
            o.created_at AS created_on,
            t.executed_price AS executed_price,
            o.idempotency_key AS idempotency_key

        FROM orders o

        JOIN instruments i
            ON o.instrument_id = i.id

        LEFT JOIN transactions t
            ON o.id = t.order_id

        WHERE o.account_id = #{accountId}

        <if test="status != null">
            AND o.status = #{status}::order_status_enum
        </if>

        <if test="from != null">
            AND o.created_at &gt;= #{from}
        </if>

        <if test="to != null">
            AND o.created_at &lt;= #{to}
        </if>

        ORDER BY o.created_at DESC

        </script>
        """)
        @Results({
            @Result(property = "orderId", column = "order_id"),
            @Result(property = "accountId", column = "account_id"),
            @Result(property = "symbol", column = "symbol"),
            @Result(property = "side", column = "side"),
            @Result(property = "quantity", column = "quantity"),
            @Result(property = "price", column = "price"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdOn", column = "created_on"),
            @Result(property = "executedPrice", column = "executed_price"),
            @Result(property = "idempotencyKey", column = "idempotency_key")
        })
        List<OrderHistoryEntry> getOrdersByAccountId(
                @Param("accountId") Long accountId,
                @Param("status") OrderStatus status,
                @Param("from") Instant from,
                @Param("to") Instant to
        );

    // Every instrument the platform knows, so the UI can show each one's currency and offer a picker.
    @Select("""
        SELECT
           symbol,
           name,
           exchange,
           asset_class::text AS asset_class,
           TRIM(currency) AS currency,
           is_tradable AS tradable
        FROM instruments
        ORDER BY symbol""")
    @Results({
        @Result(property = "symbol", column = "symbol"),
        @Result(property = "name", column = "name"),
        @Result(property = "exchange", column = "exchange"),
        @Result(property = "assetClass", column = "asset_class"),
        @Result(property = "currency", column = "currency"),
        @Result(property = "tradable", column = "tradable")
    })
    List<InstrumentResponse> getInstruments();

    // The account's watchlist, newest addition first.
    @Select("""
        SELECT
           i.symbol AS symbol,
           i.name AS name,
           i.exchange AS exchange,
           i.asset_class::text AS asset_class,
           TRIM(i.currency) AS currency,
           i.is_tradable AS tradable,
           w.added_at AS added_at
        FROM watchlist w
        JOIN instruments i ON i.id = w.instrument_id
        WHERE w.account_id = #{accountId}
        ORDER BY w.added_at DESC, i.symbol""")
    @Results({
        @Result(property = "symbol", column = "symbol"),
        @Result(property = "name", column = "name"),
        @Result(property = "exchange", column = "exchange"),
        @Result(property = "assetClass", column = "asset_class"),
        @Result(property = "currency", column = "currency"),
        @Result(property = "tradable", column = "tradable"),
        @Result(property = "addedAt", column = "added_at")
    })
    List<WatchlistEntry> getWatchlist(@Param("accountId") Long accountId);

    // Adds by symbol. Adding one that is already there changes nothing. Returns rows inserted.
    @Insert("""
        INSERT INTO watchlist (account_id, instrument_id)
        SELECT #{accountId}, i.id
        FROM instruments i
        WHERE i.symbol = #{symbol}
        ON CONFLICT (account_id, instrument_id) DO NOTHING""")
    int addToWatchlist(@Param("accountId") Long accountId, @Param("symbol") String symbol);

    @Delete("""
        DELETE FROM watchlist
        WHERE account_id = #{accountId}
          AND instrument_id = (SELECT id FROM instruments WHERE symbol = #{symbol})""")
    int removeFromWatchlist(@Param("accountId") Long accountId, @Param("symbol") String symbol);

    @Select("SELECT COUNT(*) FROM instruments WHERE symbol = #{symbol}")
    int countInstrumentsBySymbol(@Param("symbol") String symbol);

    // One instrument's own currency, or null if the symbol is unknown. Read-only; used by the portfolio module.
    @Select("SELECT TRIM(currency) FROM instruments WHERE symbol = #{symbol}")
    String getInstrumentCurrency(@Param("symbol") String symbol);
}
