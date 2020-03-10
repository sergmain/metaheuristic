/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.commons.yaml.task_ml;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class TaskMachineLearningYamlUtilsV1
        extends AbstractParamsYamlUtils<TaskMachineLearningYamlV1, TaskMachineLearningYamlV2, TaskMachineLearningYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskMachineLearningYamlV1.class);
    }

    @NonNull
    @Override
    public TaskMachineLearningYamlV2 upgradeTo(@NonNull TaskMachineLearningYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        TaskMachineLearningYamlV2 trg = new TaskMachineLearningYamlV2();
//        MetricValues m = MetricsUtils.getMetricValues(src.metrics);

        trg.metrics = new TaskMachineLearningYamlV2.MetricsV2(src.status, src.error, src.metrics);
        trg.fitting = EnumsApi.Fitting.NORMAL;
        trg.checkIntegrity();
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public TaskMachineLearningYamlUtilsV2 nextUtil() {
        return (TaskMachineLearningYamlUtilsV2) TaskMachineLearningYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(TaskMachineLearningYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public TaskMachineLearningYamlV1 to(String s) {
        if (S.b(s)) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final TaskMachineLearningYamlV1 p = getYaml().load(s);
        return p;
    }

}
