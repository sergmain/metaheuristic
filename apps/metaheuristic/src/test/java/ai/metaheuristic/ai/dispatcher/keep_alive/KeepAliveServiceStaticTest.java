/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.keep_alive;

import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import org.junit.jupiter.api.Test;

import static ai.metaheuristic.ai.dispatcher.keep_alive.KeepAliveService.coreMetadataDifferent;
import static ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml.Core;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sergio Lissner
 * Date: 10/29/2023
 * Time: 9:26 PM
 */
public class KeepAliveServiceStaticTest {

    @Test
    public void test_coreMetadataDifferent() {
        assertFalse(coreMetadataDifferent(new Core("/aaa", 1L, "code", "tag1"), new CoreStatusYaml("code", "/aaa", "tag1")));
        assertTrue(coreMetadataDifferent(new Core("/aaa", 1L, "code", "tag1"), new CoreStatusYaml("code", "/aaa", "tag2")));
        assertTrue(coreMetadataDifferent(new Core("/aaa", 1L, "code", "tag1"), new CoreStatusYaml("code", "/bbb", "tag1")));
    }
}
