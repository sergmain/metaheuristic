/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
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

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(CompanyParamsYamlV2.class);
    }

    @NonNull
    @Override
    public CompanyParamsYaml upgradeTo(@NonNull CompanyParamsYamlV2 src) {
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

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
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
    public String toString(@NonNull CompanyParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public CompanyParamsYamlV2 to(@NonNull String s) {
        final CompanyParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
