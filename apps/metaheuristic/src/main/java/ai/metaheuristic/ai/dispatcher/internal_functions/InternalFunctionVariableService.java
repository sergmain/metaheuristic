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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
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

    private final VariableService variableService;
    private final GlobalVariableRepository globalVariableRepository;
    private final GlobalVariableService globalVariableService;

    public void storeToFile(VariableUtils.VariableHolder holder, File file) {
        if (holder.variable!=null) {
            variableService.storeToFileWithTx(Objects.requireNonNull(holder.variable).id, file);
        }
        else {
            globalVariableService.storeToFileWithTx(Objects.requireNonNull(holder.globalVariable).id, file);
        }
    }

    public List<VariableUtils.VariableHolder> discoverVariables(Long execContextId, String taskContextId, String name) {
        return discoverVariables(execContextId, taskContextId, new String[]{name});
    }

    public List<VariableUtils.VariableHolder> discoverVariables(Long execContextId, String taskContextId, String[] names) {
        List<VariableUtils.VariableHolder> holders = new ArrayList<>();
        for (String name : names) {
            SimpleVariable v = variableService.findVariableInAllInternalContexts(name, taskContextId, execContextId);
            if (v!=null) {
                holders.add(new VariableUtils.VariableHolder(v));
            }
            else {
                SimpleGlobalVariable gv = globalVariableRepository.findIdByName(name);
                if (gv!=null) {
                    holders.add(new VariableUtils.VariableHolder(gv));
                }
                else {
                    throw new InternalFunctionException(
                        new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                            "Variable '"+name+"' not found in local and global contexts, internal context "+taskContextId));
                }
            }
        }
        return holders;
    }
}
