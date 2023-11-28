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

package ai.metaheuristic.ai.functions;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.event.events.RegisterFunctionCodesForStartedExecContextEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParamsUtils;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParamsUtils;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 11/14/2023
 * Time: 11:20 PM
 */
@SuppressWarnings("BooleanMethodIsAlwaysInverted")
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionRepositoryDispatcherService {

    private final SourceCodeRepository sourceCodeRepository;
    private final ExecContextRepository execContextRepository;
    private final FunctionRepository functionRepository;

    public static class Processors {
        public long mills = System.currentTimeMillis();
        public final Set<Long> ids = new HashSet<>();

        public boolean contains(Long processorId) {
            return ids.contains(processorId);
        }
    }

    // key - function code, value - list of processorIds
    private static final LinkedHashMap<String, Processors> functionReadiness = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Processors> eldest) {
            return System.currentTimeMillis() - eldest.getValue().mills > TimeUnit.HOURS.toMillis(2);
        }
    };
    private static final Set<String> activeFunctions = new HashSet<>();
    private static final LinkedHashMap<String, FunctionRepositoryResponseParams.ShortFunctionConfig> shortFunctionConfigCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FunctionRepositoryResponseParams.ShortFunctionConfig> eldest) {
            return size()>100;
        }
    };

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public static Set<String> getActiveFunctionCode(@Nullable Long processorId) {
        readLock.lock();
        try {
            if (processorId==null) {
                return new HashSet<>(activeFunctions);
            }
            else {
                return activeFunctions.stream().filter(c->{
                    Processors processors = functionReadiness.get(c);
                    return processors==null || !processors.contains(processorId);
                }).collect(Collectors.toSet());
            }
        } finally {
            readLock.unlock();
        }
    }

    public String processRequest(String data, String remoteAddr) {
        FunctionRepositoryRequestParams p = FunctionRepositoryRequestParamsUtils.UTILS.to(data);
        FunctionRepositoryResponseParams r = new FunctionRepositoryResponseParams();
        r.success = true;
        registerReadyFunctionCodesOnProcessor(p);

        final Set<String> activeFunctionCodes = getActiveFunctionCode(p.processorId);
        if (CollectionUtils.isNotEmpty(activeFunctionCodes)) {
            r.functions = new ArrayList<>();
            for (String activeFunctionCode : activeFunctionCodes) {
                FunctionRepositoryResponseParams.ShortFunctionConfig shortFunctionConfig = toShortFunctionConfig(activeFunctionCode);
                if (shortFunctionConfig != null) {
                    r.functions.add(shortFunctionConfig);
                }
                else {
                    log.warn("479.040 Function wasn't found for code " + activeFunctionCode);
                }
            }
        }

        String response = FunctionRepositoryResponseParamsUtils.UTILS.toString(r);
        return response;
    }

    @Nullable
    private FunctionRepositoryResponseParams.ShortFunctionConfig toShortFunctionConfig(String functionCode) {
        FunctionRepositoryResponseParams.ShortFunctionConfig f = shortFunctionConfigCache.computeIfAbsent(functionCode, this::loadShortFunctionConfig);
        return f;
    }

    @Nullable
    private FunctionRepositoryResponseParams.ShortFunctionConfig loadShortFunctionConfig(String functionCode) {
        Function f = functionRepository.findByCode(functionCode);
        if (f==null) {
            return null;
        }
        FunctionConfigYaml params = f.getFunctionConfigYaml();
        return new FunctionRepositoryResponseParams.ShortFunctionConfig(functionCode, params.function.sourcing, params.function.git);
    }

    private static void registerReadyFunctionCodesOnProcessor(FunctionRepositoryRequestParams p) {
        if (p.processorId==null || CollectionUtils.isEmpty(p.functionCodes)) {
            return;
        }
        writeLock.lock();
        try {
            for (String functionCode : p.functionCodes) {
                if (!activeFunctions.contains(functionCode)) {
                    continue;
                }
                functionReadiness.computeIfAbsent(functionCode, (o)-> new Processors()).ids.add(p.processorId);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public static void registerReadyFunctionCodesOnProcessor(String functionCode, Long processorId, boolean force) {
        writeLock.lock();
        try {
            if (!force && !activeFunctions.contains(functionCode)) {
                return;
            }
            functionReadiness.computeIfAbsent(functionCode, (o)-> new Processors()).ids.add(processorId);
        } finally {
            writeLock.unlock();
        }
    }

    public static boolean isProcessorReady(String funcCode, Long processorId) {
        Processors processors = functionReadiness.get(funcCode);
        if (processors != null) {
            processors.mills = System.currentTimeMillis();
            return processors.contains(processorId);
        }
        return false;
    }

    public static boolean notAllFunctionsReady(Long processorId, TaskParamsYaml taskParamYaml) {
        if (!isProcessorReady(taskParamYaml.task.function.code, processorId)) {
            return true;
        }
        for (TaskParamsYaml.FunctionConfig preFunction : taskParamYaml.task.preFunctions) {
            if (!isProcessorReady(preFunction.code, processorId)) {
                return true;
            }
        }
        for (TaskParamsYaml.FunctionConfig postFunction : taskParamYaml.task.postFunctions) {
            if (!isProcessorReady(postFunction.code, processorId)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Async
    @EventListener
    public void activateFunctions(SourceCodeParamsYaml sc) {
        Set<String> funcCodes = collectFunctionCodes(sc);
        registerCodes(funcCodes, false);
    }

    private static void registerCodes(Set<String> funcCodes, boolean clean) {
        for (String funcCode : funcCodes) {
            writeLock.lock();
            try {
                activeFunctions.add(funcCode);
                functionReadiness.computeIfAbsent(funcCode, (o)->new Processors());
            } finally {
                writeLock.unlock();
            }
        }
        if (clean) {
            List<String> forDeletion = new ArrayList<>(100);
            readLock.lock();
            try {
                for (String activeFunction : activeFunctions) {
                    if (!funcCodes.contains(activeFunction)) {
                        forDeletion.add(activeFunction);
                    }
                }
            } finally {
                readLock.unlock();
            }
            if (!forDeletion.isEmpty()) {
                writeLock.lock();
                try {
                    for (String funcCode : forDeletion) {
                        activeFunctions.remove(funcCode);
                        functionReadiness.remove(funcCode);
                    }
                } finally {
                    writeLock.unlock();
                }
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Async
    @EventListener
    public void registerFunctionCodesForStartedExecContext(RegisterFunctionCodesForStartedExecContextEvent event) {
        final SourceCodeParamsYaml params;
        if (event.sc()!=null) {
            params = event.sc();
        }
        else {
            final SourceCodeImpl sc = sourceCodeRepository.findByIdExecContextId(event.execContextId());
            if (sc==null) {
                return;
            }
            SourceCodeStoredParamsYaml scspy = sc.getSourceCodeStoredParamsYaml();
            params = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
        }
        Set<String> funcCodes = collectFunctionCodes(params);
        registerCodes(funcCodes, false);
    }

    private static Set<String> collectFunctionCodes(SourceCodeParamsYaml sc) {
        Set<String> codes = new HashSet<>();
        if (sc.source.processes!=null) {
            for (SourceCodeParamsYaml.Process process : sc.source.processes) {
                collectFunctionCodesForProcess(codes, process);
            }
        }
        return codes;
    }

    public static void collectFunctionCodesForProcess(Set<String> codes, SourceCodeParamsYaml.Process process) {
        if (process.function !=null && process.function.context==EnumsApi.FunctionExecContext.external) {
            codes.add(process.function.code);
        }
        if (process.preFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.preFunctions) {
                if (snDef.context==EnumsApi.FunctionExecContext.external) {
                    codes.add(snDef.code);
                }
            }
        }
        if (process.postFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.postFunctions) {
                if (snDef.context==EnumsApi.FunctionExecContext.external) {
                    codes.add(snDef.code);
                }
            }
        }

        if (process.subProcesses!=null) {
            for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {
                collectFunctionCodesForProcess(codes, subProcess);
            }
        }
    }

    public void updateActiveFunctions(Set<String> funcCodes) {

    }

    public void collectActiveFunctionCodes() {
        List<Long> sourceCodeIds = execContextRepository.findAllSourceCodeIdsByExecState(EnumsApi.ExecContextState.STARTED.code);
        Set<String> funcCodes = new HashSet<>();
        for (Long sourceCodeId : sourceCodeIds) {
            SourceCodeImpl sc = sourceCodeRepository.findByIdNullable(sourceCodeId);
            if (sc==null) {
                continue;
            }
            var scspy = sc.getSourceCodeStoredParamsYaml();
            SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
            funcCodes.addAll(collectFunctionCodes(scpy));
        }
        registerCodes(funcCodes, true);
    }

}
