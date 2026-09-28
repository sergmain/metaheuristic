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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeInitService;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 041-EXEC-CONTEXT-SEGMENTS-PLAN Phase 12: the two permute internal functions on segments.
 *
 * <p>{@code mh.permute-variables} and {@code mh.permute-values-of-variables} create one line per permutation under
 * their Task. Each case runs the function's existing YAML fixture to a finished state on the Phase 1 step driver
 * ({@link ExecContextBaselineSupport}: {@code MH_TASK} records and the real internal-task queue only) and pins, in
 * one text: the final ExecContext state, the Tasks handed out per scheduler step, and the final
 * (process code, ctx, exec state) of every Task.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentPermuteFunctionsTest extends PreparingSourceCode {

    private static final String PERMUTE_VARIABLES_URI = "/source_code/yaml/variables/variables-as-not-present.yaml";
    private static final String PERMUTE_VALUES_URI = "/source_code/yaml/variables/permute-values-of-variables.yaml";

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private SegmentInvariantAsserts invariants;

    private static final String PERMUTE_VARIABLES_OBSERVED = """
            execContext=FINISHED
            --- steps
            0: mh.inline-as-variable@1
            1: mh.permute-variables@1
            2: mh.nop@1,2#1, mh.nop@1,2#2, mh.nop@1,2#3
            3: mh.finish@1
            --- final
            mh.finish@1=OK
            mh.inline-as-variable@1=OK
            mh.nop@1,2#1=OK
            mh.nop@1,2#2=OK
            mh.nop@1,2#3=OK
            mh.permute-variables@1=OK""";

    private static final String PERMUTE_VALUES_OBSERVED = """
            execContext=FINISHED
            --- steps
            0: mh.inline-as-variable@1
            1: mh.permute-values-of-variables@1
            2: mh.nop@1,2#1, mh.nop@1,2#2
            3: mh.finish@1
            --- final
            mh.finish@1=OK
            mh.inline-as-variable@1=OK
            mh.nop@1,2#1=OK
            mh.nop@1,2#2=OK
            mh.permute-values-of-variables@1=OK""";

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(PERMUTE_VARIABLES_URI, EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_permuteVariables_oneLinePerPermutation() {
        assertEquals(PERMUTE_VARIABLES_OBSERVED, run(PERMUTE_VARIABLES_URI, "permute-variables"),
                "mh.permute-variables: ExecContext state, hand-out per step, final Task states");
    }

    @Test
    public void test_permuteValuesOfVariables_oneLinePerValue() {
        assertEquals(PERMUTE_VALUES_OBSERVED, run(PERMUTE_VALUES_URI, "permute-values-of-variables"),
                "mh.permute-values-of-variables: ExecContext state, hand-out per step, final Task states");
    }

    private String run(String uri, String label) {
        final String source = resolveSourceCode(new SourceCodeUriAndLang(uri, EnumsApi.SourceCodeLang.yaml, null));
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(source, EnumsApi.SourceCodeLang.yaml);
        final ExecContextBaselineSupport.StepRun run = support.runStepByStep(() -> {
            final ExecContextImpl ec = support.createAndStart(data, null);
            // the harness's per-test ExecContext pointer follows this run
            setExecContextForTest(ec);
            return ec.id;
        }, 40);
        support.assertEveryRunTaskHandedOutOnce(run.execContextId(), run.steps(), label);
        invariants.assertAll(run.execContextId());
        return "execContext=" + support.state(run.execContextId())
                + "\n--- steps\n" + ExecContextBaselineSupport.describeSteps(run.steps())
                + "\n--- final\n" + ExecContextBaselineSupport.describeRows(support.rows(run.execContextId()));
    }
}
