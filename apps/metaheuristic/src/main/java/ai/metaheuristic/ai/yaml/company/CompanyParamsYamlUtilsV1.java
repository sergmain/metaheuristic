/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.company;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class CompanyParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<CompanyParamsYamlV1, CompanyParamsYamlV2, CompanyParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(CompanyParamsYamlV1.class);
    }

    @Override
    public CompanyParamsYamlV2 upgradeTo(CompanyParamsYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        CompanyParamsYamlV2 trg = new CompanyParamsYamlV2();
        if (src.ac!=null) {
            trg.ac = new CompanyParamsYamlV2.AccessControlV2(src.ac.groups);
        }
        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public CompanyParamsYamlUtilsV2 nextUtil() {
        return (CompanyParamsYamlUtilsV2) CompanyParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(CompanyParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public CompanyParamsYamlV1 to(String s) {
        if (S.b(s)) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final CompanyParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
