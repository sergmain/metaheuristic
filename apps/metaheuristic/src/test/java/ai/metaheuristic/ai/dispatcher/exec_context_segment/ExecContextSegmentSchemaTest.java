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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextJoin;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.IdsRepository;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 of 041-EXEC-CONTEXT-SEGMENTS-PLAN: the {@code MH_EXEC_CONTEXT_SEGMENT} and {@code MH_EXEC_CONTEXT_JOIN} tables
 * of the H2 {@code 00010} script, their entities and repositories, against the real shared H2 of UT harness V3.
 * Nothing in production reads or writes these tables yet; this pins the schema contract the later phases build on:
 * the params round-trip through the record, and the two unique keys hold.
 *
 * <p>Every ExecContext id and join Task id comes from {@link SharedItEnv#uniqueLong()}, so rows written by one
 * {@code @Test} never meet another's in the shared DB.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextSegmentSchemaTest extends MhSharedItTest {

    @Autowired private ExecContextSegmentRepository segmentRepository;
    @Autowired private ExecContextJoinRepository joinRepository;
    @Autowired private ExecContextSegmentIdService idService;
    @Autowired private IdsRepository idsRepository;

    private ExecContextSegment segment(Long execContextId, String lineCtxId, Long forkTaskId) {
        ExecContextSegmentParams p = new ExecContextSegmentParams();
        ExecContextSegmentParams.Line line = new ExecContextSegmentParams.Line(lineCtxId, forkTaskId);
        line.tasks.add(new ExecContextSegmentParams.Vertex(11L, null));
        line.tasks.add(new ExecContextSegmentParams.Vertex(12L, "terminal"));
        p.lines.add(line);
        p.states.put(11L, EnumsApi.TaskExecState.OK);

        ExecContextSegment s = new ExecContextSegment();
        s.id = idService.allocate();
        s.execContextId = execContextId;
        s.lineCtxId = lineCtxId;
        s.forkTaskId = forkTaskId;
        s.structureHash = "0".repeat(64);
        s.createdOn = System.currentTimeMillis();
        s.updateParams(p);
        return s;
    }

    @Test
    public void test_segment_savedAndReadByUniqueKey_sameCtxAllowedInAnotherExecContext() {
        final Long ecId = SharedItEnv.uniqueLong();
        final Long otherEcId = SharedItEnv.uniqueLong();
        segmentRepository.save(segment(ecId, "1,2#1", 7L));
        segmentRepository.save(segment(otherEcId, "1,2#1", 7L));

        ExecContextSegment read = segmentRepository.findByExecContextIdAndLineCtxId(ecId, "1,2#1");
        assertNotNull(read, "the segment must be found by (ExecContext, line ctx)");
        assertEquals(7L, read.forkTaskId);
        assertEquals("0".repeat(64), read.structureHash);
        assertNotNull(read.version, "the record must be versioned");

        ExecContextSegmentParams p = read.getExecContextSegmentParams();
        assertEquals(1, p.lines.size());
        assertEquals("1,2#1", p.lines.getFirst().ctx);
        assertEquals(List.of(11L, 12L), p.lines.getFirst().tasks.stream().map(v -> v.taskId).toList());
        assertEquals("terminal", p.lines.getFirst().tasks.get(1).tag);
        assertEquals(EnumsApi.TaskExecState.OK, p.states.get(11L));

        assertEquals(1, segmentRepository.findIdsByExecContextId(ecId).size(), "one segment in this ExecContext");
        assertEquals(1, segmentRepository.findIdsByExecContextId(otherEcId).size(), "the same line ctx in another ExecContext is a different segment");
        assertNull(segmentRepository.findByExecContextIdAndLineCtxId(ecId, "1,2#2"));
    }

    @Test
    public void test_segment_duplicateLineCtxInOneExecContext_rejected() {
        final Long ecId = SharedItEnv.uniqueLong();
        segmentRepository.save(segment(ecId, "1,2#1", 7L));

        assertThrows(DataIntegrityViolationException.class, () -> segmentRepository.save(segment(ecId, "1,2#1", 8L)),
                "UNIQUE(EXEC_CONTEXT_ID, LINE_CTX_ID) must refuse a second segment at the same line ctx");
        assertEquals(1, segmentRepository.findIdsByExecContextId(ecId).size());
    }

    @Test
    public void test_join_savedAndReadByUniqueKey_duplicateRejected() {
        final Long ecId = SharedItEnv.uniqueLong();
        final Long joinTaskId = SharedItEnv.uniqueLong();

        ExecContextJoin j = new ExecContextJoin();
        j.execContextId = ecId;
        j.joinTaskId = joinTaskId;
        j.linesRegistered = 11;
        j.linesFinished = 3;
        j.linesDead = 1;
        j.createdOn = System.currentTimeMillis();
        joinRepository.save(j);

        ExecContextJoin read = joinRepository.findByExecContextIdAndJoinTaskId(ecId, joinTaskId);
        assertNotNull(read, "the join must be found by (ExecContext, join Task)");
        assertEquals(11, read.linesRegistered);
        assertEquals(3, read.linesFinished);
        assertEquals(1, read.linesDead);
        assertFalse(read.closed, "a new join is not closed");
        assertEquals(List.of(read.id), joinRepository.findIdsByExecContextId(ecId));

        ExecContextJoin dup = new ExecContextJoin();
        dup.execContextId = ecId;
        dup.joinTaskId = joinTaskId;
        dup.createdOn = System.currentTimeMillis();
        assertThrows(DataIntegrityViolationException.class, () -> joinRepository.save(dup),
                "UNIQUE(EXEC_CONTEXT_ID, JOIN_TASK_ID) must refuse a second record for the same join vertex");
    }

    /**
     * Decision 10: the segment's id is allocated before the row is built (from {@code mh_ids}, as a Company's unique id
     * is), so the line ctx derived from it, the params and the structure hash all go into the one INSERT. Hibernate
     * builds an INSERT from the state at {@code persist()} - observed: setting the ctx after {@code persist()} left
     * {@code LINE_CTX_ID} NULL in the INSERT - which is why the id is not generated by the row itself.
     */
    @Test
    public void test_idAllocatedBeforeTheRow_lineCtxDerivedFromIt_inTheOneInsert() {
        final Long ecId = SharedItEnv.uniqueLong();
        final Long id = idService.allocate();
        assertFalse(idsRepository.existsById(id), "allocating leaves no row in MH_IDS");
        assertTrue(idService.allocate() > id, "the next allocation draws a larger number");

        final ExecContextSegment s = new ExecContextSegment();
        s.id = id;
        s.execContextId = ecId;
        s.forkTaskId = 7L;
        s.createdOn = System.currentTimeMillis();
        s.lineCtxId = SegmentLineCtx.lineCtx("1,17", 4, id);
        final ExecContextSegmentParams p = new ExecContextSegmentParams();
        final ExecContextSegmentParams.Line line = new ExecContextSegmentParams.Line(s.lineCtxId, 7L);
        line.tasks.add(new ExecContextSegmentParams.Vertex(21L, null));
        p.lines.add(line);
        s.updateParams(p);
        s.structureHash = SegmentStructureHash.structureHash(SegmentParamsConverter.segment(s.lineCtxId, s.forkTaskId, p));
        segmentRepository.save(s);

        final ExecContextSegment read = segmentRepository.findByExecContextIdAndLineCtxId(ecId, "1,17#" + (4 + id));
        assertNotNull(read, "the row is stored under the ctx derived from its own id");
        assertEquals(id, read.id);
        assertEquals(List.of(21L), read.getExecContextSegmentParams().lines.getFirst().tasks.stream().map(v -> v.taskId).toList());
        assertEquals(SegmentStructureHash.structureHash(SegmentParamsConverter.segment(read.lineCtxId, read.forkTaskId,
                read.getExecContextSegmentParams())), Objects.requireNonNull(read.structureHash));
    }
}
