/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.commons.graph.source_code_graph;

//import ai.metaheuristic.ai.Consts;
//import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
//import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
//import ai.metaheuristic.ai.exceptions.SourceCodeGraphException;
//import ai.metaheuristic.ai.utils.CollectionUtils;
//import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.api.data.SourceCodeGraph;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.exceptions.SourceCodeGraphException;
import ai.metaheuristic.commons.graph.ExecContextProcessGraphService;
import ai.metaheuristic.commons.utils.CollectionUtils;
import ai.metaheuristic.commons.utils.ContextUtils;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.apache.commons.lang3.NotImplementedException;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ai.metaheuristic.commons.CommonConsts.MH_FINISH_FUNCTION;
import static ai.metaheuristic.commons.CommonConsts.TOP_LEVEL_CONTEXT_ID;

/**
 * @author Serge
 * Date: 2/14/2020
 * Time: 10:49 PM
 */
public class SourceCodeGraphLanguageYaml implements SourceCodeGraphLanguage {

    @Override
    public SourceCodeGraph parse(String sourceCode, Supplier<String> contextIdSupplier) {

        SourceCodeParamsYaml sourceCodeParams = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCode);
        if (CollectionUtils.isEmpty(sourceCodeParams.source.processes)) {
            throw new SourceCodeGraphException("564.020 (CollectionUtils.isEmpty(sourceCodeParams.source.processes))");
        }

        SourceCodeGraph scg = new SourceCodeGraph();
        scg.uid = sourceCodeParams.source.uid;
        if (scg.uid == null || scg.uid.isBlank()) {
            throw new SourceCodeGraphException("564.025 uid is required in source code definition");
        }
        scg.clean = sourceCodeParams.source.clean;
        scg.type = sourceCodeParams.source.type;
        if (sourceCodeParams.source.instances != null) {
            scg.instances = sourceCodeParams.source.instances;
        }
        if (sourceCodeParams.source.ac != null) {
            scg.ac = new SourceCodeGraph.AccessControl(sourceCodeParams.source.ac.groups);
        }
        if (sourceCodeParams.source.metas != null) {
            scg.metas.addAll(sourceCodeParams.source.metas);
        }
        if (sourceCodeParams.source.variables!=null) {
            scg.variables.globals = sourceCodeParams.source.variables.globals;
            sourceCodeParams.source.variables.inputs.stream().map(v -> getVariable(sourceCodeParams, v)).collect(Collectors.toCollection(() -> scg.variables.inputs));
            sourceCodeParams.source.variables.outputs.stream().map(v -> getVariable(sourceCodeParams, v)).collect(Collectors.toCollection(() -> scg.variables.outputs));
            scg.variables.inline.putAll(sourceCodeParams.source.variables.inline);
        }

        String currentInternalContextId = contextIdSupplier.get();
        boolean finishPresent = false;
        Set<String> processCodes = new HashSet<>();
        Map<String, Long> ids = new HashMap<>();
        AtomicLong currId = new AtomicLong();
        Set<ExecContextApiData.ProcessVertex> parentProcesses =  new HashSet<>();

