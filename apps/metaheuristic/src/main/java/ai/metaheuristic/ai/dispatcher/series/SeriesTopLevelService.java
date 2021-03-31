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

package ai.metaheuristic.ai.dispatcher.series;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExperimentResultData;
import ai.metaheuristic.ai.dispatcher.data.SeriesData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherCacheRemoveSourceCodeEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentResultRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SeriesRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 3/30/2021
 * Time: 2:51 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class SeriesTopLevelService {

    private final Globals globals;
    private final SeriesService seriesService;
    private final SeriesRepository seriesRepository;
    private final ExperimentResultRepository experimentResultRepository;

    public SeriesData.SeriesesResult getSerieses(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(40, pageable);
        SeriesData.SeriesesResult result = new SeriesData.SeriesesResult();
        final Slice<Object[]> serieses = seriesRepository.findAllByOrderByIdDesc(pageable);

        List<SeriesData.SeriesResult> seriesResults = new ArrayList<>();
        for (Object[] obj : serieses) {
            long seriesId = ((Number)obj[0]).longValue();
            String name = obj[1].toString();
            seriesResults.add(new SeriesData.SeriesResult(new SeriesData.SimpleSeries(seriesId, name)));
        }

        result.items = new PageImpl<>(seriesResults, pageable, seriesResults.size() + (serieses.hasNext() ? 1 : 0) );
        return result;
    }

    public List<ExperimentResultData.SimpleExperimentResult> getExperimentResults() {
        List<ExperimentResultData.SimpleExperimentResult> results = new ArrayList<>();
        List<Object[]> objs = experimentResultRepository.getResultNames();
        for (Object[] obj : objs) {
            long id = ((Number)obj[0]).longValue();
            String name = obj[1].toString();

            results.add(new ExperimentResultData.SimpleExperimentResult(id, name));
        }
        return results;
    }

    public OperationStatusRest addSeriesCommit(String name, DispatcherContext context) {
        if (S.b(name)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#286.020 name of series can be empty");
        }
        seriesService.addSeriesCommit(name);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }


}
