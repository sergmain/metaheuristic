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
public class SnippetConfigListYamlUtilsV2
        extends AbstractParamsYamlUtils<SnippetConfigListYamlV2, SnippetConfigListYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SnippetConfigListYamlV2.class);
    }

    @Override
    public SnippetConfigListYaml upgradeTo(SnippetConfigListYamlV2 src, Long ... vars) {
        src.checkIntegrity();
        SnippetConfigListYaml trg = new SnippetConfigListYaml();
        trg.snippets = src.snippets.stream().map(snSrc-> {
            SnippetConfigListYaml.SnippetConfigYaml snTrg = new SnippetConfigListYaml.SnippetConfigYaml();
            BeanUtils.copyProperties(snSrc, snTrg);

            if (snSrc.checksumMap!=null) {
                snTrg.checksumMap = new HashMap<>(snSrc.checksumMap);
            }
            if (snSrc.info!=null) {
                snTrg.info = new SnippetConfigListYaml.SnippetInfo(snSrc.info.signed, snSrc.info.length);
            }
            if (snSrc.metas!=null) {
                snTrg.metas = new ArrayList<>(snSrc.metas);
            }
            if (snSrc.ml!=null) {
                snTrg.ml = new SnippetConfigListYaml.MachineLearning(snSrc.ml.metrics, snSrc.ml.fitting);
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
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(SnippetConfigListYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public SnippetConfigListYamlV2 to(String s) {
        if (S.b(s)) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final SnippetConfigListYamlV2 p = getYaml().load(s);
        return p;
    }

}
