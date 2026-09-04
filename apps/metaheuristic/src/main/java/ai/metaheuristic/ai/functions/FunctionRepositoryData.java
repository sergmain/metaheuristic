/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.functions;

import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.replication.ReplicationApiData;
import ai.metaheuristic.commons.utils.threads.EventWithId;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergio Lissner
 * Date: 11/16/2023
 * Time: 5:38 PM
 */
public class FunctionRepositoryData {

    @AllArgsConstructor
    @EqualsAndHashCode(of = {"functionCode", "assetManagerUrl"})
    public static class DownloadFunctionTask implements EventWithId<FunctionEnums.DownloadPriority> {
        public final String functionCode;
        public final FunctionRepositoryResponseParams.ShortFunctionConfig shortFunctionConfig;
        public final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl;
        public final boolean signatureRequired;
        public final FunctionEnums.DownloadPriority priority;

        @Override
        public FunctionEnums.DownloadPriority getId() {
            return priority;
        }
    }

    /**
     * The git counterpart of {@link DownloadFunctionTask}. Deliberately a SEPARATE type rather than a
     * shared one, so the two download paths can change independently - but it is not a copy of the other
     * one's fields, because the two get their bytes from different places.
     *
     * <p>❗ No assetManagerUrl and no signatureRequired. The source of a git Function's payload is the
     * repo; an asset manager supplies nothing here. Everything needed is either in GitInfo or was already
     * sent by the Dispatcher in TaskParamsYaml, so there is nothing to look up over HTTP.
     *
     * <p>❗ getId() is the QUEUE TENANT: the normalized repo url. One virtual thread per repo is what
     * serialises writes into that repo's object store, and it is why no lock is needed around the fetch.
     * Keying on priority - which is what the copied version did - put every repo onto one of two shared
     * lanes, giving no isolation between repos and two lanes writing one object store.
     *
     * <p>❗ equals is the DEDUP KEY of a queue running with checkForDouble==true, so it must include the
     * revision. Without it a request for fn-py@456 counts as a duplicate of a queued fn-py@123 and is
     * dropped, and the Task pinned to the newer sha waits a whole poll cycle while the older one is
     * prepared instead.
     */
    @EqualsAndHashCode(of = {"functionCode", "git"})
    public static class DownloadGitFunctionTask implements EventWithId<String> {
        public final String functionCode;
        public final GitInfo git;
        /** resolved by the caller from the Function's targets; the Dispatcher already sent them */
        public final String actualFunctionFile;
        public final FunctionEnums.DownloadPriority priority;

        public DownloadGitFunctionTask(String functionCode, GitInfo git, String actualFunctionFile,
                                       FunctionEnums.DownloadPriority priority) {
            // ❗ A Processor never sees HEAD, a branch or a tag. The Dispatcher resolves the revision once,
            // when it creates the ExecContext, and every Task of that ExecContext carries the resulting sha
            // - that is what makes all Tasks of one ExecContext run the same code. So an unresolved
            // revision here is not an input to cope with, it is a broken invariant, and it fails at
            // construction rather than becoming a Task that can never be prepared and never says why.
            if (!GtiUtils.isSha(git.commit)) {
                throw new IllegalStateException(
                    "816.600 function " + functionCode + " reached a Processor with an unresolved git revision '"
                    + git.commit + "'. The Dispatcher must resolve it to a sha when the ExecContext is created.");
            }
            this.functionCode = functionCode;
            this.git = git;
            this.actualFunctionFile = actualFunctionFile;
            this.priority = priority;
        }

        @Override
        public String getId() {
            return StrUtils.asCode(git.repo);
        }
    }

    @Data
    public static class FunctionDownloadStatuses {
        public final Map<EnumsApi.FunctionState, String> statuses = new HashMap<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionPrepareResult {
        public TaskParamsYaml.FunctionConfig function;
        @Nullable
        public AssetFile functionAssetFile;
        public FunctionApiData.@Nullable SystemExecResult systemExecResult;
        public boolean isLoaded = true;
        public boolean isError = false;

        public FunctionPrepareResult(TaskParamsYaml.FunctionConfig function) {
            this.function = function;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DownloadStatus {
        public EnumsApi.FunctionState state;
        public String code;
        public ProcessorAndCoreData.AssetManagerUrl assetManagerUrl;
        public EnumsApi.FunctionSourcing sourcing;
        public AssetFile assetFile;
    }

    @Data
    @AllArgsConstructor
    public static class FunctionConfigAndStatus {
        public final TaskParamsYaml.@Nullable FunctionConfig functionConfig;
        @Nullable
        public final DownloadStatus status;
        @Nullable
        public final AssetFile assetFile;
        public final boolean contentIsInline;

        public FunctionConfigAndStatus(@Nullable DownloadStatus status) {
            this.functionConfig = null;
            this.assetFile = null;
            this.contentIsInline = false;
            this.status = status;
        }

        public FunctionConfigAndStatus(TaskParamsYaml.@Nullable FunctionConfig functionConfig, @Nullable DownloadStatus setFunctionState, AssetFile assetFile) {
            this.functionConfig = functionConfig;
            this.assetFile = assetFile;
            this.contentIsInline = false;
            this.status = setFunctionState;
        }
    }

    @Data
    public static class DownloadedFunctionConfigStatus {
        public TaskParamsYaml.FunctionConfig functionConfig;
        public FunctionEnums.ConfigStatus status;
    }

    @Data
    public static class DownloadedFunctionConfigsStatus {
        public ReplicationApiData.FunctionConfigsReplication functionConfigs;
        public FunctionEnums.ConfigStatus status;
    }
}
