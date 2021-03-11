/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphFactory;
import ai.metaheuristic.api.EnumsApi;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Serge
 * Date: 3/10/2021
 * Time: 5:14 PM
 */
public class TestSourceCodeWithMultiLevels {

    @Test
    public void test() throws IOException {

        String src = IOUtils.resourceToString("/source_code/yaml/source-code-with-multi-levels.yaml", StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        SourceCodeData.SourceCodeGraph sourceCodeGraph = SourceCodeGraphFactory.parse(
                EnumsApi.SourceCodeLang.yaml, src, () -> "" + contextId.incrementAndGet());

    }
}
