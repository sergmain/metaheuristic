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

package ai.metaheuristic.ai.yaml.communication.processor;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Communication file which is transferred from a Processor to Dispatcher
 *
 * @author Serge
 * Date: 8/29/2019
 * Time: 6:00 PM
 */
@Data
@NoArgsConstructor
public class ProcessorCommParamsYaml implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    // always report info about functions
    public FunctionDownloadStatus functionDownloadStatus = new FunctionDownloadStatus();
    public ProcessorCommContext processorCommContext;
    public RequestProcessorId requestProcessorId;
    public ReportProcessorStatus reportProcessorStatus;
    public ReportProcessorTaskStatus reportProcessorTaskStatus;
    public RequestTask requestTask;
    public ReportTaskProcessingResult reportTaskProcessingResult;
    public CheckForMissingOutputResources checkForMissingOutputResources;
    public ResendTaskOutputResourceResult resendTaskOutputResourceResult;

    @Data
    public static class FunctionDownloadStatus {
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Status {
            public Enums.FunctionState functionState;
            public String functionCode;
        }

        public List<Status> statuses = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorCommContext {
        public String processorId;
        public String sessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestProcessorId {
        public boolean keep = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckForMissingOutputResources {
        public boolean keep = true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestTask {
        public boolean acceptOnlySigned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportProcessorStatus {
        public EnvYaml env;
        public GitSourcingService.GitStatusInfo gitStatusInfo;
        public String schedule;
        public String sessionId;

        // TODO 2019-05-28, a multi-time-zoned deployment isn't supported right now
        // it'll work but in some cases behaviour can be different
        // need to change it to UTC, Coordinated Universal Time
        public long sessionCreatedOn;
        public String ip;
        public String host;

        // contains text of error which can occur while preparing a processor status
        public List<String> errors = null;
        public boolean logDownloadable;
        public int taskParamsVersion;

        public EnumsApi.OS os;

        public void addError(String error) {
            if (errors==null) {
                errors = new ArrayList<>();
            }
            errors.add(error);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportTaskProcessingResult {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleTaskExecResult {
            public long taskId;
            public String result;
        }

        public List<SimpleTaskExecResult> results = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReportProcessorTaskStatus {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long taskId;
        }

        public List<SimpleStatus> statuses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResendTaskOutputResourceResult {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long taskId;
            public Enums.ResendTaskOutputResourceStatus status;
        }

        public List<SimpleStatus> statuses;
    }
}
