package aiai.ai.core;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * User: Serg
 * Date: 03.08.2017
 * Time: 21:38
 */
public class TestCreateDatasetPath {


    @Test
    public void testCreatePath() {
        Assert.assertEquals("004", String.format("%03d", 4) );
    }
}
