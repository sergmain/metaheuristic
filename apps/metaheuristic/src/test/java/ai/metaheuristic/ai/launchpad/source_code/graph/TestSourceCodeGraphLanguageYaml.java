/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.source_code.graph;

import ai.metaheuristic.api.EnumsApi;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Serge
 * Date: 2/17/2020
 * Time: 2:38 AM
 */
public class TestSourceCodeGraphLanguageYaml {

    @Test
    public void test_01() {

        String sourceCode = "";
        AtomicLong contextId = new AtomicLong();
        SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, sourceCode, ()-> ""+ contextId.incrementAndGet());
    }
}
