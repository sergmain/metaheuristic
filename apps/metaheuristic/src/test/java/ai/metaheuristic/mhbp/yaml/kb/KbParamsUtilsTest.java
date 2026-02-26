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

package ai.metaheuristic.mhbp.yaml.kb;

import ai.metaheuristic.ai.mhbp.yaml.kb.KbParams;
import ai.metaheuristic.ai.mhbp.yaml.kb.KbParamsUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 4/16/2023
 * Time: 9:01 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class KbParamsUtilsTest {

    @Test
    public void test_KbParamsUtils() {

        String s = """
            version: 1
            kb:
             code: mhbp-sample
             type: mhbp
             inline:
              - p: answer 2+2 with digits only
                a: 4
              - p: answer square root of 9 with only digits
                a: 3
            """;

        KbParams kbp = KbParamsUtils.UTILS.to(s);
        assertNotNull(kbp.kb.inline);
        assertEquals(2, kbp.kb.inline.size());
        List<KbParams.Inline> in = kbp.kb.inline;
        assertEquals("answer 2+2 with digits only", in.get(0).p);
        assertEquals("4", in.get(0).a);
        assertEquals("answer square root of 9 with only digits", in.get(1).p);
        assertEquals("3", in.get(1).a);
    }

    @Test
    public void test_KbParamsUtils_11() throws IOException {

        String s = IOUtils.resourceToString("/mhbp/kb/kb-inline-simple-chess.yaml", StandardCharsets.UTF_8);

        KbParams kbp = KbParamsUtils.UTILS.to(s);
        assertNotNull(kbp.kb.inline);
        assertEquals(1, kbp.kb.inline.size());
        List<KbParams.Inline> in = kbp.kb.inline;
        assertEquals("A", in.get(0).a);
        assertEquals("TASK: Read the chess position provided in FEN-notation, then identify the best move to the board position below, in the format A. Your answer should only contain the letter of the correct move. Do not provide any further explanation.\n" +
                     "White to move FEN: rn2k2r/pp2ppPp/2p5/6Q1/2q3b1/2N5/PPP2PPP/R1B1K1NR Possible Moves: A: Rg8, B: Qd4, C: Qxc3 Answer only with the letter of the beset move.answer 2+2 with digits only",
                in.get(0).p.strip());
    }

    @Test
    public void test_KbParamsUtils_1() {

        String s = """
            version: 1
            kb:
              code: openai
              type: openai
              git:
                repo: https://github.com/openai/evals.git
                branch: main
                commit: origin
                kbPaths:
                  - evals: evals/registry/evals
                    data: evals/registry/data

            """;

        KbParams kbp = assertDoesNotThrow(()->KbParamsUtils.UTILS.to(s));

    }
}
