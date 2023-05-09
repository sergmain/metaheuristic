/*
 *    Copyright 2023, Sergio Lissner, Innovation platforms, LLC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package ai.metaheuristic.mhbp.yaml.kb;

import ai.metaheuristic.ai.mhbp.yaml.kb.KbParams;
import ai.metaheuristic.ai.mhbp.yaml.kb.KbParamsUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 4/16/2023
 * Time: 9:01 PM
 */
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

        String s = IOUtils.resourceToString("/kb/kb-inline-simple-chess.yaml", StandardCharsets.UTF_8);

        KbParams kbp = KbParamsUtils.UTILS.to(s);
        assertNotNull(kbp.kb.inline);
        assertEquals(1, kbp.kb.inline.size());
        List<KbParams.Inline> in = kbp.kb.inline;
        assertEquals("A", in.get(0).a);
        assertEquals("answer 2+2 with digits only", in.get(0).p);
    }

    @Test
    public void test_KbParamsUtils_1() {

        String s = """
            version: 1
            kb:
              code: openai
              type: openai-evals
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
