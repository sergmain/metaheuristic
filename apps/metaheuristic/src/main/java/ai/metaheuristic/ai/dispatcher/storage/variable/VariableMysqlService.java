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

package ai.metaheuristic.ai.dispatcher.storage.variable;

import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.repositories.VariableBlobMysqlRepository;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 12/22/2021
 * Time: 10:36 PM
 */
@Service
@Slf4j
@Profile({"dispatcher & mysql"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VariableMysqlService implements VariableDatabaseSpecificService {

    private final VariableDatabaseSpecificCommonService variableDatabaseSpecificCommonService;
    private final VariableBlobMysqlRepository variableMysqlRepository;

    public void copyData(VariableData.StoredVariable storedVariable, TaskParamsYaml.OutputVariable targetVariable) {
        variableDatabaseSpecificCommonService.copyData(storedVariable, targetVariable, variableMysqlRepository::copyData);
    }
}
