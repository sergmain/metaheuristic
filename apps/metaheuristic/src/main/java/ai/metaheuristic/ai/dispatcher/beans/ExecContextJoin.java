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

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * One join vertex of an ExecContext (041-EXEC-CONTEXT-SEGMENTS-PLAN, decisions 8 and 9): keyed by the join Task, not
 * by the fork, since many forks can resolve to one join. It counts the lines registered to the join, and how many of
 * them finished or died; {@code closed} marks the join as complete.
 */
@Entity
@Table(name = "MH_EXEC_CONTEXT_JOIN")
@Data
@NoArgsConstructor
@ToString
public class ExecContextJoin implements Serializable {
    @Serial
    private static final long serialVersionUID = -2871506142963170488L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "EXEC_CONTEXT_ID")
    public Long execContextId;

    @Column(name = "JOIN_TASK_ID")
    public Long joinTaskId;

    @Column(name = "LINES_REGISTERED")
    public int linesRegistered;

    @Column(name = "LINES_FINISHED")
    public int linesFinished;

    @Column(name = "LINES_DEAD")
    public int linesDead;

    @Column(name = "IS_CLOSED")
    public boolean closed;

    @Column(name = "CREATED_ON")
    public Long createdOn;
}
