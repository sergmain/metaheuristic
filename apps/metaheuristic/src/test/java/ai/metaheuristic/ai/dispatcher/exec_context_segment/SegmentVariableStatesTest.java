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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 041-EXEC-CONTEXT-SEGMENTS-PLAN Phase 12: {@link SegmentVariableStates} derives the input flags the whole-ExecContext
 * variable-state record used to copy across Tasks, and never modifies the stored entries.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentVariableStatesTest {

    private static ExecContextApiData.VariableInfo info(long id, boolean inited, boolean nullified, String ext) {
        final ExecContextApiData.VariableInfo v = new ExecContextApiData.VariableInfo(id, "v" + id, EnumsApi.VariableContext.local, ext);
        v.inited = inited;
        v.nullified = nullified;
        return v;
    }

    private static ExecContextApiData.VariableState entry(long taskId, List<ExecContextApiData.VariableInfo> inputs,
                                                          List<ExecContextApiData.VariableInfo> outputs) {
        final ExecContextApiData.VariableState s = new ExecContextApiData.VariableState();
        s.taskId = taskId;
        s.execContextId = 7L;
        s.taskContextId = "1";
        s.process = "p" + taskId;
        s.functionCode = "f" + taskId;
        s.inputs = inputs.isEmpty() ? null : new ArrayList<>(inputs);
        s.outputs = outputs.isEmpty() ? null : new ArrayList<>(outputs);
        return s;
    }

    private static ExecContextApiData.VariableInfo inputOf(List<ExecContextApiData.VariableState> states, long taskId, long varId) {
        return states.stream().filter(s -> s.taskId == taskId).findFirst().orElseThrow()
                .inputs.stream().filter(i -> i.id == varId).findFirst().orElseThrow();
    }

    @Test
    public void test_uploadedOutput_marksTheConsumersInput() {
        final List<ExecContextApiData.VariableState> r = SegmentVariableStates.withDerivedInputs(List.of(
                entry(1, List.of(), List.of(info(100, true, false, ".txt"))),
                entry(2, List.of(info(100, false, false, null)), List.of())));
        final ExecContextApiData.VariableInfo in = inputOf(r, 2, 100);
        assertTrue(in.inited, "the consumer's input of an uploaded output is inited");
        assertFalse(in.nullified, "not nullified, as the output");
    }

    @Test
    public void test_nullifiedOutput_marksTheConsumersInputNullified() {
        final List<ExecContextApiData.VariableState> r = SegmentVariableStates.withDerivedInputs(List.of(
                entry(1, List.of(), List.of(info(100, true, true, null))),
                entry(2, List.of(info(100, false, false, null)), List.of()),
                entry(3, List.of(info(100, false, false, null)), List.of())));
        assertTrue(inputOf(r, 2, 100).inited && inputOf(r, 2, 100).nullified, "Task 2: inited and nullified");
        assertTrue(inputOf(r, 3, 100).inited && inputOf(r, 3, 100).nullified, "Task 3: every consumer, not only the first");
    }

    @Test
    public void test_notUploadedOutput_leavesTheInputAsStored() {
        final List<ExecContextApiData.VariableState> r = SegmentVariableStates.withDerivedInputs(List.of(
                entry(1, List.of(), List.of(info(100, false, false, null))),
                entry(2, List.of(info(100, false, false, null)), List.of())));
        assertFalse(inputOf(r, 2, 100).inited, "an output not yet uploaded marks nothing");
    }

    @Test
    public void test_initedInputWithoutProducer_isBackFilledIntoOtherInputs() {
        // a source-level input: no Task produces it, one entry recorded it inited
        final List<ExecContextApiData.VariableState> r = SegmentVariableStates.withDerivedInputs(List.of(
                entry(1, List.of(info(50, true, false, null)), List.of()),
                entry(2, List.of(info(50, false, false, null)), List.of())));
        assertTrue(inputOf(r, 2, 50).inited, "an inited input is known to every other input of the same variable");
    }

    @Test
    public void test_outputStateWinsOverAnInputState() {
        final List<ExecContextApiData.VariableState> r = SegmentVariableStates.withDerivedInputs(List.of(
                entry(1, List.of(info(100, true, false, null)), List.of()),
                entry(2, List.of(), List.of(info(100, true, true, null))),
                entry(3, List.of(info(100, false, false, null)), List.of())));
        assertTrue(inputOf(r, 3, 100).nullified, "the output's nullified wins over an input's record");
        assertTrue(inputOf(r, 1, 100).nullified, "and it overrides the input that disagreed");
    }

    @Test
    public void test_unknownVariable_keepsItsStoredFlags() {
        final List<ExecContextApiData.VariableState> r = SegmentVariableStates.withDerivedInputs(List.of(
                entry(1, List.of(info(60, false, true, null)), List.of())));
        final ExecContextApiData.VariableInfo in = inputOf(r, 1, 60);
        assertFalse(in.inited, "stored inited kept");
        assertTrue(in.nullified, "stored nullified kept");
    }

    @Test
    public void test_storedEntriesAreNotModified_resultIsACopy() {
        final ExecContextApiData.VariableState consumer = entry(2, List.of(info(100, false, false, null)), List.of());
        final ExecContextApiData.VariableState producer = entry(1, List.of(), List.of(info(100, true, false, ".bin")));
        final List<ExecContextApiData.VariableState> r = SegmentVariableStates.withDerivedInputs(List.of(producer, consumer));
        assertFalse(consumer.inputs.getFirst().inited, "the stored input is untouched");
        assertNotSame(consumer, r.get(1), "the result entry is a copy");
        assertNotSame(producer.outputs.getFirst(), r.get(0).outputs.getFirst(), "outputs are copied too");
    }

    @Test
    public void test_orderAndFieldsPreserved_nullListsStayNull() {
        final ExecContextApiData.VariableState a = entry(5, List.of(), List.of());
        final ExecContextApiData.VariableState b = entry(3, List.of(info(9, false, false, null)), List.of(info(10, true, false, ".z")));
        final List<ExecContextApiData.VariableState> r = SegmentVariableStates.withDerivedInputs(List.of(a, b));
        assertEquals(List.of(5L, 3L), r.stream().map(s -> s.taskId).toList(), "input order kept");
        assertNull(r.get(0).inputs, "no inputs stays null");
        assertNull(r.get(0).outputs, "no outputs stays null");
        final ExecContextApiData.VariableState c = r.get(1);
        assertEquals(List.of(7L, 3L, "1", "p3", "f3"), List.of(c.execContextId, c.taskId, c.taskContextId, c.process, c.functionCode));
        final ExecContextApiData.VariableInfo out = c.outputs.getFirst();
        assertEquals(List.of(10L, "v10", ".z"), List.of(out.id, out.name, out.ext));
        assertEquals(EnumsApi.VariableContext.local, out.context);
        assertTrue(out.inited, "output flags as stored");
    }

    @Test
    public void test_outputExt_nonBlankFromTheProducersOutput() {
        final List<ExecContextApiData.VariableState> entries = List.of(
                entry(1, List.of(info(100, true, false, ".in")), List.of(info(101, true, false, ""))),
                entry(2, List.of(), List.of(info(100, true, false, ".csv"))));
        assertEquals(".csv", SegmentVariableStates.outputExt(entries, 100L), "an output's ext, never an input's");
        assertNull(SegmentVariableStates.outputExt(entries, 101L), "a blank ext is no ext");
        assertNull(SegmentVariableStates.outputExt(entries, 999L), "unknown variable");
    }
}
