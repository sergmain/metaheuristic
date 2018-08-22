/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.core;

import aiai.ai.utils.EnvProperty;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestEnvProperty {

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.download-dataset-task.timeout'), 1, 10, 5) }")
    private int envProperty;

    @Value("#{ T(aiai.ai.core.TestEnvProperty).getFile(environment.getProperty('aiai.station.download-dataset-task.timeout'), \"aaa.xml\" )}")
    private File file1;

    @Value("#{ T(aiai.ai.core.TestEnvProperty).getFile(environment.getProperty('aiai.station.download-dataset-task.timeout'), \"pom.xml\" )}")
    private File file2;

    public static File getFile( String filename, String defFilename) {
        return new File(defFilename);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testProp() {
        Assert.assertEquals(5, envProperty);
        Assert.assertEquals(new File("aaa.xml"), file1);
        Assert.assertEquals(new File("pom.xml"), file2);


        Assert.assertEquals(4, EnvProperty.minMax("4", 1, 5, 3));
        Assert.assertEquals(1, EnvProperty.minMax("-1", 1, 5, 3));
        Assert.assertEquals(1, EnvProperty.minMax("1", 1, 5,3 ));
        Assert.assertEquals(5, EnvProperty.minMax("5", 1, 5, 3));
        Assert.assertEquals(5, EnvProperty.minMax("9", 1, 5, 3));

        Assert.assertEquals(2, EnvProperty.minMax(" ", 1, 5, 2));

        thrown.expect(IllegalStateException.class);
        Assert.assertEquals(5, EnvProperty.minMax(" ", 1, 5, null));

        thrown.expect(IllegalStateException.class);
        Assert.assertEquals(5, EnvProperty.minMax(" ", 1, 5, 0));

        thrown.expect(IllegalStateException.class);
        Assert.assertEquals(5, EnvProperty.minMax(" ", 1, 5, 6));

        thrown.expect(NumberFormatException.class);
        EnvProperty.minMax("abc", 1, 5, 3);
    }
}
