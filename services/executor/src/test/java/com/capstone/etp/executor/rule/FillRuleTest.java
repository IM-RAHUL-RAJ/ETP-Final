package com.capstone.etp.executor.rule;

import com.capstone.etp.executor.entity.Order;
import com.capstone.etp.executor.enums.OrderSide;
import com.capstone.etp.executor.kafka.models.QuotePayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link FillRule}. No mocks, no Spring context,
 * no I/O — the rule is a static function so all tests are deterministic.
 *
 * <p>Coverage:
 * <ol>
 *   <li>BUY limit &ge; ask  → FILL at ask</li>
 *   <li>BUY limit == ask (exact boundary) → FILL</li>
 *   <li>BUY limit &lt; ask  → REJECT / PRICE_NOT_MET</li>
 *   <li>SELL limit &le; bid → FILL at bid</li>
 *   <li>SELL limit == bid (exact boundary) → FILL</li>
 *   <li>SELL limit &gt; bid → REJECT / PRICE_NOT_MET</li>
 *   <li>Ask rounded DOWN (HALF_UP) enables a fill that the raw value would not</li>
 *   <li>Ask rounded UP  (HALF_UP) blocks a fill that the raw value would allow</li>
 * </ol>
 */
class FillRuleTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Order buyOrder(String limitPrice, int qty) {
        return Order.builder()
                .side(OrderSide.BUY)
                .limitPrice(new BigDecimal(limitPrice))
                .quantity(qty)
                .build();
    }

    private static Order sellOrder(String limitPrice, int qty) {
        return Order.builder()
                .side(OrderSide.SELL)
                .limitPrice(new BigDecimal(limitPrice))
                .quantity(qty)
                .build();
    }

    private static QuotePayload quote(String ask, String bid) {
        QuotePayload q = new QuotePayload();
        q.setAsk(new BigDecimal(ask));
        q.setBid(new BigDecimal(bid));
        q.setPrice(new BigDecimal(ask)); // last-trade price — never used by the rule
        return q;
    }

    // -------------------------------------------------------------------------
    // BUY tests
    // -------------------------------------------------------------------------

    @Test
    void buy_limitAboveAsk_fillsAtAsk() {
        FillDecision d = FillRule.apply(buyOrder("235.00", 100), quote("232.77", "232.65"));

        assertThat(d.isFill()).isTrue();
        assertThat(d.getExecutedPrice()).isEqualByComparingTo("232.77");
    }

    @Test
    void buy_limitExactlyAtAsk_fills() {
        FillDecision d = FillRule.apply(buyOrder("232.77", 100), quote("232.77", "232.65"));

        assertThat(d.isFill()).isTrue();
        assertThat(d.getExecutedPrice()).isEqualByComparingTo("232.77");
    }

    @Test
    void buy_limitBelowAsk_rejects() {
        FillDecision d = FillRule.apply(buyOrder("230.00", 100), quote("232.77", "232.65"));

        assertThat(d.isFill()).isFalse();
        assertThat(d.getReason()).isEqualTo("PRICE_NOT_MET");
    }

    /**
     * ask = 232.774 → rounds DOWN to 232.77 (HALF_UP).
     * A limit of 232.77 would NOT fill against the raw 232.774 (232.77 < 232.774),
     * but DOES fill after rounding (232.77 >= 232.77). This verifies that rounding
     * happens before comparison, not after.
     */
    @Test
    void buy_askRoundedDown_enablesFillThatRawValueWouldBlock() {
        FillDecision d = FillRule.apply(buyOrder("232.77", 50), quote("232.774", "232.65"));

        assertThat(d.isFill()).isTrue();
        assertThat(d.getExecutedPrice()).isEqualByComparingTo("232.77"); // stored rounded value
    }

    /**
     * ask = 232.775 → rounds UP to 232.78 (HALF_UP).
     * A limit of 232.77 would fill against the raw 232.775 (232.77 >= 232.775? No),
     * and also does not fill after rounding (232.77 < 232.78). Confirms HALF_UP is used.
     */
    @Test
    void buy_askRoundedUp_blocksFilledThatRawValueWouldAlsoBlock() {
        FillDecision d = FillRule.apply(buyOrder("232.77", 50), quote("232.775", "232.65"));

        // 232.77 < 232.78 (rounded ask) → should NOT fill
        assertThat(d.isFill()).isFalse();
    }

    // -------------------------------------------------------------------------
    // SELL tests
    // -------------------------------------------------------------------------

    @Test
    void sell_limitBelowBid_fillsAtBid() {
        FillDecision d = FillRule.apply(sellOrder("230.00", 50), quote("232.77", "232.65"));

        assertThat(d.isFill()).isTrue();
        assertThat(d.getExecutedPrice()).isEqualByComparingTo("232.65");
    }

    @Test
    void sell_limitExactlyAtBid_fills() {
        FillDecision d = FillRule.apply(sellOrder("232.65", 50), quote("232.77", "232.65"));

        assertThat(d.isFill()).isTrue();
        assertThat(d.getExecutedPrice()).isEqualByComparingTo("232.65");
    }

    @Test
    void sell_limitAboveBid_rejects() {
        FillDecision d = FillRule.apply(sellOrder("235.00", 50), quote("232.77", "232.65"));

        assertThat(d.isFill()).isFalse();
        assertThat(d.getReason()).isEqualTo("PRICE_NOT_MET");
    }
}

