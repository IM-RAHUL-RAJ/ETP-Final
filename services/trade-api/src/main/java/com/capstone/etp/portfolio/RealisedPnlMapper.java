package com.capstone.etp.portfolio;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Mapper
public interface RealisedPnlMapper {

    @Insert("""
            INSERT INTO portfolio_realised_pnl
                   (event_id, account_id, symbol, quantity, executed_price, average_cost,
                    base_currency, realised_pnl, booked_at)
            VALUES (#{eventId}, #{accountId}, #{symbol}, #{quantity}, #{executedPrice}, #{averageCost},
                    #{baseCurrency}, #{realisedPnl}, #{bookedAt})
            ON CONFLICT (event_id) DO NOTHING""")
    int insertIfAbsent(@Param("eventId") UUID eventId,
                       @Param("accountId") Long accountId,
                       @Param("symbol") String symbol,
                       @Param("quantity") int quantity,
                       @Param("executedPrice") BigDecimal executedPrice,
                       @Param("averageCost") BigDecimal averageCost,
                       @Param("baseCurrency") String baseCurrency,
                       @Param("realisedPnl") BigDecimal realisedPnl,
                       @Param("bookedAt") Instant bookedAt);

    @Select("""
            <script>
            SELECT COALESCE(SUM(realised_pnl), 0)
              FROM portfolio_realised_pnl
             WHERE account_id = #{accountId}
            <if test="from != null"> AND booked_at &gt;= #{from}</if>
            <if test="to != null"> AND booked_at &lt; #{to}</if>
            </script>""")
    BigDecimal sumRealised(@Param("accountId") Long accountId,
                           @Param("from") Instant from,
                           @Param("to") Instant to);

    @Select("""
            <script>
            SELECT symbol AS symbol, SUM(realised_pnl) AS realised
              FROM portfolio_realised_pnl
             WHERE account_id = #{accountId}
            <if test="from != null"> AND booked_at &gt;= #{from}</if>
            <if test="to != null"> AND booked_at &lt; #{to}</if>
             GROUP BY symbol
             ORDER BY symbol
            </script>""")
    List<SymbolRealised> sumRealisedBySymbol(@Param("accountId") Long accountId,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to);

    @Select("SELECT 1")
    int ping();

    class SymbolRealised {
        private String symbol;
        private BigDecimal realised;

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public BigDecimal getRealised() {
            return realised;
        }

        public void setRealised(BigDecimal realised) {
            this.realised = realised;
        }
    }
}
