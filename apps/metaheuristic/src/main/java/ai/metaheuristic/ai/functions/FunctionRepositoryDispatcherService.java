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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParamsUtils;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParamsUtils;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
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
    private final ApplicationEventPublisher eventPublisher;

    // private final ApplicationEventPublisher eventPublisher;

    // key - function code, value - list of processorIds
    private final Map<String, Set<Long>> functionReadiness = new HashMap<>();
    private final Set<String> activeFunctions = new HashSet<>();

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public Set<String> getActiveFunctionCode(@Nullable Long processorId) {
        readLock.lock();
        try {
            if (processorId==null) {
                return new HashSet<>(activeFunctions);
            }
            else {
                return activeFunctions.stream().filter(c->{
                    Set<Long> ids = functionReadiness.get(c);
                    return ids==null || !ids.contains(processorId);
                }).collect(Collectors.toSet());
            }
        } finally {
            readLock.unlock();
        }
    }

    public String processRequest(String data, String remoteAddr) {
        FunctionRepositoryRequestParams p = FunctionRepositoryRequestParamsUtils.UTILS.to(data);
        FunctionRepositoryResponseParams r = new FunctionRepositoryResponseParams();
        registerReadyFunctionCodesOnProcessor(p);

        r.functionCodes = getActiveFunctionCode(p.processorId);

        String response = FunctionRepositoryResponseParamsUtils.UTILS.toString(r);
        return response;
    }

    private void registerReadyFunctionCodesOnProcessor(FunctionRepositoryRequestParams p) {
        if (p.processorId==null || CollectionUtils.isEmpty(p.functionCodes)) {
            return;
        }
        writeLock.lock();
        try {
            for (String functionCode : p.functionCodes) {
                if (!activeFunctions.contains(functionCode)) {
                    continue;
                }
                functionReadiness.computeIfAbsent(functionCode, (o)-> new HashSet<>()).add(p.processorId);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public boolean isProcessorReady(String funcCode, Long processorId) {
        Set<Long> set = functionReadiness.get(funcCode);
        return set != null && set.contains(processorId);
    }

    public boolean notAllFunctionsReady(Long processorId, TaskParamsYaml taskParamYaml) {
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

    @Async
    @EventListener
    public void activateFunctions(SourceCodeParamsYaml sc) {
        Set<String> funcCodes = collectFunctionCodes(sc);
        for (String funcCode : funcCodes) {
            activeFunctions.add(funcCode);
            functionReadiness.computeIfAbsent(funcCode, (o)->new HashSet<>());
        }
    }

//    @Async
//    @EventListener
//    public void deactivateFunctions(SourceCodeParamsYaml sc) {
//    }

    private Set<String> collectFunctionCodes(SourceCodeParamsYaml sc) {

        return Set.of();
    }

    public void updateActiveFunctions(Set<String> funcCodes) {

    }

    public void collectActiveFunctionCodes() {
        List<Long> execContextIds = execContextRepository.findIdsByExecState(EnumsApi.ExecContextState.STARTED.code);
        Set<String> funcCodes = new HashSet<>();
        for (Long execContextId : execContextIds) {
            ExecContextImpl ec = execContextRepository.findByIdNullable(execContextId);
            if (ec==null) {
                continue;
            }
            SourceCodeImpl sc = sourceCodeRepository.findByIdNullable(ec.sourceCodeId);
            if (sc==null) {
                continue;
            }
            var scspy = sc.getSourceCodeStoredParamsYaml();
            SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
            funcCodes.addAll(collectFunctionCodes(scpy));
        }
    }

}
