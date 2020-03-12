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
package ai.metaheuristic.ai.core;

import ai.metaheuristic.ai.utils.EnvProperty;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestEnvProperty {

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.download-dataset-task.timeout'), 1, 10, 5) }")
    private int envProperty;

    @Value("#{ T(ai.metaheuristic.ai.core.TestEnvProperty).getFile(environment.getProperty('mh.processor.download-dataset-task.timeout'), \"aaa.xml\" )}")
    private File file1;

    @Value("#{ T(ai.metaheuristic.ai.core.TestEnvProperty).getFile(environment.getProperty('mh.processor.download-dataset-task.timeout'), \"pom.xml\" )}")
    private File file2;

    public static File getFile( String filename, String defFilename) {
        return new File(defFilename);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testProp() {
        assertEquals(5, envProperty);
        assertEquals(new File("aaa.xml"), file1);
        assertEquals(new File("pom.xml"), file2);


        assertEquals(4, EnvProperty.minMax("4", 1, 5, 3));
        assertEquals(1, EnvProperty.minMax("-1", 1, 5, 3));
        assertEquals(1, EnvProperty.minMax("1", 1, 5,3 ));
        assertEquals(5, EnvProperty.minMax("5", 1, 5, 3));
        assertEquals(5, EnvProperty.minMax("9", 1, 5, 3));

        assertEquals(2, EnvProperty.minMax(" ", 1, 5, 2));

        thrown.expect(IllegalStateException.class);
        assertEquals(5, EnvProperty.minMax(" ", 1, 5, null));

        thrown.expect(IllegalStateException.class);
        assertEquals(5, EnvProperty.minMax(" ", 1, 5, 0));

        thrown.expect(IllegalStateException.class);
        assertEquals(5, EnvProperty.minMax(" ", 1, 5, 6));

        thrown.expect(NumberFormatException.class);
        EnvProperty.minMax("abc", 1, 5, 3);
    }
}
