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

package ai.metaheuristic.ai.launchpad.atlas;

import ai.metaheuristic.ai.launchpad.experiment.ExperimentUtils;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.launchpad.ExecContext;
import ai.metaheuristic.api.launchpad.SourceCode;
import ai.metaheuristic.api.launchpad.Task;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ai.metaheuristic.ai.launchpad.beans.*;
import org.springframework.beans.BeanUtils;

import java.util.*;

@Data
@NoArgsConstructor
public class ExperimentStoredToAtlas {

    @JsonIgnore
    public ExperimentParamsYaml.ExperimentFeature getFeature(Long featureId) {
        return experiment.getExperimentParamsYaml().processing.features.stream().filter(o -> Objects.equals(o.id, featureId)).findAny().orElse(null);
    }

    @JsonIgnore
    public TaskOnShelf getTask(Long taskId) {
        return tasks.stream().filter(o -> Objects.equals(o.id, taskId)).findAny().orElse(null);
    }

    @JsonIgnore
    public ExperimentParamsYaml.ExperimentTaskFeature getExperimentTaskFeature(Long taskId) {
        return experiment.getExperimentParamsYaml().processing.taskFeatures
                .stream()
                .filter(o -> o.taskId.equals(taskId))
                .findFirst().orElse(null);
    }

    @SuppressWarnings("Duplicates")
    @JsonIgnore
    public Map<String, Map<String, Integer>> getHyperParamsAsMap(boolean isFull) {
        final Map<String, Map<String, Integer>> paramByIndex = new LinkedHashMap<>();
        for (ExperimentParamsYaml.HyperParam hyperParam : experiment.getExperimentParamsYaml().experimentYaml.getHyperParams()) {
            ExperimentUtils.NumberOfVariants ofVariants = ExperimentUtils.getNumberOfVariants(hyperParam.getValues() );
            Map<String, Integer> map = new LinkedHashMap<>();
            paramByIndex.put(hyperParam.getKey(), map);
            for (int i = 0; i <ofVariants.values.size(); i++) {
                String value = ofVariants.values.get(i);


                map.put(isFull ? hyperParam.getKey()+'-'+value : value , i);
            }
        }
        return paramByIndex;
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(value = {"version", "clean"})
    @ToString(callSuper = true)
    public static class SourceCodeOnShelf extends SourceCodeImpl {
        public SourceCodeOnShelf(SourceCode sourceCode) {
            BeanUtils.copyProperties(sourceCode, this);
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(value = {"version"})
    @ToString(callSuper = true)
    public static class ExecContextOnShelf extends ExecContextImpl {
        public ExecContextOnShelf(ExecContext execContext) {
            BeanUtils.copyProperties(execContext, this);
        }
    }


    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(value = {"version", "hyperParams", "hyperParamsAsMap"})
    @ToString(callSuper = true)
    public static class ExperimentOnShelf extends Experiment {
        public ExperimentOnShelf(Experiment experiment) {
            BeanUtils.copyProperties(experiment, this);
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(value = {"version"})
    @ToString(callSuper = true)
    public static class TaskOnShelf extends TaskImpl {
        public TaskOnShelf(Task task) {
            BeanUtils.copyProperties(task, this);
        }
    }

    public SourceCodeOnShelf sourceCode;
    public ExecContextOnShelf execContext;
    public ExperimentOnShelf experiment;
    public List<TaskOnShelf> tasks = new ArrayList<>();

    public ExperimentStoredToAtlas(SourceCode sourceCode, ExecContext execContext, Experiment experiment, List<Task> tasks) {

        this.sourceCode = new SourceCodeOnShelf(sourceCode);
        this.execContext = new ExecContextOnShelf(execContext);
        this.experiment = new ExperimentOnShelf(experiment);

        if (CollectionUtils.isNotEmpty(tasks)) {
            for (Task task : tasks) {
                this.tasks.add( new TaskOnShelf(task));
            }
        }
    }
}
