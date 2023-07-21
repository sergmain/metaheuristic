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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.repositories.VariableBlobMysqlRepository;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
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

/*
    public void copyData(VariableData.StoredVariable storedVariable, TaskParamsYaml.OutputVariable targetVariable) {
        TxUtils.checkTxExists();

        CacheVariable src = cacheVariableRepository.findById(storedVariable.id).orElse(null);
        if (src==null) {
            return;
        }

        Variable trg = variableRepository.findById(targetVariable.id).orElse(null);
        if (trg==null) {
            log.warn("!!! trying to copy date to non-existed variable");
            return;
        }

        if (trg.variableBlobId==null) {
            ByteArrayInputStream bais = new ByteArrayInputStream(Consts.STUB_BYTES);
            VariableBlob variableBlob = variableBlobTxService.createOrUpdateWithInputStream(null, bais, Consts.STUB_BYTES.length);
            trg.variableBlobId = variableBlob.id;
            if (trg.variableBlobId==null) {
                throw new IllegalStateException("(trg.variableBlobId==null)");
            }
        }

        // that's right - targetVariable.filename
        trg.filename = targetVariable.filename;
        trg.uploadTs = new Timestamp(System.currentTimeMillis());

        // TODO 2021-10-14 right now, a variable as array isn't supported
        variableMysqlRepository.copyData(storedVariable.id, trg.variableBlobId);
    }
*/
}
