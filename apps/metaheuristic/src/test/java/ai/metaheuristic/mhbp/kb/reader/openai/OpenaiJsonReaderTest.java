/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.mhbp.kb.reader.openai;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.system.SystemProcessLauncher;
import ai.metaheuristic.api.data.GitData;
import ai.metaheuristic.ai.mhbp.kb.reader.openai.OpenaiInput;
import ai.metaheuristic.ai.mhbp.kb.reader.openai.OpenaiJsonReader;
import ai.metaheuristic.commons.utils.JsonUtils;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.ai.mhbp.questions.QuestionData;
import ai.metaheuristic.ai.mhbp.services.LocalGitRepoService;
import ai.metaheuristic.ai.mhbp.yaml.kb.KbParams;
import ai.metaheuristic.ai.mhbp.yaml.kb.KbParamsUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.mhbp.services.LocalGitSourcingService.getGitStatus;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 4/19/2023
 * Time: 11:52 PM
 */
@Disabled
public class OpenaiJsonReaderTest {

    @SneakyThrows
    private static String jsonAsString(QuestionData.QuestionWithAnswerToAsk o) {
        return JsonUtils.getMapper().writeValueAsString(o);
    }

    @Test
    public void test_read() {

        String s = """
                TASK: Read the chess position provided in FEN-notation, then identify the best move to the board position below, in the format A. Your answer should only contain the letter of the correct move. Do not provide any further explanation.
                            
                White to move FEN: rn2k2r/pp2ppPp/2p5/6Q1/2q3b1/2N5/PPP2PPP/R1B1K1NR Possible Moves: A: Rg8, B: Qd4, C: Qxc3 Answer only with the letter of the beset move.""";

        String yaml = """
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
        KbParams kbParams = KbParamsUtils.UTILS.to(yaml);

        assertNotNull(kbParams.kb.git);
        assertEquals(1, kbParams.kb.git.kbPaths.size());

        KbParams.KbPath kbPath = kbParams.kb.git.kbPaths.get(0);


        final String mhbpHomeEnv = System.getenv("MHBP_HOME");
        assertFalse(S.b(mhbpHomeEnv));

        Path mhbpHome = Path.of(mhbpHomeEnv);
        Path gitPath = mhbpHome.resolve(Consts.GIT_PATH);

        String code = StrUtils.asCode(kbParams.kb.git.repo);

        Path p = gitPath.resolve(code);
        assertFalse(Files.notExists(p));

        Path repo = p.resolve(CommonConsts.GIT_REPO);
        assertFalse(Files.notExists(repo));

        QuestionData.Chapters qas = OpenaiJsonReader.read(10L, repo, kbParams.kb.git);

        String jsonl = qas.chapters.stream().flatMap(o->o.list().stream()).map(OpenaiJsonReaderTest::jsonAsString).collect(Collectors.joining("\n"));

        System.out.println("Total inputs: " + qas.chapters.stream().mapToLong(o->o.list().size()).sum());
        System.out.println("Final size: " + jsonl.length());

        int i = 0;
    }

    @Test
    public void test_read_yaml() {
        String s = """
                balance-chemical-equation:
                  id: balance-chemical-equation.dev.v0
                  metrics: [accuracy]
                                
                balance-chemical-equation.dev.v0:
                  class: evals.elsuite.basic.match:Match
                  args:
                    samples_jsonl: balance_chemical_equation/samples.jsonl
                """;

        final String jsonlPath = OpenaiJsonReader.parseAndGetJsonlPath(s);
        System.out.println(jsonlPath);
        int i = 0;
    }

    @Test
    public void test_toOpenaiInput_1() throws JsonProcessingException {

        String json = """
                {
                  "input": [
                    {
                      "role": "system",
                      "content": "You are LawStudentGPT. Answer the following True/False question according to the ABA Model Rules of Professional Conduct."
                    },
                    {
                      "role": "user",
                      "content": "A lawyer with general experience not considered competent to handle a case involving a specialized field of law."
                    }
                  ],
                  "ideal": "False"
                }""";

        OpenaiInput openaiInput = OpenaiJsonReader.toOpenaiInput(json);

        assertEquals("system", openaiInput.input.get(0).role);
        assertEquals("You are LawStudentGPT. Answer the following True/False question according to the ABA Model Rules of Professional Conduct.", openaiInput.input.get(0).content);
        assertEquals("user", openaiInput.input.get(1).role);
        assertEquals("A lawyer with general experience not considered competent to handle a case involving a specialized field of law.", openaiInput.input.get(1).content);
        assertEquals("False", openaiInput.getIdeal());

        System.out.println(openaiInput);
    }

    @Test
    public void test_toOpenaiInput_2() throws JsonProcessingException {

        String json = """
                {
                  "input": [
                    {
                      "role": "system",
                      "content": "You are ChemistGPT, can help user balance chemical equation. User for example, if user's input is \\"C6H5COOH + O2 = CO2 + H2O\\", you will reply the balanced chemical equation: \\"2C6H5COOH + 15O2 = 14CO2 + 6H2O\\", without explanation. If you can't balance the equation, just reply \\"Unknown\\""
                    },
                    {
                      "role": "user",
                      "content": "Fe + Cl2 = FeCl3"
                    }
                  ],
                  "ideal": [
                    "2Fe + 3Cl_2 = 2FeCl_3"
                  ]
                }""";

        OpenaiInput openaiInput = OpenaiJsonReader.toOpenaiInput(json);

        assertEquals(2, openaiInput.input.size());
        assertEquals("system", openaiInput.input.get(0).role);
        assertEquals("You are ChemistGPT, can help user balance chemical equation. User for example, if user's input is \"C6H5COOH + O2 = CO2 + H2O\", you will reply the balanced chemical equation: \"2C6H5COOH + 15O2 = 14CO2 + 6H2O\", without explanation. If you can't balance the equation, just reply \"Unknown\"", openaiInput.input.get(0).content);
        assertEquals("user", openaiInput.input.get(1).role);
        assertEquals("Fe + Cl2 = FeCl3", openaiInput.input.get(1).content);
        assertEquals("2Fe + 3Cl_2 = 2FeCl_3", openaiInput.getIdeal());

        System.out.println(openaiInput);
    }

    @Test
    public void test_toOpenaiInput_3() throws JsonProcessingException {

        String json = """
               {
                 "input": [
                   {
                     "role": "system",
                     "content": "The text transformation rules are as follows: 1) if \\"a\\" character is encountered, replace it with \\"z\\". 2) if \\"e\\" character is encountered, replace it with \\"y\\". The rules are case-sensitive. Return the transformed text. Respond as concise as possible."
                   },
                   {
                     "role": "system",
                     "content": "How are you?",
                     "name": "example_user"
                   },
                   {
                     "role": "system",
                     "content": "How zry you?",
                     "name": "example_assistant"
                   },
                   {
                     "role": "user",
                     "content": "Hello! How are you?"
                   }
                 ],
                 "ideal": [
                   "Hyllo! How zry you?"
                 ]
               }""";

        OpenaiInput openaiInput = OpenaiJsonReader.toOpenaiInput(json);

        assertEquals(4, openaiInput.input.size());
        assertEquals("system", openaiInput.input.get(0).role);
        assertEquals("The text transformation rules are as follows: 1) if \"a\" character is encountered, replace it with \"z\". 2) if \"e\" character is encountered, replace it with \"y\". The rules are case-sensitive. Return the transformed text. Respond as concise as possible.", openaiInput.input.get(0).content);
        assertEquals("system", openaiInput.input.get(1).role);
        assertEquals("How are you?", openaiInput.input.get(1).content);
        assertEquals("system", openaiInput.input.get(2).role);
        assertEquals("How zry you?", openaiInput.input.get(2).content);
        assertEquals("user", openaiInput.input.get(3).role);
        assertEquals("Hello! How are you?", openaiInput.input.get(3).content);
        assertEquals("Hyllo! How zry you?", openaiInput.getIdeal());

        System.out.println(openaiInput);
    }

    @Test
    public void test_OpenaiJsonReader_read(@TempDir Path temp) throws IOException {
        String yaml = IOUtils.resourceToString("/mhbp/kb/openai-format-math/kb-openai-format-math.yaml", StandardCharsets.UTF_8);

        KbParams kbParams = KbParamsUtils.UTILS.to(yaml);
        assertNotNull(kbParams.kb.git);

        GitData.GitStatusInfo statusInfo = getGitStatus(new GitData.GitContext(30L, 100));
        assertEquals(EnumsApi.GitStatus.installed, statusInfo.status);

        KbParams.Git git = new KbParams.Git(kbParams.kb.git.repo, kbParams.kb.git.branch, kbParams.kb.git.commit, null);
        Path gitPath = temp.resolve("git");
        GitData.GitContext gitContext = new GitData.GitContext(60L, 100);

        SystemProcessLauncher.ExecResult result = LocalGitRepoService.initGitRepo(git, gitPath, gitContext);
        assertNotNull(result.systemExecResult);
        assertTrue(result.systemExecResult.isOk);
        assertNotNull(result.functionDir);
        assertTrue(Files.exists(result.functionDir));

        Path f = result.functionDir.resolve("pom.xml");
        assertTrue(Files.exists(f));
        assertTrue(Files.isRegularFile(f));

        QuestionData.Chapters chapters = OpenaiJsonReader.read(10L, result.functionDir, kbParams.kb.git);

        assertEquals(1, chapters.chapters.size());
        assertEquals("math/simple-math.jsonl", chapters.chapters.get(0).chapterCode());
        assertEquals(2, chapters.chapters.get(0).list().size());
        assertEquals("answer 2+2 with digits only", chapters.chapters.get(0).list().get(0).q());
        assertEquals("4", chapters.chapters.get(0).list().get(0).a());
        assertEquals("answer square root of 9 with only digits", chapters.chapters.get(0).list().get(1).q());
        assertEquals("3", chapters.chapters.get(0).list().get(1).a());

        int i=0;
    }

}
