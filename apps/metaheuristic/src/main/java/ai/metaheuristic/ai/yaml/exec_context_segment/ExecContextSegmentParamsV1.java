/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.exec_context_segment;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V1 of {@link ExecContextSegmentParams}: a segment's structure (lines of Tasks), the exec state of its Tasks, and the
 * variable-state entries of its Tasks.
 */
@Data
public class ExecContextSegmentParamsV1 implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    public static class LineV1 {
        public String ctx;
        @Nullable
        public Long forkTaskId;
        public List<VertexV1> tasks = new ArrayList<>();
        /** 041 Phase 11: counted in its derived join's record; see {@code ExecContextSegmentParams.Line#registered}. */
        public boolean registered;
    }

    @Data
    @NoArgsConstructor
    public static class VertexV1 {
        public Long taskId;
        @Nullable
        public String tag;
    }

    public final List<LineV1> lines = new ArrayList<>();

    public final Map<Long, EnumsApi.TaskExecState> states = new HashMap<>();

    public final Map<Long, Integer> triesWasMade = new HashMap<>();

    public final List<ExecContextApiData.VariableState> variableStates = new ArrayList<>();
}
