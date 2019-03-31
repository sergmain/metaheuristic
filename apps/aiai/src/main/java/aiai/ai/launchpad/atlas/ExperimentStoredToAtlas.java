/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.atlas;

import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.utils.CollectionUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.beans.BeanUtils;

import java.util.*;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class ExperimentStoredToAtlas {

    @JsonIgnore
    public ExperimentFeatureOnShelf getFeature(Long featureId) {
        return features.stream().filter(o -> Objects.equals(o.id, featureId)).findAny().orElse(null);
    }

    @JsonIgnore
    public TaskOnShelf getTask(Long taskId) {
        return tasks.stream().filter(o -> Objects.equals(o.id, taskId)).findAny().orElse(null);
    }

    @JsonIgnore
    public ExperimentTaskFeatureOnShelf getExperimentTaskFeature(Long taskId) {
        return taskFeatures
                .stream()
                .filter(o -> o.taskId.equals(taskId))
                .findFirst().orElse(null);
    }

    @SuppressWarnings("Duplicates")
    @JsonIgnore
    public Map<String, Map<String, Integer>> getHyperParamsAsMap(boolean isFull) {
        final Map<String, Map<String, Integer>> paramByIndex = new LinkedHashMap<>();
        for (ExperimentHyperParams hyperParam : getHyperParams()) {
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
    public static class FlowOnShelf extends Flow {
        public FlowOnShelf(Flow flow) {
            BeanUtils.copyProperties(flow, this);
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(value = {"version"})
    @ToString(callSuper = true)
    public static class FlowInstanceOnShelf extends FlowInstance {
        public FlowInstanceOnShelf(FlowInstance flowInstance) {
            BeanUtils.copyProperties(flowInstance, this);
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
    public static class ExperimentFeatureOnShelf extends ExperimentFeature {
        public ExperimentFeatureOnShelf(ExperimentFeature experimentFeature) {
            BeanUtils.copyProperties(experimentFeature, this);
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(value = {"version", "experiment"})
    @ToString(callSuper = true)
    public static class ExperimentHyperParamsOnShelf extends ExperimentHyperParams {
        public ExperimentHyperParamsOnShelf(ExperimentHyperParams experimentHyperParams) {
            BeanUtils.copyProperties(experimentHyperParams, this);
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(value = {"version"})
    @ToString(callSuper = true)
    public static class ExperimentSnippetOnShelf extends ExperimentSnippet {
        public ExperimentSnippetOnShelf(ExperimentSnippet experimentSnippet) {
            BeanUtils.copyProperties(experimentSnippet, this);
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(value = {"version"})
    @ToString(callSuper = true)
    public static class ExperimentTaskFeatureOnShelf extends ExperimentTaskFeature {
        public ExperimentTaskFeatureOnShelf(ExperimentTaskFeature experimentTaskFeature) {
            BeanUtils.copyProperties(experimentTaskFeature, this);
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(value = {"version"})
    @ToString(callSuper = true)
    public static class TaskOnShelf extends Task {
        public TaskOnShelf(Task task) {
            BeanUtils.copyProperties(task, this);
        }
    }

    public FlowOnShelf flow;
    public FlowInstanceOnShelf flowInstance;
    public ExperimentOnShelf experiment;
    public List<ExperimentFeatureOnShelf> features = new ArrayList<>();
    public List<ExperimentHyperParamsOnShelf> hyperParams = new ArrayList<>();
    public List<ExperimentSnippetOnShelf> snippets = new ArrayList<>();
    public List<ExperimentTaskFeatureOnShelf> taskFeatures = new ArrayList<>();
    public List<TaskOnShelf> tasks = new ArrayList<>();

    public ExperimentStoredToAtlas(
            Flow flow, FlowInstance flowInstance, Experiment experiment,
            List<ExperimentFeature> features, List<ExperimentHyperParams> hyperParams,
            List<ExperimentSnippet> snippets, List<ExperimentTaskFeature> taskFeatures,
            List<Task> tasks) {

        this.flow = new FlowOnShelf(flow);
        this.flowInstance = new FlowInstanceOnShelf(flowInstance);
        this.experiment = new ExperimentOnShelf(experiment);

        if (CollectionUtils.isNotEmpty(features)) {
            for (ExperimentFeature feature : features) {
                this.features.add( new ExperimentFeatureOnShelf(feature));
            }
        }
        if (CollectionUtils.isNotEmpty(hyperParams)) {
            for (ExperimentHyperParams params : hyperParams) {
                this.hyperParams.add( new ExperimentHyperParamsOnShelf(params));
            }
        }
        if (CollectionUtils.isNotEmpty(snippets)) {
            for (ExperimentSnippet snippet : snippets) {
                this.snippets.add( new ExperimentSnippetOnShelf(snippet));
            }
        }
        if (CollectionUtils.isNotEmpty(tasks)) {
            for (Task task : tasks) {
                this.tasks.add( new TaskOnShelf(task));
            }
        }
        if (CollectionUtils.isNotEmpty(taskFeatures)) {
            for (ExperimentTaskFeature taskFeature : taskFeatures) {
                this.taskFeatures.add( new ExperimentTaskFeatureOnShelf(taskFeature));
            }
        }
    }
}
