package com.capstone.etp.automation;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/** The strategies customers have set up. Created off; only an explicit switch-on makes one trade. */
@Mapper
public interface AutomationMapper {

    String COLUMNS = "id, account_id, type, symbol, quantity, short_period, long_period, period, multiplier, enabled";

    /** @return the new id, or null when the account already has {@code cap} strategies */
    @Select("""
            INSERT INTO automation_strategies (account_id, type, symbol, quantity, short_period, long_period, period, multiplier, enabled)
            SELECT #{accountId}, #{type}, #{symbol}, #{quantity}, #{shortPeriod}, #{longPeriod}, #{period}, #{multiplier}, FALSE
             WHERE (SELECT COUNT(*) FROM automation_strategies WHERE account_id = #{accountId}) < #{cap}
            RETURNING id""")
    Long insertWithinCap(@Param("accountId") Long accountId,
                         @Param("type") String type,
                         @Param("symbol") String symbol,
                         @Param("quantity") Integer quantity,
                         @Param("shortPeriod") Integer shortPeriod,
                         @Param("longPeriod") Integer longPeriod,
                         @Param("period") Integer period,
                         @Param("multiplier") BigDecimal multiplier,
                         @Param("cap") int cap);

    @Select("SELECT " + COLUMNS + " FROM automation_strategies WHERE account_id = #{accountId} ORDER BY id")
    List<StrategyDefinition> findByAccount(@Param("accountId") Long accountId);

    @Select("SELECT " + COLUMNS + " FROM automation_strategies WHERE id = #{id}")
    StrategyDefinition findById(@Param("id") Long id);

    /** What the engine runs: only the strategies that are switched on. */
    @Select("SELECT " + COLUMNS + " FROM automation_strategies WHERE enabled ORDER BY id")
    List<StrategyDefinition> findEnabled();

    @Update("UPDATE automation_strategies SET enabled = #{enabled} WHERE id = #{id}")
    int setEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    @Delete("DELETE FROM automation_strategies WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
