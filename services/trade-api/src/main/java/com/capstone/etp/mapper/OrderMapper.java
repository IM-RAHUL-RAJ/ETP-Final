package com.capstone.etp.mapper;

import com.capstone.etp.entity.Account;
import com.capstone.etp.entity.Instrument;
import com.capstone.etp.entity.Order;
import com.capstone.etp.entity.PortfolioHolding;
import com.capstone.etp.enums.OrderStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OrderMapper {


    //Get Account by ID for validation

    @Select("""
        SELECT 
            id AS id, client_id, account_ref, account_type, currency, 
            cash_balance, status, version, created_at, suspended_at, closed_at, updated_at
        FROM accounts
        WHERE id = #{accountId}""")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "clientId", column = "client_id"),
        @Result(property = "accountRef", column = "account_ref"),
        @Result(property = "accountType", column = "account_type"),
        @Result(property = "currency", column = "currency"),
        @Result(property = "cashBalance", column = "cash_balance"),
        @Result(property = "status", column = "status"),
        @Result(property = "version", column = "version"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "suspendedAt", column = "suspended_at"),
        @Result(property = "closedAt", column = "closed_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Account findAccountById(@Param("accountId") Long accountId);

    //Get Instrument by Symbol for validation

    @Select("""
        SELECT 
            id AS id, symbol, name, asset_class, currency, is_tradable, stopped_at
        FROM instruments
        WHERE symbol = #{symbol}""")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "symbol", column = "symbol"),
        @Result(property = "name", column = "name"),
        @Result(property = "assetClass", column = "asset_class"),
        @Result(property = "currency", column = "currency"),
        @Result(property = "tradable", column = "is_tradable"),
        @Result(property = "stoppedAt", column = "stopped_at"),
       
    })
    Instrument findInstrumentBySymbol(@Param("symbol") String symbol);

    //Get Portfolio Holding for account and instrument

    @Select("""
        SELECT 
            ph.id AS id, ph.portfolio_id, ph.instrument_id, ph.quantity, ph.avg_price, 
         ph.updated_at
        FROM portfolio_holdings ph
        JOIN portfolios p ON ph.portfolio_id = p.id
        WHERE p.account_id = #{accountId} AND ph.instrument_id = #{instrumentId}""")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "portfolioId", column = "portfolio_id"),
        @Result(property = "instrumentId", column = "instrument_id"),
        @Result(property = "quantity", column = "quantity"),
        @Result(property = "avgPrice", column = "avg_price"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    PortfolioHolding findPortfolioHolding(
            @Param("accountId") Long accountId,
            @Param("instrumentId") Long instrumentId);

    //Get all existing orders for an account (for idempotency key check)

    @Select("""
        SELECT 
            id AS id, account_id, instrument_id, idempotency_key AS idempotency_key, 
            order_type, quantity, limit_price, status, created_at, terminal_at
        FROM orders
        WHERE account_id = #{accountId}""")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "accountId", column = "account_id"),
        @Result(property = "instrumentId", column = "instrument_id"),
        @Result(property = "idempotencyKey", column = "idempotency_key"),
        @Result(property = "side", column = "order_type"),
        @Result(property = "quantity", column = "quantity"),
        @Result(property = "limitPrice", column = "limit_price"),
        @Result(property = "status", column = "status"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "terminalAt", column = "terminal_at")
    })
    List<Order> findExistingOrdersByAccount(@Param("accountId") Long accountId);

    // ============ Order Query Methods ============

    //Get Order by ID

    @Select("""
        SELECT 
            id AS id, account_id, instrument_id, idempotency_key AS idempotency_key, 
            order_type, quantity, limit_price, status, created_at, terminal_at
        FROM orders
        WHERE id = #{orderId}""")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "accountId", column = "account_id"),
        @Result(property = "instrumentId", column = "instrument_id"),
        @Result(property = "idempotencyKey", column = "idempotency_key"),
        @Result(property = "side", column = "order_type"),
        @Result(property = "quantity", column = "quantity"),
        @Result(property = "limitPrice", column = "limit_price"),
        @Result(property = "status", column = "status"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "terminalAt", column = "terminal_at")
    })
    Order findOrderById(@Param("orderId") UUID orderId);

    //Check if order with idempotency key exists

    @Select("""
        SELECT 
            id AS id, account_id, instrument_id, idempotency_key AS idempotency_key, 
            order_type, quantity, limit_price, status, created_at, terminal_at
        FROM orders
        WHERE idempotency_key = #{idempotencyKey}""")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "accountId", column = "account_id"),
        @Result(property = "instrumentId", column = "instrument_id"),
        @Result(property = "idempotencyKey", column = "idempotency_key"),
        @Result(property = "side", column = "order_type"),
        @Result(property = "quantity", column = "quantity"),
        @Result(property = "limitPrice", column = "limit_price"),
        @Result(property = "status", column = "status"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "terminalAt", column = "terminal_at")
    })
    Order findOrderByIdempotencyKey(@Param("idempotencyKey") UUID idempotencyKey);

    // ============ Order Persistence Methods ============

    //Insert a new order into the orders table with NEW status

    @Insert("""
        INSERT INTO orders 
            (id, account_id, instrument_id, idempotency_key, order_type, quantity, limit_price, status, created_at)
        VALUES 
            (#{order.id}, #{order.accountId}, #{order.instrumentId}, #{order.idempotencyKey}, 
             #{order.side}::order_type_enum, #{order.quantity}, #{order.limitPrice}, #{order.status}:: order_status_enum, #{order.createdAt})""")
    void insertOrder(@Param("order") Order order);

    @Update("""
        UPDATE orders
        SET status = #{newStatus}::order_status_enum, terminal_at = #{terminalAt}
        WHERE id = #{orderId} AND status = #{expectedStatus}::order_status_enum""")
    int updateOrderStatusIfCurrentStatusMatches(
            @Param("orderId") UUID orderId,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("newStatus") OrderStatus newStatus,
            @Param("terminalAt") Instant terminalAt);

    @Update("""
        UPDATE accounts
        SET version = version + 1, updated_at = CURRENT_TIMESTAMP
        WHERE id = #{accountId} AND version = #{version}""")
    int incrementAccountVersionIfVersionMatches(
            @Param("accountId") Long accountId,
            @Param("version") Long version);
}