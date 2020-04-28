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
package ai.metaheuristic.ai.dispatcher.experiment;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.api.data.experiment.BaseMetricElement;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.math.BigDecimal;

@SuppressWarnings("DuplicatedCode")
@Service
@EnableTransactionManagement
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExperimentService {

    private final ExecContextCache execContextCache;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;

    @Async
    @EventListener
    public void handleAsync(DispatcherInternalEvent.DeleteExecContextEvent event) {
        deleteExperiment(event.execContextId);
    }

    public static int compareMetricElement(BaseMetricElement o2, BaseMetricElement o1) {
        for (int i = 0; i < Math.min(o1.getValues().size(), o2.getValues().size()); i++) {
            final BigDecimal holder1 = o1.getValues().get(i);
            if (holder1 == null) {
                return -1;
            }
            final BigDecimal holder2 = o2.getValues().get(i);
            if (holder2 == null) {
                return -1;
            }
            int c = ObjectUtils.compare(holder1, holder2);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(o1.getValues().size(), o2.getValues().size());
    }

    public static ExperimentApiData.ExperimentData asExperimentData(Experiment e) {
        ExperimentParamsYaml params = e.getExperimentParamsYaml();

        ExperimentApiData.ExperimentData ed = new ExperimentApiData.ExperimentData();
        ed.id = e.id;
        ed.code = e.code;
        ed.execContextId = e.execContextId;
        ed.name = params.name;
        ed.description = params.description;
        ed.createdOn = params.createdOn;
        ed.numberOfTask = 0;

        return ed;
    }

    @Nullable
    public ExperimentApiData.ExperimentData asExperimentDataShort(Experiment e) {
        ExperimentParamsYaml params = e.getExperimentParamsYaml();
        ExecContextImpl ec = execContextCache.findById(e.execContextId);
        if (ec==null) {
            log.warn("ExecContext wasn't found for id #"+e.execContextId);
            return null;
        }

        ExperimentApiData.ExperimentData ed = new ExperimentApiData.ExperimentData();
        ed.id = e.id;
        ed.state = ec.state;
        ed.version = e.version;
        ed.code = e.code;
        ed.execContextId = e.execContextId;
        ed.name = params.name;
        ed.description = params.description;
        ed.createdOn = params.createdOn;
        ed.numberOfTask = 0;

        return ed;
    }

    private void deleteExperiment(Long execContextId) {
        Long id = experimentRepository.findIdByExecContextId(execContextId);
        if (id==null) {
            return;
        }
        experimentCache.deleteById(id);
    }
}
