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

package ai.metaheuristic.ai.mhbp.yaml.backup;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

public class BackupParamsUtilsV1 extends
        AbstractParamsYamlUtils<BackupParamsV1, BackupParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(BackupParamsV1.class);
    }

    @Override
    public BackupParams upgradeTo(BackupParamsV1 v1) {
        v1.checkIntegrity();

        BackupParams t = new BackupParams();
        t.backup.apis = v1.backup.apis.stream().map(BackupParamsUtilsV1::toApi).toList();
        t.backup.auths = v1.backup.auths.stream().map(BackupParamsUtilsV1::toAuth).toList();
        t.backup.scenarioGroups = v1.backup.scenarioGroups.stream().map(BackupParamsUtilsV1::toScenarioGroup).toList();

        t.checkIntegrity();
        return t;
    }

    private static BackupParams.ScenarioGroup toScenarioGroup(BackupParamsV1.ScenarioGroupV1 v1) {
        BackupParams.ScenarioGroup sg = new BackupParams.ScenarioGroup();
        sg.createdOn = v1.createdOn;
        sg.name = v1.name;
        sg.description = v1.description;
        sg.scenarios = v1.scenarios.stream().map(BackupParamsUtilsV1::toScenario).toList();
        return sg;
    }

    private static BackupParams.Scenario toScenario(BackupParamsV1.ScenarioV1 v1) {
        BackupParams.Scenario s = new BackupParams.Scenario();
        s.createdOn = v1.createdOn;
        s.name = v1.name;
        s.description  =v1.description;
        s.params = v1.params;
        return s;
    }

    private static BackupParams.Auth toAuth(BackupParamsV1.AuthV1 v1) {
        BackupParams.Auth a = new BackupParams.Auth();
        a.createdOn = v1.createdOn;
        a.code = v1.code;
        a.params = v1.params;
        return a;
    }

    private static BackupParams.Api toApi(BackupParamsV1.ApiV1 v1) {
        BackupParams.Api a = new BackupParams.Api();
        a.createdOn = v1.createdOn;
        a.code = v1.code;
        a.name = v1.name;
        a.scheme = v1.scheme;
        return a;
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
    public String toString(BackupParamsV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @Override
    public BackupParamsV1 to(String s) {
        final BackupParamsV1 p = getYaml().load(s);
        return p;
    }

}
