package aiai.ai.some;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;

public class TestBreak {

    @Test
    public void testBreak() {

        int[] ints = new int[] {1,2,3,4,5};
        int i=0;
        while (i++<10) {
            for (int anInt : ints) {
                if (anInt == 3) {
                    break;
                }
            }
        }
        assertNotEquals(1, i);
    }
}
