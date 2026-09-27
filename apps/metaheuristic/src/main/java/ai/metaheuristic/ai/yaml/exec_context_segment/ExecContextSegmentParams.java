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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ <a href="https://github.com/sergmain/metaheuristic/wiki/multi-versioning-mechanic">...</a></b>
 * <br/>
 * The params of one ExecContext segment (041-EXEC-CONTEXT-SEGMENTS-PLAN, decision 8): the segment's structure, the
 * exec state of its Tasks, and the variable-state entries of its Tasks.
 *
 * <p>Structure is a list of {@link Line}s. A line is one chain of Tasks at one {@code taskContextId}, in chain order;
 * its head is wired from {@link Line#forkTaskId} (null for the root chain of the ExecContext) and its tail joins the
 * derived join, which is not stored. That is everything the whole-ExecContext DOT carried: Task ids, ctx, tags and
 * the edges they imply.
 */
@Data
@JsonPropertyOrder({"version"})
public class ExecContextSegmentParams implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    /** One chain of Tasks at one taskContextId, in chain order. */
    @Data
    @NoArgsConstructor
    public static class Line {
        public String ctx;
        @Nullable
        public Long forkTaskId;
        public List<Vertex> tasks = new ArrayList<>();

        public Line(String ctx, @Nullable Long forkTaskId) {
            this.ctx = ctx;
            this.forkTaskId = forkTaskId;
        }
    }

    /** One Task of a line; {@code tag} is the process tag (e.g. {@code terminal}), null when the process has none. */
    @Data
    @NoArgsConstructor
    public static class Vertex {
        public Long taskId;
        @Nullable
        public String tag;

        public Vertex(Long taskId, @Nullable String tag) {
            this.taskId = taskId;
            this.tag = tag;
        }
    }

    public final List<Line> lines = new ArrayList<>();

    public final Map<Long, EnumsApi.TaskExecState> states = new HashMap<>();

    public final Map<Long, Integer> triesWasMade = new HashMap<>();

    public final List<ExecContextApiData.VariableState> variableStates = new ArrayList<>();
}
