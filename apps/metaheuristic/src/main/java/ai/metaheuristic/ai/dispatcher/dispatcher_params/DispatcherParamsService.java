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

package ai.metaheuristic.ai.dispatcher.dispatcher_params;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Dispatcher;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.event.DispatcherCacheRemoveSourceCodeEvent;
import ai.metaheuristic.ai.dispatcher.repositories.DispatcherParamsRepository;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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

    private final DispatcherParamsRepository dispatcherParamsRepository;

    @Async
    @EventListener
    public void handleAsync(final DispatcherCacheRemoveSourceCodeEvent event) {
        unregisterSourceCode(event.sourceCodeUid);
    }

    public void registerSourceCode(SourceCodeImpl sourceCode) {
        unregisterSourceCode(sourceCode.uid);

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);

        registerSpecific(sourceCode, scpy, Consts.MH_EXPERIMENT_RESULT_PROCESSOR, this::registerExperiment);
        registerSpecific(sourceCode, scpy, Consts.MH_BATCH_RESULT_PROCESSOR_FUNCTION, this::registerBatch);
    }

    private void registerSpecific(SourceCodeImpl sourceCode, SourceCodeParamsYaml scpy, String functionCode, Consumer<String> consumer) {
        SourceCodeParamsYaml.Process p = findProcessForFunction(scpy, functionCode);
        if (p==null) {
            return;
        }
        consumer.accept(sourceCode.uid);
    }

    @Nullable
    private SourceCodeParamsYaml.Process findProcessForFunction(SourceCodeParamsYaml scpy, String functionCode) {
        for (SourceCodeParamsYaml.Process process : scpy.source.processes) {
            SourceCodeParamsYaml.Process result = findProcessForFunction(process, functionCode);
            if (result!=null) {
                return result;
            }
        }
        return null;
    }

    @Nullable
    private SourceCodeParamsYaml.Process findProcessForFunction(SourceCodeParamsYaml.Process process, String functionCode) {
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
    }

    private void registerExperiment(String uid) {
        updateParams((dpy) -> {
            if (dpy.experiments.contains(uid)) {
                return;
            }
            dpy.experiments.add(uid);
        });
    }

    public void unregisterSourceCode(String uid) {
        if (getExperiments().contains(uid)) {
            unregisterExperiment(uid);
        }
        else if(getBatches().contains(uid)) {
            unregisterBatch(uid);
        }
    }

    public void unregisterExperiment(String uid) {
        updateParams((dpy) -> dpy.experiments.remove(uid));
    }

    public void unregisterBatch(String uid) {
        updateParams((dpy) -> dpy.batches.remove(uid));
    }

    private void registerBatch(String uid) {
        updateParams((dpy) -> {
            if (dpy.batches.contains(uid)) {
                return;
            }
            dpy.batches.add(uid);
        });
    }

    private void updateParams(Consumer<DispatcherParamsYaml> consumer) {
        synchronized(this) {
            Dispatcher d = find();
            DispatcherParamsYaml dpy = d.getDispatcherParamsYaml();
            consumer.accept(dpy);
            d.updateParams(dpy);
            save(d);
        }
    }

    public List<String> getExperiments() {
        find();
        return Objects.requireNonNull(dispatcherParamsYaml).experiments;
    }

    public List<String> getBatches() {
        find();
        return Objects.requireNonNull(dispatcherParamsYaml).batches;
    }

    @Nullable
    private Dispatcher dispatcherCacheValue = null;

    @Nullable
    private DispatcherParamsYaml dispatcherParamsYaml = null;

    private void save(Dispatcher dispatcher) {
        if (!Consts.DISPATCHERS_CACHE.equals(dispatcher.code)) {
            throw new IllegalStateException("(!Consts.DISPATCHERS_CACHE.equals(dispatcher.code))");
        }
        if (S.b(dispatcher.params)) {
            throw new IllegalStateException("(S.b(dispatcher.params))");
        }
/*
        // todo 2020-04-24 this isn't working. need better solution to reduce number of db interaction
        if (dispatcherCacheValue!=null && dispatcher.params.equals(dispatcherCacheValue.params)) {
            log.info("Dispatcher params is the same. Won't be saved  to db");
            return;
        }
*/
        try {
            dispatcherCacheValue = dispatcherParamsRepository.save(dispatcher);
            dispatcherParamsYaml = DispatcherParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherCacheValue.params);
        } catch (Throwable th) {
            log.error("Error while saving DispatcherParams", th);
            dispatcherCacheValue = null;
            dispatcherParamsYaml = null;
        }
    }

    private synchronized Dispatcher find() {
        if (dispatcherCacheValue==null) {
            dispatcherCacheValue = dispatcherParamsRepository.findByCode(Consts.DISPATCHERS_CACHE);
            if (dispatcherCacheValue==null) {

                Dispatcher entity = new Dispatcher();
                entity.code = Consts.DISPATCHERS_CACHE;
                dispatcherParamsYaml = new DispatcherParamsYaml();
                entity.params = DispatcherParamsYamlUtils.BASE_YAML_UTILS.toString(dispatcherParamsYaml);
                dispatcherCacheValue = dispatcherParamsRepository.save(entity);
            }
            else {
                dispatcherParamsYaml = DispatcherParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherCacheValue.params);
            }
        }
        return dispatcherCacheValue;
    }

}
