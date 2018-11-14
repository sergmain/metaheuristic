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
package aiai.ai.yaml;

import aiai.ai.yaml.config.DatasetPreparingConfigUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestDatasetPreparingConfig {


    @Test
    public void testEmptyString() {
        Assert.assertNull(DatasetPreparingConfigUtils.to(""));
        Assert.assertNull(DatasetPreparingConfigUtils.to((String) null));
    }

    @Test
    public void testMarshaling() {
        DatasetPreparingConfig output = new DatasetPreparingConfig();
        output.parts.add("output#1");
        output.parts.add("output#2");
        output.rawFile="raw.txt";
        output.datasetFile="dataset.txt";

        String yaml = DatasetPreparingConfigUtils.toString(output);
        DatasetPreparingConfig output1 = DatasetPreparingConfigUtils.to(yaml);
        System.out.println(yaml);

        Assert.assertEquals(output, output1);
    }

}
