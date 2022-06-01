/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.repositories.VariablePostgresqlRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Timestamp;

/**
 * @author Serge
 * Date: 12/22/2021
 * Time: 10:36 PM
 */
@Service
@Slf4j
@Profile({"dispatcher & postgresql"})
@RequiredArgsConstructor
public class VariablePostgresqlService implements VariableDatabaseSpecificService {

    public final VariablePostgresqlRepository variablePostgresqlRepository;

    public void copyData(VariableData.StoredVariable srcVariable, TaskParamsYaml.OutputVariable targetVariable) {
        TxUtils.checkTxExists();

        // TODO 2021-10-14 right now, an array variable isn't supported
        variablePostgresqlRepository.copyData(srcVariable.id, targetVariable.id, targetVariable.filename, new Timestamp(System.currentTimeMillis()));
    }
}
