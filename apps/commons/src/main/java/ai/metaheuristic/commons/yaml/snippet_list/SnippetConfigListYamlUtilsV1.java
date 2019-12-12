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

package ai.metaheuristic.commons.yaml.snippet_list;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class SnippetConfigListYamlUtilsV1
        extends AbstractParamsYamlUtils<SnippetConfigListYamlV1, SnippetConfigListYamlV2, SnippetConfigListYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SnippetConfigListYamlV1.class);
    }

    @Override
    public SnippetConfigListYamlV2 upgradeTo(SnippetConfigListYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        SnippetConfigListYamlV2 trg = new SnippetConfigListYamlV2();
        trg.snippets = src.snippets.stream().map(snSrc-> {
            SnippetConfigListYamlV2.SnippetConfigYamlV2 snTrg = new SnippetConfigListYamlV2.SnippetConfigYamlV2();
            BeanUtils.copyProperties(snSrc, snTrg);

            if (snSrc.checksumMap != null) {
                snTrg.checksumMap = new HashMap<>(snSrc.checksumMap);
            }
            if (snSrc.info != null) {
                snTrg.info = new SnippetConfigListYamlV2.SnippetInfoV2(snSrc.info.signed, snSrc.info.length);
            }
            if (snSrc.metas != null) {
                snTrg.metas = new ArrayList<>(snSrc.metas);
            }
            if (snSrc.metrics) {
                snTrg.ml = new SnippetConfigListYamlV2.MachineLearningV2(true, false);
            }
            return  snTrg;
        }).collect(Collectors.toList());

        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public SnippetConfigListYamlUtilsV2 nextUtil() {
        return (SnippetConfigListYamlUtilsV2) SnippetConfigListYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(SnippetConfigListYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public SnippetConfigListYamlV1 to(String s) {
        if (S.b(s)) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final SnippetConfigListYamlV1 p = getYaml().load(s);
        return p;
    }

}
