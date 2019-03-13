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

package aiai.ai.launchpad.data;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentFeature;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.utils.SimpleSelectOption;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.ArrayList;
import java.util.List;

public class ExperimentData {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentsResult extends BaseDataClass {
        public Slice<Experiment> items;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentResult extends BaseDataClass {
        public Experiment experiment;

        public ExperimentResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentResult(Experiment experiment) {
            this.experiment = experiment;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentInfo extends BaseDataClass {
        public Experiment experiment;
        public ExperimentExtendedResult experimentResult;

        public ExperimentInfo(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentInfo(Experiment experiment, ExperimentExtendedResult experimentResult) {
            this.experiment = experiment;
            this.experimentResult = experimentResult;
        }
    }

    @Data
    public static class ConsoleResult {
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleConsoleOutput {
            public int exitCode;
            public boolean isOk;
            public String console;
        }
        public final List<SimpleConsoleOutput> items = new ArrayList<>();
    }

    @Data
    public static class ExperimentExtendedResult {
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
        public List<ExperimentFeature> features;
        public FlowInstance flowInstance;
        public Enums.FlowInstanceExecState flowInstanceExecState;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentFeatureProgress extends BaseDataClass {
        public TasksData.TasksResult tasksResult;
        public Experiment experiment;
        public ExperimentFeature experimentFeature;
        public ExperimentExtendedResult experimentResult;
        public ConsoleResult consoleResult;

        public ExperimentFeatureProgress(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ExperimentFeatureProgress(Experiment experiment, ExperimentExtendedResult experimentResult) {
            this.experiment = experiment;
            this.experimentResult = experimentResult;
        }
    }

}