        for (SourceCodeParamsYaml.Process p : sourceCodeParams.source.processes) {
            if (finishPresent) {
                throw new SourceCodeGraphException("564.040 mh.finish isn't the last process");
            }
            checkProcessCode(processCodes, p);

            ExecContextApiData.ProcessVertex vertex = addProcessVertex(sourceCodeParams, scg, currentInternalContextId, ids, currId, parentProcesses, p);

            if (MH_FINISH_FUNCTION.equals(p.function.code)) {
                finishPresent = true;
            }

            parentProcesses = processSubProcesses(contextIdSupplier, sourceCodeParams, scg, vertex, currentInternalContextId, processCodes, ids, currId, p.subProcesses);
        }
        if (!finishPresent) {
            SourceCodeParamsYaml.Process p = createFinishProcess();
            addProcessVertex(sourceCodeParams, scg, TOP_LEVEL_CONTEXT_ID, ids, currId, parentProcesses, p);
        }
        return scg;
    }

    private static ExecContextApiData.ProcessVertex addProcessVertex(
            SourceCodeParamsYaml sourceCodeParams, SourceCodeGraph scg,
            String currentInternalContextId, Map<String, Long> ids, AtomicLong currId,
            Set<ExecContextApiData.ProcessVertex> parentProcesses, SourceCodeParamsYaml.Process p) {

        ExecContextParamsYaml.Process processInGraph = toProcessForExecCode(sourceCodeParams, p, currentInternalContextId);
        scg.processes.add(processInGraph);
        ExecContextApiData.ProcessVertex vertex = createProcessVertex(ids, currId, p.code, currentInternalContextId);
        ExecContextProcessGraphService.addProcessVertexToGraph(scg.processGraph, vertex, parentProcesses);
        return vertex;
    }

    private static SourceCodeParamsYaml.Process createFinishProcess() {
        SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
        p.code = MH_FINISH_FUNCTION;
        p.name = MH_FINISH_FUNCTION;
        SourceCodeParamsYaml.FunctionDefForSourceCode f = new SourceCodeParamsYaml.FunctionDefForSourceCode();
        f.code = MH_FINISH_FUNCTION;
        f.context = EnumsApi.FunctionExecContext.internal;
        p.function = f;
        return p;
    }

    private static Set<ExecContextApiData.ProcessVertex> processSubProcesses(
        Supplier<String> contextIdSupplier, SourceCodeParamsYaml sourceCodeParams,
        SourceCodeGraph scg, ExecContextApiData.ProcessVertex parentProcess,
        String currentInternalContextId, Set<String> processCodes, Map<String, Long> ids,
        AtomicLong currId, SourceCodeParamsYaml.@Nullable SubProcesses subProcesses) {

        Set<ExecContextApiData.ProcessVertex> lastProcesses = new HashSet<>();
        // tasks for sub-processes of internal function will be produced at runtime phase
        if (subProcesses !=null && subProcesses.processes != null && !subProcesses.processes.isEmpty()) {
            Set<ExecContextApiData.ProcessVertex> prevProcesses = new HashSet<>();
            String subInternalContextId = null;
            if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                subInternalContextId = currentInternalContextId + ContextUtils.CONTEXT_DIGIT_SEPARATOR + contextIdSupplier.get();
            }
            List<ExecContextApiData.ProcessVertex> andProcesses = new ArrayList<>();
            // Accumulate recursive leaves from ALL 'and' children, not just the last one
            Set<ExecContextApiData.ProcessVertex> allAndLastProcesses = new HashSet<>();
            Set<ExecContextApiData.ProcessVertex> tempLastProcesses = CollectionUtils.asSet(parentProcess);
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
                    throw new NotImplementedException("564.060 not yet, logic: " + subProcesses.logic);
                }
                if (subInternalContextId==null) {
                    throw new IllegalStateException("564.080 (subInternalContextId==null)");
                }
                Set<ExecContextApiData.ProcessVertex> tempParents;
                if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.and || subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.or) {
                    tempParents = CollectionUtils.asSet(parentProcess);
                }
                else if (subProcesses.logic== EnumsApi.SourceCodeSubProcessLogic.sequential) {
                    tempParents = tempLastProcesses;
                }
                else {
                    throw new NotImplementedException("564.100 not yet");
                }

                ExecContextApiData.ProcessVertex subV = addProcessVertex(sourceCodeParams, scg, subInternalContextId, ids, currId, tempParents, subP);
                tempLastProcesses = processSubProcesses(contextIdSupplier, sourceCodeParams, scg, subV, subInternalContextId, processCodes, ids, currId, subP.subProcesses);

                if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                    prevProcesses = CollectionUtils.asSet(subV);
                }
                else if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.and) {
                    // Collect recursive leaves, excluding the direct child vertex if it has subprocesses.
                    // Leaf branches (no subprocesses) are their own leaves and must be included.
                    for (ExecContextApiData.ProcessVertex v : tempLastProcesses) {
                        boolean isDirectChildOfParent = scg.processGraph.incomingEdgesOf(v).stream()
                                .anyMatch(e -> scg.processGraph.getEdgeSource(e).equals(parentProcess));
                        boolean hasChildren = scg.processGraph.outDegreeOf(v) > 0;
                        if (!(isDirectChildOfParent && hasChildren)) {
                            allAndLastProcesses.add(v);
                        }
                    }
                }
                else {
                    throw new NotImplementedException("564.120 not yet");
                }
            }
            lastProcesses.addAll(prevProcesses);
            if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.and || subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.or) {
                lastProcesses.addAll(allAndLastProcesses);
            }
            else {
                lastProcesses.addAll(tempLastProcesses);
            }
        }
        lastProcesses.add(parentProcess);
        return lastProcesses;
    }

    public static ExecContextApiData.ProcessVertex createProcessVertex(Map<String, Long> ids, AtomicLong currId, String process, String internalContextId) {
        return new ExecContextApiData.ProcessVertex(ids.computeIfAbsent(process, o -> currId.incrementAndGet()), process, internalContextId);
    }

    private static ExecContextParamsYaml.Process toProcessForExecCode(SourceCodeParamsYaml sourceCodeParams, SourceCodeParamsYaml.Process o, String internalContextId) {
        ExecContextParamsYaml.Process pr = new ExecContextParamsYaml.Process();
        pr.internalContextId = internalContextId;
        pr.processName = o.name;
        pr.processCode = o.code;
        pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
        o.inputs.stream().map(v->getVariable(sourceCodeParams, v)).collect(Collectors.toCollection(()->pr.inputs));
        o.outputs.stream().map(v->getVariable(sourceCodeParams, v)).collect(Collectors.toCollection(()->pr.outputs));
        pr.function = new ExecContextParamsYaml.FunctionDefinition(o.function.code, o.function.params, o.function.context, o.function.refType);
        pr.logic = o.subProcesses!=null ? o.subProcesses.logic : null;
        pr.preFunctions = o.preFunctions !=null ? o.preFunctions.stream().map(d->new ExecContextParamsYaml.FunctionDefinition(d.code, d.params, d.context, d.refType)).collect(Collectors.toList()) : null;
        pr.postFunctions = o.postFunctions !=null ? o.postFunctions.stream().map(d->new ExecContextParamsYaml.FunctionDefinition(d.code, d.params, d.context, d.refType)).collect(Collectors.toList()) : null;
        pr.metas = o.metas;
        if (o.cache!=null) {
            pr.cache = new ExecContextParamsYaml.Cache(o.cache.enabled, o.cache.omitInline, o.cache.cacheMeta);
        }
        pr.tag = o.tag;
        pr.priority = o.priority;
        pr.condition = o.condition!=null ? o.condition.conditions : null;
        pr.triesAfterError = o.triesAfterError;
        return pr;
    }

    private static ExecContextParamsYaml.Variable getVariable(SourceCodeParamsYaml sourceCodeParams, SourceCodeParamsYaml.Variable v) {
        EnumsApi.VariableContext context = sourceCodeParams.source.variables!=null && sourceCodeParams.source.variables.globals!=null &&
                sourceCodeParams.source.variables.globals.stream().anyMatch(g->g.equals(v.name))
                ? EnumsApi.VariableContext.global
                : ( v.array ? EnumsApi.VariableContext.array :  EnumsApi.VariableContext.local );
        return new ExecContextParamsYaml.Variable(v.name, context, v.getSourcing(), v.git, v.disk, v.parentContext, v.type, v.getNullable(), v.ext, v.mutable);
    }

    private static void checkProcessCode(Set<String> processCodes, SourceCodeParamsYaml.Process p) {
        if (processCodes.contains(p.code)) {
            throw new SourceCodeGraphException("564.140 (processCodes.contains(p.code))");
        }
        processCodes.add(p.code);
    }
}
