package aiai.ai.launchpad.experiment;

import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.yaml.metrics.MetricValues;
import aiai.ai.yaml.metrics.MetricsUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@Profile("launchpad")
@Slf4j
public class MetricsMaxValueCollector {

    private final TaskRepository taskRepository;

    public MetricsMaxValueCollector(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public double calcMaxValueForMetrics(long featureId) {

        try (Stream<Object[]> stream = taskRepository.findMetricsByExperimentFeatureId(featureId) ) {
            //noinspection UnnecessaryLocalVariable
            double value = stream
                    .map(o -> {
                        MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to((String)o[1]) );
                        if (metricValues==null) {
                            return null;
                        }
                        String metricKey=null;
                        //noinspection LoopStatementThatDoesntLoop
                        for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                            metricKey = entry.getKey();
                            break;
                        }
                        if (metricKey==null) {
                            return null;
                        }
                        return metricValues.values.get(metricKey).doubleValue();
                    }).filter(Objects::nonNull).max(Double::compareTo).orElse(0.0);
            return value;
        }
    }
}
