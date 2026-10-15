package com.capstone.etp.funds;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Cash movements. The balance change and the record are written together by the service, in one transaction. */
@Mapper
public interface FundsMapper {

    /** The balance after an addition, or no row when the account is not active. The version moves so an order in flight retries. */
    @Select("""
            UPDATE accounts
               SET cash_balance = cash_balance + #{amount}, version = version + 1, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{accountId} AND status::text = 'ACTIVE'
            RETURNING cash_balance""")
    BigDecimal deposit(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);

    /** The balance after a withdrawal, or no row when the account is not active or holds less than the amount. */
    @Select("""
            UPDATE accounts
               SET cash_balance = cash_balance - #{amount}, version = version + 1, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{accountId} AND status::text = 'ACTIVE' AND cash_balance >= #{amount}
            RETURNING cash_balance""")
    BigDecimal withdraw(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);

    @Select("SELECT currency FROM accounts WHERE id = #{accountId}")
    String currencyOf(@Param("accountId") Long accountId);

    @Select("SELECT status::text FROM accounts WHERE id = #{accountId}")
    String statusOf(@Param("accountId") Long accountId);

    @Insert("""
            INSERT INTO cash_movements (id, account_id, kind, amount, currency, method, balance_after)
            VALUES (#{id}, #{accountId}, #{kind}, #{amount}, #{currency}, #{method}, #{balanceAfter})""")
    int insertMovement(@Param("id") UUID id,
                       @Param("accountId") Long accountId,
                       @Param("kind") FundsKind kind,
                       @Param("amount") BigDecimal amount,
                       @Param("currency") String currency,
                       @Param("method") FundsMethod method,
                       @Param("balanceAfter") BigDecimal balanceAfter);

    @Select("""
            SELECT id, account_id, kind, amount, currency, method, balance_after, created_at
              FROM cash_movements WHERE account_id = #{accountId}
             ORDER BY created_at DESC LIMIT #{limit}""")
    List<FundsMovement> findByAccount(@Param("accountId") Long accountId, @Param("limit") int limit);
}
