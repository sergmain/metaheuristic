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

package ai.metaheuristic.ai.dispatcher.source_code.graph;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.exceptions.SourceCodeGraphException;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.lang.Nullable;

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
            throw new SourceCodeGraphException("#564.020 (CollectionUtils.isEmpty(sourceCodeParams.source.processes))");
        }

        SourceCodeData.SourceCodeGraph scg = new SourceCodeData.SourceCodeGraph();
        scg.clean = sourceCodeParams.source.clean;
        scg.variables.globals = sourceCodeParams.source.variables.globals;
        sourceCodeParams.source.variables.inputs.stream().map(v->getVariable(sourceCodeParams, v)).collect(Collectors.toCollection(()->scg.variables.inputs));
        sourceCodeParams.source.variables.outputs.stream().map(v->getVariable(sourceCodeParams, v)).collect(Collectors.toCollection(()->scg.variables.outputs));
        scg.variables.inline.putAll(sourceCodeParams.source.variables.inline);

        String currentInternalContextId = contextIdSupplier.get();
        boolean finishPresent = false;
        Set<String> processCodes = new HashSet<>();
        Map<String, Long> ids = new HashMap<>();
        AtomicLong currId = new AtomicLong();
        Set<ExecContextData.ProcessVertex> parentProcesses =  new HashSet<>();

        for (SourceCodeParamsYaml.Process p : sourceCodeParams.source.processes) {
            if (finishPresent) {
                throw new SourceCodeGraphException("#564.040 mh.finish isn't the last process");
            }
            checkProcessCode(processCodes, p);

            ExecContextData.ProcessVertex vertex = addProcessVertex(sourceCodeParams, scg, currentInternalContextId, ids, currId, parentProcesses, p);

            if (Consts.MH_FINISH_FUNCTION.equals(p.function.code)) {
                finishPresent = true;
            }

            parentProcesses = processSubProcesses(contextIdSupplier, sourceCodeParams, scg, vertex, currentInternalContextId, processCodes, ids, currId, p.subProcesses);
        }
        if (!finishPresent) {
            SourceCodeParamsYaml.Process p = createFinishProcess();
            addProcessVertex(sourceCodeParams, scg, Consts.TOP_LEVEL_CONTEXT_ID, ids, currId, parentProcesses, p);
        }
        return scg;
    }

    private static ExecContextData.ProcessVertex addProcessVertex(
            SourceCodeParamsYaml sourceCodeParams, SourceCodeData.SourceCodeGraph scg,
            String currentInternalContextId, Map<String, Long> ids, AtomicLong currId,
            Set<ExecContextData.ProcessVertex> parentProcesses, SourceCodeParamsYaml.Process p) {

        ExecContextParamsYaml.Process processInGraph = toProcessForExecCode(sourceCodeParams, p, currentInternalContextId);
        scg.processes.add(processInGraph);
        ExecContextData.ProcessVertex vertex = createProcessVertex(ids, currId, p.code, currentInternalContextId);
        ExecContextProcessGraphService.addProcessVertexToGraph(scg.processGraph, vertex, parentProcesses);
        return vertex;
    }

    private static SourceCodeParamsYaml.Process createFinishProcess() {
        SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
        p.code = Consts.MH_FINISH_FUNCTION;
        p.name = Consts.MH_FINISH_FUNCTION;
        SourceCodeParamsYaml.FunctionDefForSourceCode f = new SourceCodeParamsYaml.FunctionDefForSourceCode();
        f.code = Consts.MH_FINISH_FUNCTION;
        f.context = EnumsApi.FunctionExecContext.internal;
        p.function = f;
        return p;
    }

    private static Set<ExecContextData.ProcessVertex> processSubProcesses(
            Supplier<String> contextIdSupplier, SourceCodeParamsYaml sourceCodeParams,
            SourceCodeData.SourceCodeGraph scg, ExecContextData.ProcessVertex parentProcess,
            String currentInternalContextId, Set<String> processCodes, Map<String, Long> ids,
            AtomicLong currId, @Nullable SourceCodeParamsYaml.SubProcesses subProcesses) {

        Set<ExecContextData.ProcessVertex> lastProcesses = new HashSet<>();
        // tasks for sub-processes of internal function will be produced at runtime phase
        if (subProcesses !=null && subProcesses.processes != null && !subProcesses.processes.isEmpty()) {
            Set<ExecContextData.ProcessVertex> prevProcesses = new HashSet<>();
            String subInternalContextId = null;
            if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                subInternalContextId = currentInternalContextId + ContextUtils.CONTEXT_DIGIT_SEPARATOR + contextIdSupplier.get();
            }
            List<ExecContextData.ProcessVertex> andProcesses = new ArrayList<>();
            Set<ExecContextData.ProcessVertex> tempLastProcesses = CollectionUtils.asSet(parentProcess);
            for (SourceCodeParamsYaml.Process subP : subProcesses.processes) {
                checkProcessCode(processCodes, subP);
                if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.and || subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.or) {
                    subInternalContextId = currentInternalContextId + ContextUtils.CONTEXT_DIGIT_SEPARATOR + contextIdSupplier.get();
                    tempLastProcesses.add(parentProcess);
                }
                else if (subProcesses.logic== EnumsApi.SourceCodeSubProcessLogic.sequential) {
                    // .
                }
                else {
                    throw new NotImplementedException("#564.060 not yet, logic: " + subProcesses.logic);
                }
                if (subInternalContextId==null) {
                    throw new IllegalStateException("#564.080 (subInternalContextId==null)");
                }
                Set<ExecContextData.ProcessVertex> tempParents;
                if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.and || subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.or) {
                    tempParents = CollectionUtils.asSet(parentProcess);
                }
                else if (subProcesses.logic== EnumsApi.SourceCodeSubProcessLogic.sequential) {
                    tempParents = tempLastProcesses;
                }
                else {
                    throw new NotImplementedException("#564.100 not yet");
                }

                ExecContextData.ProcessVertex subV = addProcessVertex(sourceCodeParams, scg, subInternalContextId, ids, currId, tempParents, subP);
                tempLastProcesses = processSubProcesses(contextIdSupplier, sourceCodeParams, scg, subV, subInternalContextId, processCodes, ids, currId, subP.subProcesses);

                if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                    prevProcesses = CollectionUtils.asSet(subV);
                }
                else if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.and) {
                    andProcesses.add(subV);
                }
                else {
                    throw new NotImplementedException("#564.120 not yet");
                }
            }
            lastProcesses.addAll(andProcesses);
            lastProcesses.addAll(prevProcesses);
            lastProcesses.addAll(tempLastProcesses);
        }
        lastProcesses.add(parentProcess);
        return lastProcesses;
    }

    public static ExecContextData.ProcessVertex createProcessVertex(Map<String, Long> ids, AtomicLong currId, String process, String internalContextId) {
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
        if (o.cache!=null) {
            pr.cache = new ExecContextParamsYaml.Cache(o.cache.enabled, o.cache.omitInline);
        }
        pr.tags = o.tags;
        pr.priority = o.priority;
        return pr;
    }

    private static ExecContextParamsYaml.Variable getVariable(SourceCodeParamsYaml sourceCodeParams, SourceCodeParamsYaml.Variable v) {
        EnumsApi.VariableContext context = sourceCodeParams.source.variables.globals!=null &&
                sourceCodeParams.source.variables.globals.stream().anyMatch(g->g.equals(v.name))
                ? EnumsApi.VariableContext.global
                : ( v.array ? EnumsApi.VariableContext.array :  EnumsApi.VariableContext.local );
        return new ExecContextParamsYaml.Variable(v.name, context, v.getSourcing(), v.git, v.disk, v.parentContext, v.type, v.getNullable(), v.ext);
    }

    private static void checkProcessCode(Set<String> processCodes, SourceCodeParamsYaml.Process p) {
        if (processCodes.contains(p.code)) {
            throw new SourceCodeGraphException("#564.140 (processCodes.contains(p.code))");
        }
        processCodes.add(p.code);
    }
}
