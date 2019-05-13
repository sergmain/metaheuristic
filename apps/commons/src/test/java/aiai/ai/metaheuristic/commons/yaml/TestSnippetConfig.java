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
package aiai.ai.metaheuristic.commons.yaml;

import ai.metaheuristic.api.v1.data.SnippetApiData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigList;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigListUtils;
import org.junit.Test;

import java.util.ArrayList;

public class TestSnippetConfig {

    @Test
    public void test() {
        SnippetConfigList scs = new SnippetConfigList();
        scs.snippets = new ArrayList<>();

        SnippetApiData.SnippetConfig config = new SnippetApiData.SnippetConfig();
        config.code = "aiai.fit.default.snippet:1.0";
        config.type = CommonConsts.FIT_TYPE;
        config.file = "fit-model.py";

        config.checksumMap = Checksum.fromJson("{\"checksums\":{\"SHA256\":\"6b168e87112aceaea0bc514e48b123db1528052c8c784702b1c50acd37aa89cb\"}}").checksums;
        config.checksumMap.putAll( Checksum.fromJson("{\"checksums\":{\"MD5\":\"6b168e87112aceaea0bc514e48b123db1528052c8c784702b1c50acd37aa89cb\"}}").checksums);

        scs.snippets.add(config);

        String yaml = SnippetConfigListUtils.toString(scs);
        System.out.println(yaml);
    }

}
