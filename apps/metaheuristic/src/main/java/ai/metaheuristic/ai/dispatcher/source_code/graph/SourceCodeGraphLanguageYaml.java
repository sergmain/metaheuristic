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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.exceptions.SourceCodeGraphException;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.apache.commons.lang3.NotImplementedException;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

        List<ExecContextData.ProcessVertex> parentProcesses =  new ArrayList<>();

        String currentInternalContextId = contextIdSupplier.get();
        boolean finishPresent = false;
        Set<String> processCodes = new HashSet<>();
        Map<String, Long> ids = new HashMap<>();
        AtomicLong currId = new AtomicLong();
        for (SourceCodeParamsYaml.Process p : sourceCodeParams.source.processes) {
            if (finishPresent) {
                throw new SourceCodeGraphException("(finishPresent)");
            }
            checkProcessCode(processCodes, p);
            ExecContextParamsYaml.Process processInGraph = toProcessForExecCode(sourceCodeParams, p, currentInternalContextId);
            scg.processes.add(processInGraph);


            ExecContextData.ProcessVertex vertex = getVertex(ids, currId, p.code, currentInternalContextId);

            ExecContextProcessGraphService.addNewTasksToGraph(scg.processGraph, vertex, parentProcesses);
            if (Consts.MH_FINISH_FUNCTION.equals(p.function.code)) {
                finishPresent = true;
            }

            parentProcesses.clear();
            parentProcesses.add(vertex);

            SourceCodeParamsYaml.SubProcesses subProcesses = p.subProcesses;
            // tasks for sub-processes of internal function will be produced at runtime phase
            if (subProcesses!=null && CollectionUtils.isNotEmpty(subProcesses.processes)) {
                // todo 2020-04-02 replace with recursion for supporting cases then there are more than 2 levels of inclusion

                List<ExecContextData.ProcessVertex> prevProcesses = new ArrayList<>();
                prevProcesses.add(vertex);
                String subInternalContextId = null;
                if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                    subInternalContextId = currentInternalContextId + ',' + contextIdSupplier.get();
                }
                List<ExecContextData.ProcessVertex> andProcesses = new ArrayList<>();
                for (SourceCodeParamsYaml.Process subP : subProcesses.processes) {
                    if (subP.subProcesses!=null && CollectionUtils.isNotEmpty(subP.subProcesses.processes)) {
                        throw new IllegalStateException("SubProcesses with level of recursion more that 1 isn't supported right now.");
                    }
                    checkProcessCode(processCodes, subP);
                    if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.and || subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.or) {
                        subInternalContextId = currentInternalContextId + ',' + contextIdSupplier.get();
                    }
                    if (subInternalContextId==null) {
                        throw new IllegalStateException("(subInternalContextId==null)");
                    }

                    processInGraph = toProcessForExecCode(sourceCodeParams, subP, subInternalContextId);
                    scg.processes.add(processInGraph);

                    ExecContextData.ProcessVertex subV = getVertex(ids, currId, subP.code, subInternalContextId);

                    ExecContextProcessGraphService.addNewTasksToGraph(scg.processGraph, subV, prevProcesses);
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
            ExecContextData.ProcessVertex finishVertex = getVertex(ids, currId, Consts.MH_FINISH_FUNCTION, currentInternalContextId);
            ExecContextProcessGraphService.addNewTasksToGraph(scg.processGraph, finishVertex, parentProcesses);
        }
        return scg;
    }

    public static ExecContextData.ProcessVertex getVertex(Map<String, Long> ids, AtomicLong currId, String process, String internalContextId) {
        return new ExecContextData.ProcessVertex(ids.computeIfAbsent(process, o -> currId.incrementAndGet()), process, internalContextId);
    }

    private static ExecContextParamsYaml.Process toProcessForExecCode(SourceCodeParamsYaml sourceCodeParams, SourceCodeParamsYaml.Process o, String internalContextId) {
        ExecContextParamsYaml.Process pr = new ExecContextParamsYaml.Process();
        pr.internalContextId = internalContextId;
        pr.processName = o.name;
        pr.processCode = o.code;
        pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
        o.inputs.stream().map(v->getVariable(sourceCodeParams, v)).collect(Collectors.toCollection(()->pr.inputs));
        o.outputs.stream().map(v->getVariable(sourceCodeParams, v)).collect(Collectors.toCollection(()->pr.outputs));
        pr.function = new ExecContextParamsYaml.FunctionDefinition(o.function.code, o.function.params, o.function.context);
        pr.logic = o.subProcesses!=null ? o.subProcesses.logic : null;
        pr.preFunctions = o.preFunctions !=null ? o.preFunctions.stream().map(d->new ExecContextParamsYaml.FunctionDefinition(d.code, d.params, d.context)).collect(Collectors.toList()) : null;
        pr.postFunctions = o.postFunctions !=null ? o.postFunctions.stream().map(d->new ExecContextParamsYaml.FunctionDefinition(d.code, d.params, d.context)).collect(Collectors.toList()) : null;
        pr.metas = o.metas;
        return pr;
    }

    private static ExecContextParamsYaml.Variable getVariable(SourceCodeParamsYaml sourceCodeParams, SourceCodeParamsYaml.Variable v) {
        EnumsApi.VariableContext context = sourceCodeParams.source.variables.globals!=null &&
                sourceCodeParams.source.variables.globals.stream().anyMatch(g->g.equals(v.name))
                ? EnumsApi.VariableContext.global
                : ( v.array ? EnumsApi.VariableContext.array :  EnumsApi.VariableContext.local );
        return new ExecContextParamsYaml.Variable(v.name, context, v.getSourcing(), v.git, v.disk, v.parentContext, v.type);
    }

    private void checkProcessCode(Set<String> processCodes, SourceCodeParamsYaml.Process p) {
        if (processCodes.contains(p.code)) {
            throw new SourceCodeGraphException("(processCodes.contains(p.code))");
        }
        processCodes.add(p.code);
    }
}
