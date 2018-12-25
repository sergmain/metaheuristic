package aiai.ai.utils;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestCommons {

    @Test
    public void testStringUtilsSubstring() {
        assertEquals("abc", StringUtils.substring("abc", 0, 100));
    }
}
