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

import aiai.ai.utils.checksum.Checksum;
import aiai.ai.yaml.snippet.SnippetType;
import aiai.ai.yaml.snippet.SnippetsConfig;
import aiai.ai.yaml.snippet.SnippetsConfigUtils;
import org.junit.Test;

import java.util.ArrayList;

public class TestSnippetYaml {

    @Test
    public void test() {
        SnippetsConfig scs = new SnippetsConfig();
        scs.snippets = new ArrayList<>();

        SnippetsConfig.SnippetConfig config = new SnippetsConfig.SnippetConfig();
        config.name = "aiai.fit.default.snippet";
        config.type = SnippetType.fit;
        config.file = "fit-model.py";
        config.version = "1.0";

        config.checksums = Checksum.fromJson("{\"checksums\":{\"SHA256\":\"6b168e87112aceaea0bc514e48b123db1528052c8c784702b1c50acd37aa89cb\"}}").checksums;
        config.checksums.putAll( Checksum.fromJson("{\"checksums\":{\"MD5\":\"6b168e87112aceaea0bc514e48b123db1528052c8c784702b1c50acd37aa89cb\"}}").checksums);

        scs.snippets.add(config);

        String yaml = SnippetsConfigUtils.toString(scs);
        System.out.println(yaml);
    }

}
