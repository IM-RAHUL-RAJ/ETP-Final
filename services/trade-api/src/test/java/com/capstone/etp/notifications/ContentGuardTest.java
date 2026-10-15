package com.capstone.etp.notifications;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentGuardTest {

    @Test
    void ordinaryOrderTextPasses() {
        assertDoesNotThrow(() -> ContentGuard.requireSafe("Your BUY order for 100 AAPL was filled at 232.71 (your limit was 233.00)."));
        assertDoesNotThrow(() -> ContentGuard.requireSafe("X:BTC-USD rose above 42000.00. The latest price is 42010.55."));
        assertDoesNotThrow(() -> ContentGuard.requireSafe("Order filled: SELL 1000000 FX:USDJPY"));
    }

    @Test
    void anEmailAddressIsRefused() {
        assertThrows(ContentGuard.UnsafeContentException.class,
                () -> ContentGuard.requireSafe("Sent to priya.menon@example.com"));
    }

    @Test
    void aCardSizedNumberIsRefused() {
        assertThrows(ContentGuard.UnsafeContentException.class,
                () -> ContentGuard.requireSafe("Card 4111111111111111 was used"));
    }

    @Test
    void credentialWordsAreRefused() {
        assertThrows(ContentGuard.UnsafeContentException.class, () -> ContentGuard.requireSafe("Your temporary password is abc"));
        assertThrows(ContentGuard.UnsafeContentException.class, () -> ContentGuard.requireSafe("Authorization: Bearer xyz"));
        assertThrows(ContentGuard.UnsafeContentException.class, () -> ContentGuard.requireSafe("api key leaked"));
    }

    @Test
    void aLineBreakIsRefusedSoASubjectCannotInjectAMailHeader() {
        assertThrows(ContentGuard.UnsafeContentException.class,
                () -> ContentGuard.requireSafe("Order filled: BUY 1 AAPL\r\nBcc: someone@elsewhere.example"));
        assertThrows(ContentGuard.UnsafeContentException.class, () -> ContentGuard.requireSafe("one\ntwo"));
        assertThrows(ContentGuard.UnsafeContentException.class, () -> ContentGuard.requireSafe("bell\u0007"));
    }

    @Test
    void theRefusalNeverEchoesWhatItRefused() {
        ContentGuard.UnsafeContentException ex = assertThrows(ContentGuard.UnsafeContentException.class,
                () -> ContentGuard.requireSafe("mail me at secret.person@example.com"));

        assertFalse(ex.getMessage().contains("secret.person"));
    }

    @Test
    void nullIsFine() {
        assertDoesNotThrow(() -> ContentGuard.requireSafe(null));
    }
}
