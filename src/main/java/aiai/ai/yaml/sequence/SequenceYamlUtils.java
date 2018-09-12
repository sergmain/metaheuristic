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
package aiai.ai.yaml.sequence;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import javax.annotation.PostConstruct;

@Service
public class SequenceYamlUtils {

    private Yaml yamlSequenceYaml;

    // TODO 2018.09.12. so, snakeYaml isn't thread-safe?
    private static final Object syncObj = new Object();

    public SequenceYamlUtils() {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Representer representer = new Representer();
        representer.addClassTag(SequenceYaml.class, Tag.MAP);

        yamlSequenceYaml = new Yaml(new Constructor(SequenceYaml.class), representer, options);
    }

    public String toString(SequenceYaml sequenceYaml) {
        synchronized (syncObj) {
            return yamlSequenceYaml.dump(sequenceYaml);
        }
    }

    public SequenceYaml toSequenceYaml(String s) {
        synchronized (syncObj) {
            return yamlSequenceYaml.load(s);
        }
    }


}
