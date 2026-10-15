package com.capstone.etp.alerts;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;


@Mapper
public interface AlertMapper {

    String COLUMNS = """
            id, account_id, symbol, direction, operator, threshold, enabled, status, delivery_state,
            delivery_attempts, triggered_price, triggered_at, created_at,
            (SELECT COUNT(*) FROM price_alert_triggers t WHERE t.alert_id = price_alerts.id) AS trigger_count""";

    @Select("""
            INSERT INTO price_alerts (account_id, symbol, direction, operator, threshold, enabled)
            SELECT #{accountId}, #{symbol}, #{direction}, #{operator}, #{threshold}, #{enabled}
             WHERE (SELECT COUNT(*) FROM price_alerts WHERE account_id = #{accountId}) < #{cap}
            RETURNING id""")
    Long insertWithinCap(@Param("accountId") Long accountId,
                         @Param("symbol") String symbol,
                         @Param("direction") AlertDirection direction,
                         @Param("operator") AlertOperator operator,
                         @Param("threshold") BigDecimal threshold,
                         @Param("enabled") boolean enabled,
                         @Param("cap") int cap);

    @Select("SELECT " + COLUMNS + " FROM price_alerts WHERE account_id = #{accountId} ORDER BY created_at DESC, id DESC")
    List<PriceAlert> findByAccount(@Param("accountId") Long accountId);

    @Select("SELECT " + COLUMNS + " FROM price_alerts WHERE id = #{id} AND account_id = #{accountId}")
    PriceAlert findByIdAndAccount(@Param("id") Long id, @Param("accountId") Long accountId);

    /** Only alerts that are armed: active and switched on. */
    @Select("SELECT " + COLUMNS + " FROM price_alerts WHERE status = 'ACTIVE' AND enabled")
    List<PriceAlert> findAllActive();

    /**
     * Changes the condition and re-arms the alert. A fired alert is armed again by an edit (its history stays),
     * but not while its message is still being handed over.
     */
    @Update("""
            UPDATE price_alerts
               SET symbol = #{symbol}, direction = #{direction}, operator = #{operator}, threshold = #{threshold},
                   enabled = #{enabled}, status = 'ACTIVE', delivery_state = 'NONE',
                   delivery_attempts = 0, triggered_price = NULL, triggered_at = NULL
             WHERE id = #{id} AND account_id = #{accountId}
               AND status IN ('ACTIVE', 'TRIGGERED')
               AND delivery_state NOT IN ('PENDING_HANDOVER', 'HANDOVER_FAILED')""")
    int updateCondition(@Param("id") Long id,
                        @Param("accountId") Long accountId,
                        @Param("symbol") String symbol,
                        @Param("direction") AlertDirection direction,
                        @Param("operator") AlertOperator operator,
                        @Param("threshold") BigDecimal threshold,
                        @Param("enabled") boolean enabled);

    /** Switches an alert on or off. Switching a fired alert on arms it again; its history stays. */
    @Update("""
            UPDATE price_alerts
               SET enabled = #{enabled},
                   status = CASE WHEN #{enabled} AND status = 'TRIGGERED' THEN 'ACTIVE' ELSE status END,
                   delivery_state = CASE WHEN #{enabled} AND status = 'TRIGGERED' THEN 'NONE' ELSE delivery_state END,
                   delivery_attempts = CASE WHEN #{enabled} AND status = 'TRIGGERED' THEN 0 ELSE delivery_attempts END,
                   triggered_price = CASE WHEN #{enabled} AND status = 'TRIGGERED' THEN NULL ELSE triggered_price END,
                   triggered_at = CASE WHEN #{enabled} AND status = 'TRIGGERED' THEN NULL ELSE triggered_at END
             WHERE id = #{id} AND account_id = #{accountId}
               AND status IN ('ACTIVE', 'TRIGGERED')
               AND delivery_state NOT IN ('PENDING_HANDOVER', 'HANDOVER_FAILED')""")
    int setEnabled(@Param("id") Long id, @Param("accountId") Long accountId, @Param("enabled") boolean enabled);

    /** Removes an alert and, by cascade, its trigger history. Refused while a fired alert is still being handed over. */
    @Delete("""
            DELETE FROM price_alerts
             WHERE id = #{id} AND account_id = #{accountId}
               AND delivery_state NOT IN ('PENDING_HANDOVER', 'HANDOVER_FAILED')""")
    int delete(@Param("id") Long id, @Param("accountId") Long accountId);

    @Update("""
            UPDATE price_alerts
               SET status = 'TRIGGERED', delivery_state = 'PENDING_HANDOVER',
                   triggered_price = #{price}, triggered_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND status = 'ACTIVE' AND enabled""")
    int markTriggered(@Param("id") Long id, @Param("price") BigDecimal price);

    @Update("""
            UPDATE price_alerts
               SET delivery_state = 'HANDED_OVER', delivery_attempts = delivery_attempts + 1
             WHERE id = #{id} AND status = 'TRIGGERED'""")
    int markHandedOver(@Param("id") Long id);

    @Update("""
            UPDATE price_alerts
               SET delivery_state = 'HANDOVER_FAILED', delivery_attempts = delivery_attempts + 1
             WHERE id = #{id} AND status = 'TRIGGERED'""")
    int markHandoverFailed(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM price_alerts " + """
             WHERE status = 'TRIGGERED'
               AND delivery_state IN ('PENDING_HANDOVER', 'HANDOVER_FAILED')
               AND delivery_attempts < #{maxAttempts}
               AND triggered_at < CURRENT_TIMESTAMP - make_interval(secs => 10)
             ORDER BY triggered_at
             LIMIT #{limit}""")
    List<PriceAlert> findPendingHandover(@Param("maxAttempts") int maxAttempts, @Param("limit") int limit);

    // ---- trigger history

    /** Records one firing. The event id is unique, so recording the same firing twice changes nothing. */
    @Insert("""
            INSERT INTO price_alert_triggers (alert_id, event_id, trigger_price)
            VALUES (#{alertId}, #{eventId}, #{price})
            ON CONFLICT (event_id) DO NOTHING""")
    int insertTrigger(@Param("alertId") Long alertId, @Param("eventId") UUID eventId, @Param("price") BigDecimal price);

    @Select("""
            SELECT id FROM price_alert_triggers WHERE alert_id = #{alertId}
             ORDER BY triggered_at DESC, id DESC LIMIT 1""")
    Long findLatestTriggerId(@Param("alertId") Long alertId);

    @Update("UPDATE price_alert_triggers SET notification_id = #{notificationId} WHERE id = #{triggerId}")
    int attachNotification(@Param("triggerId") Long triggerId, @Param("notificationId") Long notificationId);

    @Select("""
            SELECT id, alert_id, event_id, trigger_price, triggered_at, notification_id
              FROM price_alert_triggers WHERE alert_id = #{alertId}
             ORDER BY triggered_at DESC, id DESC LIMIT #{limit}""")
    List<AlertTrigger> findTriggers(@Param("alertId") Long alertId, @Param("limit") int limit);
}
