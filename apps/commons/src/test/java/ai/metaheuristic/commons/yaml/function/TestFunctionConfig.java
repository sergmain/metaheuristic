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

package ai.metaheuristic.commons.yaml.function;

import ai.metaheuristic.commons.yaml.bundle.BundleParamsYaml;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 3/11/2022
 * Time: 1:59 AM
 */
public class TestFunctionConfig {

    // this test is cheching that both classes
    //  ai.metaheuristic.commons.yaml.bundle.BundleParamsYaml
    // and
    //  ai.metaheuristic.commons.yaml.function.FunctionConfigYaml
    // have the same set of fields, except field version
    @Test
    public void test() {
        Field[] fields = BundleParamsYaml.FunctionConfig.class.getDeclaredFields();

        Field[] fields1 = ai.metaheuristic.commons.yaml.function.FunctionConfigYaml.class.getDeclaredFields();

        boolean ok = check(fields, fields1, "FunctionConfigYaml", Set.of());
        ok &= check(fields1, fields, "FunctionConfigListYaml.FunctionConfig", Set.of("version"));

        assertTrue(ok);
    }

    private static boolean check(Field[] fields, Field[] fields1, String name, Set<String> set) {
        boolean ok = true;
        for (Field field : fields) {
            boolean found = false;
            final String fieldName = field.getName();
            if (set.contains(fieldName)) {
                continue;
            }
            for (Field field1 : fields1) {
                if (field1.getName().equals(fieldName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                ok = false;
                System.out.println("field '"+ fieldName +"' wasn't found in class "+ name);
            }
        }
        return ok;
    }
}
