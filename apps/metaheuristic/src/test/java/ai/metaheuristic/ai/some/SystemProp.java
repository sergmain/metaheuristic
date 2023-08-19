/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.some;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Sergio Lissner
 * Date: 8/19/2023
 * Time: 3:14 PM
 */
public class SystemProp {
    @Test
    public void test_() {
        System.getProperties().forEach( (o, o2) -> {
            if (o instanceof String s && StringUtils.equalsAny(s, "java.class.path", "java.library.path", "line.separator")) {
                return;
            }
            System.out.printf("'\t%s: %s\n", o, o2);
        });
    }
}
