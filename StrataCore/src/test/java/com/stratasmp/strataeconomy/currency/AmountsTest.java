package com.stratasmp.strataeconomy.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AmountsTest {

    @ParameterizedTest
    @CsvSource({"1,1", "0,0", "2.5k,2500", "10K,10000", "3m,3000000", "1.001m,1001000", "1b,1000000000",
            "1t,1000000000000", "'1,000',1000", " 42 ,42", "0.5,0", "999999999999999,999999999999999"})
    void parsesPlainAndSuffixedAmounts(String input, long expected) {
        assertEquals(expected, Amounts.parse(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "-5", "+5", "1e5", "1e99999999", "1e-999999999", "Infinity", "NaN", "1.2.3",
            "k", "1.1234567891"})
    void rejectsJunk(String input) {
        assertThrows(NumberFormatException.class, () -> Amounts.parse(input));
    }

    @Test
    void rejectsAmountsAboveTheCap() {
        assertThrows(NumberFormatException.class, () -> Amounts.parse("1000001t"));
        assertThrows(NumberFormatException.class, () -> Amounts.parse("99999999999999999999"));
        assertEquals(Amounts.MAX_AMOUNT, Amounts.parse("1000t"));
    }

    @Test
    void exponentInputNeverStallsTheCaller() {
        // this used to hang the tick thread: BigDecimal expanded the exponent before the cap was checked
        assertTimeout(Duration.ofSeconds(2), () -> {
            assertThrows(NumberFormatException.class, () -> Amounts.parse("1e99999999"));
            assertThrows(NumberFormatException.class, () -> Amounts.parse("1e-999999999"));
        });
    }

    @Test
    void formatsShortAmounts() {
        assertEquals("999", Amounts.formatShort(999));
        assertEquals("1.5M", Amounts.formatShort(1_500_000));
        assertEquals("2B", Amounts.formatShort(2_000_000_000L));
        assertEquals("3T", Amounts.formatShort(3_000_000_000_000L));
    }
}
