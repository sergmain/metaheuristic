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

package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParamsUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;

/**
 * One segment of an ExecContext (041-EXEC-CONTEXT-SEGMENTS-PLAN, decision 8): the root segment, or one grafted line
 * with its nested subtree. {@code LINE_CTX_ID} is unique per ExecContext; {@code FORK_TASK_ID} is the Task whose
 * completion starts the line (null for the root segment); {@code STRUCTURE_HASH} is the hash of the segment's structure,
 * written whenever the structure is written and never on a task-state change.
 */
@Entity
@Table(name = "MH_EXEC_CONTEXT_SEGMENT")
@Data
@NoArgsConstructor
@ToString
public class ExecContextSegment implements Serializable {
    @Serial
    private static final long serialVersionUID = 4185730269145720316L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "EXEC_CONTEXT_ID")
    public Long execContextId;

    @Column(name = "LINE_CTX_ID")
    public String lineCtxId;

    @Nullable
    @Column(name = "FORK_TASK_ID")
    public Long forkTaskId;

    @Column(name = "STRUCTURE_HASH")
    public String structureHash;

    @Column(name = "CREATED_ON")
    public Long createdOn;

    @Column(name = "PARAMS")
    private String params;

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.paramsLocked.reset(()->this.params = params);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<ExecContextSegmentParams> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ExecContextSegmentParams parseParams() {
        ExecContextSegmentParams temp = ExecContextSegmentParamsUtils.BASE_UTILS.to(params);
        return temp==null ? new ExecContextSegmentParams() : temp;
    }

    @JsonIgnore
    public ExecContextSegmentParams getExecContextSegmentParams() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ExecContextSegmentParams p) {
        setParams(ExecContextSegmentParamsUtils.BASE_UTILS.toString(p));
    }
}
