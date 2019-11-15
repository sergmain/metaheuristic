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

import ai.metaheuristic.api.data.task_ml.TaskMachineLearningYaml;
import ai.metaheuristic.api.data.task_ml.TaskMachineLearningYamlV1;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class TaskMachineLearningYamlUtilsV1
        extends AbstractParamsYamlUtils<TaskMachineLearningYamlV1, TaskMachineLearningYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    public Yaml getYaml() {
        return YamlUtils.init(TaskMachineLearningYamlV1.class);
    }

    @Override
    public TaskMachineLearningYaml upgradeTo(TaskMachineLearningYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        TaskMachineLearningYaml trg = new TaskMachineLearningYaml();
        trg.metrics = src.metrics;
        trg.predicted = src.predicted;
        trg.overfitted = src.overfitted;
        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(TaskMachineLearningYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    public TaskMachineLearningYamlV1 to(String s) {
        if (S.b(s)) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final TaskMachineLearningYamlV1 p = getYaml().load(s);
        return p;
    }

}
