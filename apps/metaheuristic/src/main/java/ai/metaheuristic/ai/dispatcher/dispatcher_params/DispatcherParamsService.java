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

package ai.metaheuristic.ai.dispatcher.dispatcher_params;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Dispatcher;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.event.DispatcherCacheCheckingEvent;
import ai.metaheuristic.ai.dispatcher.event.DispatcherCacheRemoveSourceCodeEvent;
import ai.metaheuristic.ai.dispatcher.repositories.DispatcherParamsRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 4/19/2020
 * Time: 5:18 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class DispatcherParamsService {

    public final ApplicationEventPublisher eventPublisher;
    public final DispatcherParamsRepository dispatcherParamsRepository;
    public final SourceCodeRepository sourceCodeRepository;
    public final SourceCodeCache sourceCodeCache;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    @Nullable
    private Dispatcher dispatcherCacheValue = null;

    @Nullable
    private DispatcherParamsYaml dispatcherParamsYaml = null;

    @PostConstruct
    @Transactional
    public void checkAndCreateNewDispatcher() {
        Dispatcher dispatcherCacheValue = dispatcherParamsRepository.findByCode(Consts.DISPATCHERS_CACHE);
        if (dispatcherCacheValue==null) {
            Dispatcher entity = new Dispatcher();
            entity.code = Consts.DISPATCHERS_CACHE;
            dispatcherParamsYaml = new DispatcherParamsYaml();
            entity.setParams(DispatcherParamsYamlUtils.BASE_YAML_UTILS.toString(dispatcherParamsYaml));
            dispatcherParamsRepository.save(entity);
        }
        List<Long> sourceCodeIds = sourceCodeRepository.findAllAsIds();
        for (Long sourceCodeId : sourceCodeIds) {
            SourceCodeImpl sc = sourceCodeCache.findById(sourceCodeId);
            if (sc==null) {
                continue;
            }
            registerSourceCode(sc);
        }
    }

    @Async
    @EventListener
    public void handleDispatcherCacheRemoveSourceCodeEvent(final DispatcherCacheRemoveSourceCodeEvent event) {
        unregisterSourceCode(event.sourceCodeUid);
    }

    @Transactional
    public void registerLongRunningExecContext(Long taskId, Long subExecContextId) {
        try {
            final DispatcherParamsYaml.LongRunningExecContext e = new DispatcherParamsYaml.LongRunningExecContext(taskId, subExecContextId);
            writeLock.lock();
            updateParams((dpy) -> {
                if (dpy.longRunnings.stream().anyMatch(o->o.taskId.equals(taskId))) {
                    return Boolean.FALSE;
                }
                dpy.longRunnings.add(e);
                return Boolean.TRUE;
            });
        } finally {
            writeLock.unlock();
        }
    }

    @Transactional
    public Void deRegisterLongRunningExecContext(Long taskId) {
        try {
            writeLock.lock();
            updateParams((dpy) -> {
                for (int i = 0; i < dpy.longRunnings.size(); i++) {
                    if (dpy.longRunnings.get(i).taskId.equals(taskId)) {
                        dpy.longRunnings.remove(i);
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            });
            return null;
        } finally {
            writeLock.unlock();
        }
    }

    @Transactional
    public void registerSourceCodes(List<SourceCodeImpl> sourceCodes) {
        try {
            writeLock.lock();
            for (SourceCodeImpl sourceCode : sourceCodes) {
                registerSourceCode(sourceCode);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Transactional
    public void registerSourceCode(SourceCodeImpl sourceCode) {
        try {
            writeLock.lock();

            SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
            SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);

            registerSpecific(sourceCode, scpy, Consts.MH_EXPERIMENT_RESULT_PROCESSOR, this::registerExperiment);
            registerSpecific(sourceCode, scpy, Consts.MH_BATCH_RESULT_PROCESSOR_FUNCTION, this::registerBatch);
        } finally {
            writeLock.unlock();
        }
    }

    private void registerSpecific(SourceCodeImpl sourceCode, SourceCodeParamsYaml scpy, String functionCode, Consumer<String> consumer) {
        try {
            writeLock.lock();
            SourceCodeParamsYaml.Process p = findProcessForFunction(scpy, functionCode);
            if (p==null) {
                return;
            }
            consumer.accept(sourceCode.uid);
        } finally {
            writeLock.unlock();
        }
    }

    @Nullable
    private SourceCodeParamsYaml.Process findProcessForFunction(SourceCodeParamsYaml scpy, String functionCode) {
        try {
            writeLock.lock();
            for (SourceCodeParamsYaml.Process process : scpy.source.processes) {
                SourceCodeParamsYaml.Process result = findProcessForFunction(process, functionCode);
                if (result!=null) {
                    return result;
                }
            }
            return null;
        } finally {
            writeLock.unlock();
        }
    }

    @Nullable
    private SourceCodeParamsYaml.Process findProcessForFunction(SourceCodeParamsYaml.Process process, String functionCode) {
        try {
            writeLock.lock();
            if (process.function.code.equals(functionCode)) {
                return process;
            }
            if (process.subProcesses!=null && process.subProcesses.processes!=null) {
                for (SourceCodeParamsYaml.Process p : process.subProcesses.processes) {
                    SourceCodeParamsYaml.Process result = findProcessForFunction(p, functionCode);
                    if (result!=null) {
                        return result;
                    }
                }
            }
            return null;
        } finally {
            writeLock.unlock();
        }
    }

    private void registerExperiment(String uid) {
        updateParams((dpy) -> {
            if (dpy.experiments.contains(uid)) {
                return Boolean.FALSE;
            }
            dpy.experiments.add(uid);
            return Boolean.TRUE;
        });
    }

    @Transactional
    public void unregisterSourceCode(String uid) {
        try {
            writeLock.lock();
            if (getExperiments().contains(uid)) {
                unregisterExperiment(uid);
            }
            else if(getBatches().contains(uid)) {
                unregisterBatch(uid);
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void unregisterExperiment(String uid) {
        updateParams((dpy) -> dpy.experiments.remove(uid));
    }

    private void unregisterBatch(String uid) {
        updateParams((dpy) -> dpy.batches.remove(uid));
    }

    private void registerBatch(String uid) {
        updateParams((dpy) -> {
            if (dpy.batches.contains(uid)) {
                return Boolean.FALSE;
            }
            dpy.batches.add(uid);
            return Boolean.TRUE;
        });
    }

    private void updateParams(Function<DispatcherParamsYaml, Boolean> consumer) {
        try {
            writeLock.lock();
            Dispatcher d = find();
            if (d==null) {
                throw new IllegalStateException("Dispatcher cache must be initialized at this point");
            }
            DispatcherParamsYaml dpy = d.getDispatcherParamsYaml();
            if (consumer.apply(dpy)) {
                d.updateParams(dpy);
                save(d);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public List<String> getExperiments() {
        find();
        try {
            readLock.lock();
            return dispatcherParamsYaml ==null ? List.of() : new ArrayList<>(dispatcherParamsYaml.experiments);
        } finally {
            readLock.unlock();
        }
    }

    public List<DispatcherParamsYaml.LongRunningExecContext> getLongRunningExecContexts() {
        find();
        try {
            readLock.lock();
            return dispatcherParamsYaml ==null ? List.of() : dispatcherParamsYaml.longRunnings.stream()
                    .sorted((o1, o2)->o2.execContextId.compareTo(o1.execContextId))
                    .collect(Collectors.toList());
        } finally {
            readLock.unlock();
        }
    }

    public List<String> getBatches() {
        find();
        try {
            readLock.lock();
            return dispatcherParamsYaml==null ? List.of() : new ArrayList<>(dispatcherParamsYaml.batches);
        } finally {
            readLock.unlock();
        }
    }

    private void save(Dispatcher dispatcher) {
        TxUtils.checkTxExists();
        try {
            writeLock.lock();
            if (!Consts.DISPATCHERS_CACHE.equals(dispatcher.code)) {
                throw new IllegalStateException("(!Consts.DISPATCHERS_CACHE.equals(dispatcher.code))");
            }
            if (S.b(dispatcher.getParams())) {
                throw new IllegalStateException("(S.b(dispatcher.params))");
            }
            try {
                dispatcherCacheValue = dispatcherParamsRepository.save(dispatcher);
                dispatcherParamsYaml = DispatcherParamsYamlUtils.BASE_YAML_UTILS.to(dispatcher.getParams());
            } catch (Throwable th) {
                log.error("Error while saving DispatcherParams", th);
                dispatcherCacheValue = null;
                dispatcherParamsYaml = null;
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Nullable
    private Dispatcher find() {
        try {
            writeLock.lock();
            if (dispatcherCacheValue==null) {
                dispatcherCacheValue = dispatcherParamsRepository.findByCode(Consts.DISPATCHERS_CACHE);
                if (dispatcherCacheValue==null) {
                    eventPublisher.publishEvent(new DispatcherCacheCheckingEvent());
                    return null;
                }
                else {
                    dispatcherParamsYaml = DispatcherParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherCacheValue.getParams());
                }
            }
            return dispatcherCacheValue;
        } finally {
            writeLock.unlock();
        }
    }

}
