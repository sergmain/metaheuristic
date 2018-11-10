package aiai.ai.flow;

import aiai.ai.launchpad.Process;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class TestProcess {


    @Test
    public void testProcessMeta() {
        Process p = new Process();
        p.meta =
                "dataset:dataset-processing\n" +
                        "raw-file:assembled-raw\n" +
                        "feature:feature";
        Properties prop = p.getMetaAsProp();
        assertEquals("dataset-processing", prop.getProperty("dataset"));
        assertEquals("assembled-raw", prop.getProperty("raw-file"));
        assertEquals("feature", prop.getProperty("feature"));
    }
}
