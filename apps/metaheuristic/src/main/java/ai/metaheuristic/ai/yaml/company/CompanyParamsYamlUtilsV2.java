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
public class CompanyParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<CompanyParamsYamlV2, CompanyParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(CompanyParamsYamlV2.class);
    }

    @Override
    public CompanyParamsYaml upgradeTo(CompanyParamsYamlV2 src, Long ... vars) {
        src.checkIntegrity();
        CompanyParamsYaml trg = new CompanyParamsYaml();
        if (src.ac!=null) {
            trg.ac = new CompanyParamsYaml.AccessControl(src.ac.groups);
        }
        trg.createdOn = src.createdOn;
        trg.updatedOn = src.updatedOn;
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
    public String toString(CompanyParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public CompanyParamsYamlV2 to(String s) {
        if (S.b(s)) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final CompanyParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
