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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * @author Serge
 * Date: 7/30/2020
 * Time: 1:53 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class InternalFunctionVariableService {

    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final GlobalVariableRepository globalVariableRepository;
    private final GlobalVariableService globalVariableService;

    public void storeToFile(VariableUtils.VariableHolder holder, File file) {
        if (holder.variable!=null) {
            variableService.storeToFile(Objects.requireNonNull(holder.variable).id, file);
        }
        else {
            globalVariableService.storeToFile(Objects.requireNonNull(holder.globalVariable).id, file);
        }
    }

    @Nullable
    @Transactional(readOnly = true)
    public InternalFunctionData.InternalFunctionProcessingResult discoverVariables(Long execContextId, String taskContextId, String name, List<VariableUtils.VariableHolder> holders) {
        return discoverVariables(execContextId, taskContextId, new String[]{name}, holders);
    }

    @Nullable
    public InternalFunctionData.InternalFunctionProcessingResult discoverVariables(Long execContextId, String taskContextId, String[] names, List<VariableUtils.VariableHolder> holders) {
        for (String name : names) {
            SimpleVariable v = variableRepository.findByNameAndTaskContextIdAndExecContextId(name, taskContextId, execContextId);
            if (v!=null) {
                holders.add(new VariableUtils.VariableHolder(v));
            }
            else {
                SimpleGlobalVariable gv = globalVariableRepository.findIdByName(name);
                if (gv!=null) {
                    holders.add(new VariableUtils.VariableHolder(gv));
                }
                else {
                    return new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                            "Variable '"+name+"' not found in local and global contexts, internal context #"+taskContextId);
                }
            }
        }
        return null;
    }


}
