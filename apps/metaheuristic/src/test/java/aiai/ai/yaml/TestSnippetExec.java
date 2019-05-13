/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.yaml;

import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.api.v1.data.SnippetApiData;
import org.junit.Assert;
import org.junit.Test;

public class TestSnippetExec {

    @Test
    public void testEmptyString() {
        Assert.assertNull(SnippetExecUtils.to(""));
        Assert.assertNull(SnippetExecUtils.to((String) null));
    }

    @Test
    public void testMarshaling() {
        SnippetApiData.SnippetExec output = new SnippetApiData.SnippetExec();
        output.exec = new SnippetApiData.SnippetExecResult(true, 0, "output#1");

        String yaml = SnippetExecUtils.toString(output);
        SnippetApiData.SnippetExec output1 = SnippetExecUtils.to(yaml);
        Assert.assertEquals(output, output1);
    }

}
