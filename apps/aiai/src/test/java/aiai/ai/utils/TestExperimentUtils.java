package aiai.ai.utils;

import aiai.ai.launchpad.beans.ExperimentSnippet;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TestExperimentUtils {

    @Test
    public void testNumberOfVariants() {

        final String listAsStr = String.valueOf(Arrays.asList("aaa","bbb","ccc"));
        ExperimentUtils.NumberOfVariants nov = ExperimentUtils.getNumberOfVariants(listAsStr);

        assertNotNull(nov);
        assertTrue(nov.status);
        assertNotNull(nov.values);
        assertNull(nov.error);

        assertEquals(3, nov.getCount());
        assertEquals(3, nov.values.size());

        assertEquals("aaa", nov.values.get(0));
        assertEquals("bbb", nov.values.get(1));
        assertEquals("ccc", nov.values.get(2));
    }

    @Test
    public void testSorting() {
        ExperimentSnippet es1 = new ExperimentSnippet();
        es1.setSnippetCode("snippet-1:1");
        es1.setType("predict");

        ExperimentSnippet es2 = new ExperimentSnippet();
        es2.setSnippetCode("snippet-2:1");
        es2.setType("fit");

        List<ExperimentSnippet>experimentSnippets = Arrays.asList( es1, es2 );
        ExperimentUtils.sortExperimentSnippets(experimentSnippets);
        assertEquals("fit", experimentSnippets.get(0).getType());
        assertEquals("predict", experimentSnippets.get(1).getType());
    }
}
