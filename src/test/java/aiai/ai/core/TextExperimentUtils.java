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

import aiai.ai.launchpad.experiment.ExperimentUtils;
import org.junit.Assert;
import org.junit.Test;

public class TextExperimentUtils {

    @Test
    public void testCreatePath() {
        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 0), ExperimentUtils.getEpochVariants("") );
        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 0), ExperimentUtils.getEpochVariants(" ") );
        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 0), ExperimentUtils.getEpochVariants(null) );

        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 1), ExperimentUtils.getEpochVariants("10") );
        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 1), ExperimentUtils.getEpochVariants(" 10 ") );

        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 3), ExperimentUtils.getEpochVariants(" [ 10, 15, 37] ") );
        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 2), ExperimentUtils.getEpochVariants(" [ 10, 15, ] ") );

        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 2), ExperimentUtils.getEpochVariants(" Range ( 10, 20, 5) ") );
        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 3), ExperimentUtils.getEpochVariants(" Range ( 10, 21, 5) ") );
        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 1), ExperimentUtils.getEpochVariants(" Range ( 10, 15, 5) ") );
        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 2), ExperimentUtils.getEpochVariants(" Range ( 10, 16, 5) ") );
        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 1), ExperimentUtils.getEpochVariants(" Range ( 10, 14, 5) ") );
        Assert.assertEquals( new ExperimentUtils.NumberOfVariants(true, null, 0), ExperimentUtils.getEpochVariants(" Range ( 10, 10, 5) ") );
        Assert.assertFalse(ExperimentUtils.getEpochVariants(" Range ( 10, 14, ) ").isStatus());
        Assert.assertFalse(ExperimentUtils.getEpochVariants(" Range ( , 14, 10) ").isStatus());
        Assert.assertFalse(ExperimentUtils.getEpochVariants(" Range ( abc, 15, 3) ").isStatus());
        Assert.assertFalse(ExperimentUtils.getEpochVariants(" Range ( 10, abc, 3) ").isStatus());
        Assert.assertFalse(ExperimentUtils.getEpochVariants(" Range ( 10, 15, abc) ").isStatus());
        Assert.assertFalse(ExperimentUtils.getEpochVariants(" abc ").isStatus());
        Assert.assertFalse(ExperimentUtils.getEpochVariants(" [ 10, abc, 37] ").isStatus());

    }

}
