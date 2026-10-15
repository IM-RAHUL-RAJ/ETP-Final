package com.capstone.etp.portfolio;

import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The portfolio value history. It reads cash_movements only to take deposits and withdrawals out of a day's change. */
@Mapper
public interface PortfolioSnapshotMapper {

    @Data
    class Snapshot {
        private Instant takenAt;
        private BigDecimal totalValue;
    }

    /** Records a point unless one was taken within the last ten minutes. Returns 1 when a row was written. */
    @Insert("""
            INSERT INTO portfolio_value_snapshots (account_id, total_value, base_currency)
            SELECT #{accountId}, #{totalValue}, #{baseCurrency}
             WHERE NOT EXISTS (SELECT 1 FROM portfolio_value_snapshots
                                WHERE account_id = #{accountId}
                                  AND taken_at > CURRENT_TIMESTAMP - interval '10 minutes')""")
    int recordIfDue(@Param("accountId") Long accountId,
                    @Param("totalValue") BigDecimal totalValue,
                    @Param("baseCurrency") String baseCurrency);

    @Select("""
            SELECT taken_at, total_value FROM portfolio_value_snapshots
             WHERE account_id = #{accountId} AND taken_at >= #{since}
             ORDER BY taken_at""")
    List<Snapshot> findSince(@Param("accountId") Long accountId, @Param("since") Instant since);

    /** The latest point at or before the given moment, for "a day ago". */
    @Select("""
            SELECT taken_at, total_value FROM portfolio_value_snapshots
             WHERE account_id = #{accountId} AND taken_at <= #{at}
             ORDER BY taken_at DESC LIMIT 1""")
    Snapshot findAtOrBefore(@Param("accountId") Long accountId, @Param("at") Instant at);

    /** Deposits minus withdrawals since a moment, so adding cash does not look like a gain. */
    @Select("""
            SELECT COALESCE(SUM(CASE kind WHEN 'DEPOSIT' THEN amount ELSE -amount END), 0)
              FROM cash_movements WHERE account_id = #{accountId} AND created_at > #{since}""")
    BigDecimal netFundsSince(@Param("accountId") Long accountId, @Param("since") Instant since);
}
