package aiai.ai.utils;

import lombok.AllArgsConstructor;

public class DigitUtils {

    public static final int DIV = 10_000;

    @AllArgsConstructor
    public static class Power {
        public long power7;
        public long power4;
    }

    public static Power getPower(long num) {
        return new Power(num/DIV, num % DIV );
    }
}
