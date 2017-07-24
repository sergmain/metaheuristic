package aiai.ai.core;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * User: Serg
 * Date: 24.07.2017
 * Time: 22:09
 */
public class TestRegexSplit {
    @Test
    public void testSplit() {
        Assert.assertArrayEquals(new String[]{"str1", "str2", "\"str 3\"", "str4"}, Arrays.stream("str1, str2, \"str 3\",  str4".split("[,]")).filter(s -> s != null && s.length() > 0).map(String :: trim).toArray());
        Assert.assertEquals(Arrays.asList("str1", "str2", "\"str 3\"", "str4"), Arrays.stream("str1, str2, \"str 3\",  str4".split("[,]")).filter(s -> s != null && s.length() > 0).map(String :: trim).collect(Collectors.toList()));
    }
}
