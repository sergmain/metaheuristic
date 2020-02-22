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
package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.data.FunctionApiData;
import org.junit.Assert;
import org.junit.Test;

public class TestFunctionExec {

    @Test
    public void testEmptyString() {
        Assert.assertNull(FunctionExecUtils.to(""));
        Assert.assertNull(FunctionExecUtils.to((String) null));
    }

    @Test
    public void testMarshaling() {
        FunctionApiData.FunctionExec output = new FunctionApiData.FunctionExec();
        output.exec = new FunctionApiData.SystemExecResult("output-of-a-function",true, 0, "output#1");

        String yaml = FunctionExecUtils.toString(output);
        FunctionApiData.FunctionExec output1 = FunctionExecUtils.to(yaml);
        Assert.assertEquals(output, output1);
    }

}
