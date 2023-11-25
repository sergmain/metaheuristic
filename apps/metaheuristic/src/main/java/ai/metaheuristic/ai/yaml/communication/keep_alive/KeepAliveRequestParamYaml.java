/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.communication.keep_alive;

import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Serge
 * Date: 11/21/2020
 * Time: 1:03 AM
 */
@Data
public class KeepAliveRequestParamYaml implements BaseParams {

    public final int version=3;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorage {
        public String code;
        public String path;
    }

    // event though at core side a quatas is placed in env.yaml above all cores level
    // here it'll be placed at concrete core
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
//    @EqualsAndHashCode( of={"tag","amount"})
    public static class Quota {
        public String tag;
        public int amount;
        // core can disable specific tag. i.e. on scheduler basis
        public boolean disabled;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Quotas {
        public List<Quota> values = new ArrayList<>();
        public int limit;
        public int defaultValue;
        public boolean disabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Core {
        public String coreDir;
        @Nullable
        public Long coreId;

        public String coreCode;

        @Nullable
        public String tags;

        public Core(String coreCode) {
            this.coreCode = coreCode;
        }
    }

    @Data
    @NoArgsConstructor
    public static class Env {
        public final Map<String, String> mirrors = new ConcurrentHashMap<>();
        public final Map<String, String> envs = new ConcurrentHashMap<>();
        public final List<DiskStorage> disk = new ArrayList<>();
        public final Quotas quotas = new Quotas();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    // ReportProcessor
    public static class ProcessorStatus {
        public Env env;
        public GtiUtils.GitStatusInfo gitStatusInfo;
        public String schedule;
        public String ip;
        public String host;

        public boolean logDownloadable;
        public int taskParamsVersion;

        public EnumsApi.OS os;
        public String currDir;

        // contains text of error which can occur while preparing a processor status
        @Nullable
        public List<String> errors = null;

        public void addError(String error) {
            if (errors==null) {
                errors = new ArrayList<>();
            }
            errors.add(error);
        }
    }

/*
    @Data
    public static class FunctionDownloadStatuses {
        public final Map<EnumsApi.FunctionState, String> statuses = new HashMap<>();
    }
*/

    @Data
    @NoArgsConstructor
    public static class RequestProcessorId {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorCommContext {
        public Long processorId;
        public String sessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Processor {
        public ProcessorStatus status;

        // if not null it means we need a new processorId
        @Nullable
        public ProcessorCommContext processorCommContext;

        public String processorCode;

        public Processor(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final Processor processor = new Processor();
    public final List<Core> cores = new ArrayList<>();
/*
    public final FunctionDownloadStatuses functions = new FunctionDownloadStatuses();
*/

}
