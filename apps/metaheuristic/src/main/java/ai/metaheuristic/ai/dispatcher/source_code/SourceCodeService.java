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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class SourceCodeService {

    private final Globals globals;
    private final ExecContextRepository execContextRepository;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeValidationService sourceCodeValidationService;

    private final ExecContextFSM execContextFSM;
    private final TaskProducingService taskProducingService;

    @Transactional
    public OperationStatusRest deleteSourceCodeById(@Nullable Long sourceCodeId) {
        if (sourceCodeId==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.240 Can't delete a sourceCode while 'replicated' mode of asset is active");
        }
        SourceCode sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.250 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        sourceCodeCache.deleteById(sourceCodeId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public static List<SourceCodeParamsYaml.Variable> findVariableByType(SourceCodeParamsYaml scpy, String type) {
        List<SourceCodeParamsYaml.Variable> list = new ArrayList<>();
        for (SourceCodeParamsYaml.Process process : scpy.source.processes) {
            findVariableByType(process, type, list);
        }
        return list;

    }

    private static void findVariableByType(SourceCodeParamsYaml.Process process, String type, List<SourceCodeParamsYaml.Variable> list) {
        for (SourceCodeParamsYaml.Variable output : process.outputs) {
            if (output.type.equals(type)) {
                list.add(output);
            }
        }
        if (process.subProcesses!=null) {
            for (SourceCodeParamsYaml.Process p : process.subProcesses.processes) {
                findVariableByType(p, type, list);
            }
        }
    }

}
