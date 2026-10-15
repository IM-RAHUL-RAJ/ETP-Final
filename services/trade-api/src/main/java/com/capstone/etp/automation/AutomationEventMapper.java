package com.capstone.etp.automation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The history of automated trades. */
@Mapper
public interface AutomationEventMapper {

    String COLUMNS = "id, strategy_id, account_id, strategy_type, symbol, trigger_side, outcome, idempotency_key, order_id, "
            + "limit_price, quantity, detail, created_at, updated_at";

    /** Recorded when the rule fires, before the order is sent. */
    @Select("""
            INSERT INTO automation_events (strategy_id, account_id, strategy_type, symbol, trigger_side, outcome,
                                           idempotency_key, limit_price, quantity, detail)
            VALUES (#{strategyId}, #{accountId}, #{strategyType}, #{symbol}, #{triggerSide}, 'STRATEGY_TRIGGERED',
                    #{idempotencyKey}, #{limitPrice}, #{quantity}, #{detail})
            ON CONFLICT (idempotency_key) DO NOTHING
            RETURNING id""")
    Long insertTrigger(@Param("strategyId") Long strategyId,
                       @Param("accountId") Long accountId,
                       @Param("strategyType") String strategyType,
                       @Param("symbol") String symbol,
                       @Param("triggerSide") String triggerSide,
                       @Param("idempotencyKey") UUID idempotencyKey,
                       @Param("limitPrice") BigDecimal limitPrice,
                       @Param("quantity") Integer quantity,
                       @Param("detail") String detail);

    @Update("""
            UPDATE automation_events
               SET outcome = #{outcome}, order_id = #{orderId}, detail = COALESCE(#{detail}, detail), updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id}""")
    int recordOutcome(@Param("id") Long id, @Param("outcome") String outcome, @Param("orderId") UUID orderId,
                      @Param("detail") String detail);

    @Select("SELECT " + COLUMNS + " FROM automation_events WHERE account_id = #{accountId} ORDER BY created_at DESC, id DESC LIMIT #{limit}")
    List<AutomationEvent> findByAccount(@Param("accountId") Long accountId, @Param("limit") int limit);

    /** The trigger that placed this order, if an automated strategy placed it. Matched by the order's idempotency key. */
    @Select("""
            SELECT e.id, e.strategy_id, e.account_id, e.strategy_type, e.symbol, e.trigger_side, e.outcome, e.idempotency_key,
                   e.order_id, e.limit_price, e.quantity, e.detail, e.created_at, e.updated_at
              FROM automation_events e
              JOIN orders o ON o.idempotency_key = e.idempotency_key
             WHERE o.id = #{orderId}
             LIMIT 1""")
    AutomationEvent findForOrder(@Param("orderId") UUID orderId);
}
