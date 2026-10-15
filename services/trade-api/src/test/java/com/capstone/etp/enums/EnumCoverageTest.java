package com.capstone.etp.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumCoverageTest {

    @Test
    void valuesAndValueOf_coverAllEnums() {
        assertEquals(AccountStatus.ACTIVE, AccountStatus.valueOf("ACTIVE"));
        assertEquals(3, AccountStatus.values().length);

        assertEquals(AccountType.CRYPTO, AccountType.valueOf("CRYPTO"));
        assertEquals(4, AccountType.values().length);

        assertEquals(AssetClass.FX, AssetClass.valueOf("FX"));
        assertEquals(4, AssetClass.values().length);

        assertEquals(ClientStatus.DORMANT, ClientStatus.valueOf("DORMANT"));
        assertEquals(3, ClientStatus.values().length);

        assertEquals(Gender.OTHER, Gender.valueOf("OTHER"));
        assertEquals(3, Gender.values().length);

        assertEquals(OrderSide.BUY, OrderSide.valueOf("BUY"));
        assertEquals(2, OrderSide.values().length);

        assertEquals(OrderStatus.REJECTED, OrderStatus.valueOf("REJECTED"));
        assertEquals(4, OrderStatus.values().length);

        assertEquals(RiskProfile.AGGRESSIVE, RiskProfile.valueOf("AGGRESSIVE"));
        assertEquals(3, RiskProfile.values().length);

        assertEquals(TransactionStatus.FAILED, TransactionStatus.valueOf("FAILED"));
        assertEquals(2, TransactionStatus.values().length);
    }
}
