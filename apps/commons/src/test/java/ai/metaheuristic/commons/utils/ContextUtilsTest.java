/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.commons.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;import java.nio.file.Path;import java.util.List;

import static ai.metaheuristic.commons.utils.ContextUtils.*;import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 3/8/2026
 * Time: 3:59 PM
 */
@Execution(ExecutionMode.CONCURRENT)
class ContextUtilsTest {

    @Test
    void test_createPath_1(@TempDir Path path) throws IOException {
        // real case -

/*
        2026-03-11T14:47:13.029-07:00 ERROR 36564 --- [    v-tread-274] a.m.a.d.i.InternalFunctionProcessor      : 977.060 system error while processing internal function 'mh.aggregate', error: Illegal char <|> at index 5: 1,4,5|1#1

        java.nio.file.InvalidPathException: Illegal char <|> at index 5: 1,4,5|1#1
        at java.base/sun.nio.fs.WindowsPathParser.normalize(WindowsPathParser.java:191) ~[na:na]
        at java.base/sun.nio.fs.WindowsPathParser.parse(WindowsPathParser.java:142) ~[na:na]
        at java.base/sun.nio.fs.WindowsPathParser.parse(WindowsPathParser.java:46) ~[na:na]
        at java.base/sun.nio.fs.WindowsPath.parse(WindowsPath.java:92) ~[na:na]
        at java.base/sun.nio.fs.WindowsFileSystem.getPath(WindowsFileSystem.java:203) ~[na:na]
        at java.base/java.nio.file.Path.resolve(Path.java:513) ~[na:na]
        at ai.metaheuristic.ai.dispatcher.internal_functions.aggregate.AggregateFunction.processInternal(AggregateFunction.java:173) ~[classes/:na]
        at ai.metaheuristic.ai.dispatcher.internal_functions.aggregate.AggregateFunction.process(AggregateFunction.java:124) ~[classes/:na]
*/

        String contextId = "1" + CONTEXT_DIGIT_SEPARATOR + "2" + ANCESTOR_SEPARATOR + "1" + CONTEXT_SEPARATOR + "0";
        Path p = path.resolve(normalize(contextId));
    }

    @Test
    void test_filterTaskContexts_1() {
        String taskContextId1 = "1,2";
        List<String> ctxIds = List.of("1,2,5,6|1|0#0", "1,2,5|1#0", "1,2,5,6", "1", "1,2,5");

        // aсt
        var result = ai.metaheuristic.commons.utils.ContextUtils.filterTaskContexts(taskContextId1, ctxIds);


        assertEquals(4, result.size());
        assertFalse(result.contains("1"));

        assertTrue(result.contains("1,2,5,6|1|0#0"));
        assertTrue(result.contains("1,2,5|1#0"));
        assertTrue(result.contains("1,2,5,6"));
        assertTrue(result.contains("1,2,5"));
    }

    @Test
    void test_filterTaskContexts_2() {
        String taskContextId1 = "1,2,5,6|1|0#0";
        List<String> ctxIds = List.of("1,2,5,6|1|0#0", "1,2,5|1#0", "1,2,5,6", "1", "1,2,5");

        // aсt
        var result = ai.metaheuristic.commons.utils.ContextUtils.filterTaskContexts(taskContextId1, ctxIds);


        assertEquals(1, result.size());
        assertFalse(result.contains("1"));
        assertFalse(result.contains("1,2,5|1#0"));
        assertFalse(result.contains("1,2,5,6"));
        assertFalse(result.contains("1,2,5"));

        assertTrue(result.contains("1,2,5,6|1|0#0"));
    }
}