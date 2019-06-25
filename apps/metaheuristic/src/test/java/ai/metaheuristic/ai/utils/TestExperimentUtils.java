/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentUtils;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.commons.CommonConsts;
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
        Snippet es1 = new Snippet();
        es1.setCode("snippet-1:1");
        es1.setType(CommonConsts.PREDICT_TYPE);

        Snippet es2 = new Snippet();
        es2.setCode("snippet-2:1");
        es2.setType(CommonConsts.FIT_TYPE);

        List<Snippet>experimentSnippets = Arrays.asList( es1, es2 );
        SnippetService.sortExperimentSnippets(experimentSnippets);
        assertEquals(CommonConsts.FIT_TYPE, experimentSnippets.get(0).getType());
        assertEquals(CommonConsts.PREDICT_TYPE, experimentSnippets.get(1).getType());
    }
}
