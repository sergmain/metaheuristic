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

package ai.metaheuristic.ai.dispatcher;

import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 10/24/2021
 * Time: 7:31 PM
 */
public class TestNamesOfClasses {

    @Test
    public void test() {
        // !!! __ Do not change the name of class to SouthBridgeController ___ !!!
        //noinspection ConstantExpression
        assertEquals("Southbridge"+"Controller", SouthbridgeController.class.getSimpleName());
    }
}
