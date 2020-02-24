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

package ai.metaheuristic.ai.dispatcher.source_code.graph;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.exceptions.SourceCodeGraphException;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.apache.commons.lang3.NotImplementedException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 2/14/2020
 * Time: 10:49 PM
 */
public class SourceCodeGraphLanguageYaml implements SourceCodeGraphLanguage {

    @Override
    public SourceCodeData.SourceCodeGraph parse(String sourceCode, Supplier<String> contextIdSupplier) {

        SourceCodeParamsYaml sourceCodeParams = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCode);
        if (CollectionUtils.isEmpty(sourceCodeParams.source.processes)) {
            throw new SourceCodeGraphException("(CollectionUtils.isEmpty(sourceCodeParams.source.processes))");
        }

        SourceCodeData.SourceCodeGraph scg = new SourceCodeData.SourceCodeGraph();
        scg.clean = sourceCodeParams.source.clean;
        scg.variables.globals = sourceCodeParams.source.variables.globals;
        scg.variables.startInputAs = sourceCodeParams.source.variables.startInputAs;
        scg.variables.inline.putAll(sourceCodeParams.source.variables.inline);

        long internalContextId = 1L;
        List<String> parentProcesses =  new ArrayList<>();

        String currentInternalContextId = "" + internalContextId;
        boolean finishPresent = false;
        Set<String> processCodes = new HashSet<>();
        for (SourceCodeParamsYaml.Process p : sourceCodeParams.source.processes) {
            if (finishPresent) {
                throw new SourceCodeGraphException("(finishPresent)");
            }
            checkProcessCode(processCodes, p);

            SourceCodeGraphUtils.addNewTasksToGraph(scg, p.code, parentProcesses);
            if (Consts.MH_FINISH_FUNCTION.equals(p.function.code)) {
                finishPresent = true;
            }

            parentProcesses.clear();
            parentProcesses.add(p.code);

            SourceCodeParamsYaml.SubProcesses subProcesses = p.subProcesses;
            // tasks for sub-processes of internal function will be produced at runtime phase
            if (subProcesses !=null && p.function.context!= EnumsApi.FunctionExecContext.internal) {
                if (CollectionUtils.isEmpty(subProcesses.processes)) {
                    throw new SourceCodeGraphException("(subProcesses !=null) && (CollectionUtils.isEmpty(subProcesses.processes))");
                }
                List<String> prevProcesses = new ArrayList<>();
                prevProcesses.add(p.code);
                String subInternalContextId = null;
                if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                    subInternalContextId = currentInternalContextId + ',' + contextIdSupplier.get();
                }
                List<String> andProcesses = new ArrayList<>();
                for (SourceCodeParamsYaml.Process subP : subProcesses.processes) {
                    checkProcessCode(processCodes, subP);

                    if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.and || subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.or) {
                        subInternalContextId = currentInternalContextId + ',' + contextIdSupplier.get();
                    }
                    if (subInternalContextId==null) {
                        throw new IllegalStateException("(subInternalContextId==null)");
                    }
                    String subV = subP.code;
                    SourceCodeGraphUtils.addNewTasksToGraph(scg, subV, prevProcesses);
                    if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                        prevProcesses.clear();
                        prevProcesses.add(subV);
                    }
                    else if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.and) {
                        andProcesses.add(subV);
                    }
                    else {
                        throw new NotImplementedException("not yet");
                    }
                }
                parentProcesses.addAll(andProcesses);
                parentProcesses.addAll(prevProcesses);
            }
        }
        if (!finishPresent) {
            String finishVertex = Consts.MH_FINISH_FUNCTION;
            SourceCodeGraphUtils.addNewTasksToGraph(scg, finishVertex, parentProcesses);
        }
        return scg;
    }

    private void checkProcessCode(Set<String> processCodes, SourceCodeParamsYaml.Process p) {
        if (processCodes.contains(p.code)) {
            throw new SourceCodeGraphException("(processCodes.contains(p.code))");
        }
        processCodes.add(p.code);
    }
}
