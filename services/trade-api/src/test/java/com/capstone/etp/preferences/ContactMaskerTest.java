package com.capstone.etp.preferences;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContactMaskerTest {

    @Test
    void masksAnEmailKeepingTheFirstLetterAndTheDomain() {
        assertEquals("p***@example.com", ContactMasker.maskEmail("priya.menon@example.com"));
    }

    @Test
    void masksAPhoneKeepingTheLastFourDigits() {
        assertEquals("******3210", ContactMasker.maskPhone("9876543210"));
    }

    @Test
    void shortOrMissingValuesAreNeverEchoed() {
        assertEquals("****", ContactMasker.maskPhone("1234"));
        assertEquals("***", ContactMasker.maskEmail("no-at-sign"));
        assertNull(ContactMasker.maskEmail(null));
        assertNull(ContactMasker.maskPhone(" "));
    }
}