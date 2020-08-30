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

package ai.metaheuristic.commons.yaml.variable;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 4/11/2020
 * Time: 10:03 PM
 */
@SuppressWarnings("DuplicatedCode")
public class VariableArrayParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<VariableArrayParamsYamlV1, VariableArrayParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(VariableArrayParamsYamlV1.class);
    }

    @NonNull
    @Override
    public VariableArrayParamsYaml upgradeTo(VariableArrayParamsYamlV1 v1, Long ... vars) {
        v1.checkIntegrity();
        VariableArrayParamsYaml t = new VariableArrayParamsYaml();
        v1.array.stream().map(VariableArrayParamsYamlUtilsV1::upInputVariable).collect(Collectors.toCollection(()->t.array));
        t.checkIntegrity();
        return t;
    }

    private static VariableArrayParamsYaml.Variable upInputVariable(VariableArrayParamsYamlV1.VariableV1 v1) {
        VariableArrayParamsYaml.Variable v = new VariableArrayParamsYaml.Variable();
        v.id = v1.id;
        v.name = v1.name;
        v.dataType = v1.dataType;
        v.sourcing = v1.sourcing;
        v.git = v1.git;
        v.disk = v1.disk;
        v.filename = v1.filename;
        return v;
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
    public String toString(@NonNull VariableArrayParamsYamlV1 params) {
        return getYaml().dump(params);
    }

    @NonNull
    @Override
    public VariableArrayParamsYamlV1 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        //noinspection UnnecessaryLocalVariable
        final VariableArrayParamsYamlV1 p = getYaml().load(yaml);
        return p;
    }

}
