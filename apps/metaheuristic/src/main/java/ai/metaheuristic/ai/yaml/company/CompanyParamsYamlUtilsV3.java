/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Sergio Lissner
 */
public class CompanyParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<CompanyParamsYamlV3, CompanyParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 3;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(CompanyParamsYamlV3.class);
    }

    @NonNull
    @Override
    public CompanyParamsYaml upgradeTo(@NonNull CompanyParamsYamlV3 src) {
        src.checkIntegrity();
        CompanyParamsYaml trg = new CompanyParamsYaml();
        if (src.ac!=null) {
            trg.ac = new CompanyParamsYaml.AccessControl(src.ac.groups);
        }
        if (src.vault!=null) {
            trg.vault = new CompanyParamsYaml.VaultEntries(
                src.vault.salt, src.vault.iterations, src.vault.encryptedEntries);
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
    public String toString(@NonNull CompanyParamsYamlV3 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public CompanyParamsYamlV3 to(@NonNull String s) {
        final CompanyParamsYamlV3 p = getYaml().load(s);
        return p;
    }

}
