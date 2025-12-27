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

package ai.metaheuristic.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Serge
 * Date: 3/5/2022
 * Time: 4:27 PM
 */
@Execution(CONCURRENT)
public class TestConsts {

    @Test
    public void testConsts() {
        assertTrue(Consts.DISPATCHER_REQUEST_PROCESSSING_MILLISECONDS < Consts.DISPATCHER_SOCKET_TIMEOUT_MILLISECONDS);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    public void test_bearer_with_space() {
        assertTrue(Consts.BEARER.length()>1);
        assertEquals(' ', Consts.BEARER.charAt(Consts.BEARER.length() - 1));
    }


}
