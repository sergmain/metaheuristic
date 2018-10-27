package aiai.ai.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestDigitUtils {

    @Test
    public void testPower() {
        assertEquals(0, DigitUtils.getPower(42).power7);
        assertEquals(42, DigitUtils.getPower(42).power4);
        assertEquals(1, DigitUtils.getPower(10042).power7);
        assertEquals(42, DigitUtils.getPower(10042).power4);
        assertEquals(100, DigitUtils.getPower(1009999).power7);
        assertEquals(9999, DigitUtils.getPower(1009999).power4);
    }
}
