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

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Series;
import ai.metaheuristic.ai.dispatcher.data.ExperimentResultData;
import ai.metaheuristic.ai.dispatcher.data.SeriesData;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentResultRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SeriesRepository;
import ai.metaheuristic.ai.yaml.series.SeriesParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final SeriesService seriesService;
    private final SeriesRepository seriesRepository;
    private final ExperimentResultRepository experimentResultRepository;

    public SeriesData.SeriesesResult getSerieses(Pageable pageable) {
        pageable = PageUtils.fixPageSize(40, pageable);
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

    public SeriesData.SeriesResult getSeries(Long seriesId, DispatcherContext context) {
        final Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            String errorMessage = "#286.040 series wasn't found, seriesId: " + seriesId;
            return new SeriesData.SeriesResult(errorMessage);
        }
        return new SeriesData.SeriesResult(new SeriesData.SimpleSeries(series.id, series.name));
    }

    private final Map<Long, SeriesParamsYaml> seriesParamsYamlMap = new HashMap<>();
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public SeriesData.SeriesShortDetails getSeriesDetails(Long seriesId) {
        final Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            String errorMessage = "#286.040 series wasn't found, seriesId: " + seriesId;
            return new SeriesData.SeriesShortDetails(errorMessage);
        }
        final SeriesParamsYaml seriesParamsYaml = seriesParamsYamlMap.computeIfAbsent(seriesId, (id) -> series.getSeriesParamsYaml());
        final SeriesData.SeriesShortDetails seriesDetails = new SeriesData.SeriesShortDetails(series.id, series.name);
        for (Map.Entry<EnumsApi.Fitting, Integer> entry : seriesParamsYaml.fittingCounts.entrySet()) {
            switch(entry.getKey()) {
                case UNKNOWN:
                    seriesDetails.unknownFitting = entry.getValue();
                    break;
                case UNDERFITTING:
                    seriesDetails.underFitting = entry.getValue();
                    break;
                case NORMAL:
                    seriesDetails.normalFitting = entry.getValue();
                    break;
                case OVERFITTING:
                    seriesDetails.overFitting = entry.getValue();
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + entry.getKey());
            }
        }

        return seriesDetails;
    }

    private static SeriesData.SeriesDetail to(SeriesParamsYaml.ExperimentPart part) {
        SeriesData.SeriesDetail detail = new SeriesData.SeriesDetail();
        detail.fitting = part.fitting;
        detail.hyperParams = part.hyperParams;
        detail.metrics.values.putAll(part.metrics.values);
        detail.variables.addAll(part.variables);
        return detail;
    }

    public OperationStatusRest processSeriesImport(Long seriesId, Long experimentResultId) {
        final Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            String errorMessage = "#286.060 series wasn't found, seriesId: " + seriesId;
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, errorMessage);
        }
        try {
            seriesParamsYamlMap.remove(seriesId);
            return seriesService.processSeriesImport(seriesId, experimentResultId);
        }
        catch (Throwable th) {
            String es = "#286.080 error while importing an experiment result. error: " + th.getMessage();
            log.error(es, th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
    }

    public SeriesData.SeriesImportDetails getSeriesImportDetails(Long seriesId) {
        final Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            String errorMessage = "#286.060 series wasn't found, seriesId: " + seriesId;
            log.error(errorMessage);
            return new SeriesData.SeriesImportDetails(errorMessage);
        }
        try {
            SeriesData.SeriesImportDetails details = new SeriesData.SeriesImportDetails(seriesId, series.name);

            final SeriesParamsYaml spy = seriesParamsYamlMap.computeIfAbsent(seriesId, (id) -> series.getSeriesParamsYaml());

            List<Object[]> resultNames = experimentResultRepository.getResultNames();
            for (Object[] obj : resultNames) {
                long experimentResultId = ((Number) obj[0]).longValue();
                String name = obj[1].toString();

                details.importDetails.add(
                        new SeriesData.ImportDetail(
                                new ExperimentResultData.SimpleExperimentResult(experimentResultId, name),
                                spy.experimentResults.contains(name)));
            }
            return details;
        }
        catch (Throwable th) {
            String es = "#286.080 error while importing an experiment result. error: " + th.getMessage();
            log.error(es, th);
            return new SeriesData.SeriesImportDetails(es);
        }
    }

    public SeriesData.SeriesFittingDetails getSeriesFittingDetails(Long seriesId, String fittingStr) {
        final Series series = seriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            String errorMessage = "#286.100 series wasn't found, seriesId: " + seriesId;
            log.error(errorMessage);
            return new SeriesData.SeriesFittingDetails(errorMessage);
        }

        try {
            EnumsApi.Fitting fitting = EnumsApi.Fitting.of(fittingStr.toUpperCase());

            SeriesData.SeriesFittingDetails details = new SeriesData.SeriesFittingDetails(seriesId, series.name, fitting);

            final SeriesParamsYaml spy = seriesParamsYamlMap.computeIfAbsent(seriesId, (id) -> series.getSeriesParamsYaml());


            String metricsCode = spy.parts.stream()
                    .filter(o->!o.metrics.values.isEmpty())
                    .map(o->o.metrics.values.entrySet().stream().findFirst().map(Map.Entry::getKey).orElse(null))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            if (metricsCode==null) {
                String errorMessage = "#286.110 Code of metrics wasn't found";
                log.error(errorMessage);
                return new SeriesData.SeriesFittingDetails(errorMessage);
            }

            List<SeriesParamsYaml.ExperimentPart> parts = spy.parts.stream().filter(o->o.fitting==fitting)
                    .sorted((o1, o2) -> o2.metrics.values.get(metricsCode).compareTo(o1.metrics.values.get(metricsCode)))
                    .collect(Collectors.toList());

            final List<SeriesParamsYaml.ExperimentPart> top20 = parts.stream()
                    .sorted((o1, o2) -> o2.metrics.values.get(metricsCode).compareTo(o1.metrics.values.get(metricsCode)))
                    .limit(20)
                    .collect(Collectors.toList());

            details.metricsInfos.metricsCode = metricsCode;
            details.all = get(parts, metricsCode);
            details.top20 = get(top20, metricsCode);

            top20.stream()
                    .map(p->new SeriesData.MetricsInfo(p.metrics.values.get(metricsCode).toString(),
                            p.hyperParams.entrySet().stream()
                                    .map(h->""+h.getKey()+":"+h.getValue())
                                    .collect(Collectors.joining(", ")),
                            String.join(", ", p.variables)
                    ))
                    .collect(Collectors.toCollection(() -> details.metricsInfos.metricsInfos));

            return details;
        }
        catch (Throwable th) {
            String es = "#286.120 error while importing an experiment result. error: " + th.getMessage();
            log.error(es, th);
            return new SeriesData.SeriesFittingDetails(es);
        }
    }

    private SeriesData.HyperParamsAndFeatures get(List<SeriesParamsYaml.ExperimentPart> parts, String metricsCode) {
        SeriesData.HyperParamsAndFeatures hpaf = new SeriesData.HyperParamsAndFeatures();

        Map<String, Map<String, AtomicInteger>> hyperParamsOccurCount = new HashMap<>();
        Map<String, AtomicInteger> featureOccurCount = new HashMap<>();
        for (SeriesParamsYaml.ExperimentPart part : parts) {
            for (Map.Entry<String, String> entry : part.hyperParams.entrySet()) {
                hyperParamsOccurCount.computeIfAbsent(entry.getKey(), (k)->new HashMap<>())
                        .computeIfAbsent(entry.getValue(), (k)->new AtomicInteger())
                        .incrementAndGet();
            }
            String f = String.join(", ", part.variables);
            featureOccurCount.computeIfAbsent(f, (k)->new AtomicInteger()).incrementAndGet();
        }

        for (Map.Entry<String, Map<String, AtomicInteger>> entry : hyperParamsOccurCount.entrySet()) {
            hpaf.hyperParams.add(
                    new SeriesData.OccurCount(
                            entry.getKey(),
                            entry.getValue().entrySet().stream()
                                    .sorted((o, o1)->Integer.compare(o1.getValue().get(), o.getValue().get()))
                                    .map(o->""+o.getKey()+":"+o.getValue())
                                    .collect(Collectors.joining(","))
                    ));
        }
        featureOccurCount.entrySet().stream()
                .map(entry -> new SeriesData.OccurCount(entry.getKey(), entry.getValue().toString()))
                .sorted((o1, o2)->Integer.compare(Integer.parseInt(o2.counts), Integer.parseInt(o1.counts) ))
                .forEach(hpaf.features::add);

        return hpaf;

    }
}
