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
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYaml;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

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

    public final DispatcherParamsCache dispatcherParamsCache;

    public void registerSourceCode(SourceCodeImpl sourceCode) {
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);

        registerSpecific(sourceCode, scpy, Consts.MH_EXPERIMENT_RESULT_PROCESSOR, this::registerExperiment);
        registerSpecific(sourceCode, scpy, Consts.MH_BATCH_RESULT_PROCESSOR, this::registerBatch);
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

    public void registerExperiment(String uid) {
        updateParams((dpy) -> {
            if (dpy.experiments.contains(uid)) {
                return;
            }
            dpy.experiments.add(uid);
        });
    }

    public void unregisterExperiment(String uid) {
        updateParams((dpy) -> {
            dpy.experiments.remove(uid);
        });
    }

    public void registerBatch(String uid) {
        updateParams((dpy) -> {
            if (dpy.batches.contains(uid)) {
                return;
            }
            dpy.batches.add(uid);
        });
    }

    public void unregisterBatch(String uid) {
        updateParams((dpy) -> {
            dpy.batches.remove(uid);
        });
    }

    private void updateParams(Consumer<DispatcherParamsYaml> consumer) {
        synchronized(this) {
            Dispatcher d = dispatcherParamsCache.find();
            DispatcherParamsYaml dpy = d.getDispatcherParamsYaml();
            consumer.accept(dpy);
            d.updateParams(dpy);
            dispatcherParamsCache.save(d);
        }
    }
}
