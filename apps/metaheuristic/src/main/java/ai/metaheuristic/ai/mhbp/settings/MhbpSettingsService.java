/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.settings;

import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Auth;
import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.mhbp.beans.ScenarioGroup;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.mhbp.repositories.AuthRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioGroupRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioRepository;
import ai.metaheuristic.ai.mhbp.yaml.backup.BackupParams;
import ai.metaheuristic.ai.mhbp.yaml.backup.BackupParamsUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.stream.StreamSupport;

/**
 * @author Sergio Lissner
 * Date: 5/20/2023
 * Time: 10:40 PM
 */
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class MhbpSettingsService {

    private final ApiRepository apiRepository;
    private final AuthRepository authRepository;
    private final ScenarioGroupRepository scenarioGroupRepository;
    private final ScenarioRepository scenarioRepository;

    public String export() {
        BackupParams b = new BackupParams();
        b.backup.apis = StreamSupport.stream(apiRepository.findAll().spliterator(), false).map(MhbpSettingsService::toApi).toList();
        b.backup.auths = StreamSupport.stream(authRepository.findAll().spliterator(), false).map(MhbpSettingsService::toAuth).toList();
        b.backup.scenarioGroups = scenarioGroupRepository.findAllAsList().stream().map(this::toScenarioGroup).toList();

        return BackupParamsUtils.UTILS.toString(b);
    }

    private BackupParams.ScenarioGroup toScenarioGroup(ScenarioGroup v1) {
        BackupParams.ScenarioGroup sg = new BackupParams.ScenarioGroup();
        sg.createdOn = v1.createdOn;
        sg.name = v1.name;
        sg.description = v1.description;
        sg.scenarios = scenarioRepository.findAllByScenarioGroupId(v1.id).stream().map(MhbpSettingsService::toScenario).toList();;

        return sg;
    }

    private static BackupParams.Scenario toScenario(Scenario v1) {
        BackupParams.Scenario s = new BackupParams.Scenario();
        s.createdOn = v1.createdOn;
        s.name = v1.name;
        s.description  =v1.description;
        s.params = v1.getParams();
        return s;
    }

    private static BackupParams.Auth toAuth(Auth v1) {
        BackupParams.Auth a = new BackupParams.Auth();
        a.createdOn = v1.createdOn;
        a.code = v1.code;
        a.params = v1.getParams();
        return a;
    }

    private static BackupParams.Api toApi(Api v1) {
        BackupParams.Api a = new BackupParams.Api();
        a.createdOn = v1.createdOn;
        a.code = v1.code;
        a.name = v1.name;
        a.scheme = v1.getScheme();

        return a;
    }


}
