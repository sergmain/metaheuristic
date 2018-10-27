package aiai.ai.utils;

import lombok.AllArgsConstructor;

public class DigitUtils {

    @AllArgsConstructor
    public static class Power {
        public long power7;
        public long power4;
    }

    public static Power getPower(long num) {
        return new Power(num/10_000, num % 10_000 );
    }
}
