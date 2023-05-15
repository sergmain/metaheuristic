/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 3:06 PM
 */
@RestController
@RequestMapping("/rest/v1/provider/simple/stub")
@Slf4j
//@CrossOrigin
@RequiredArgsConstructor
@Profile("dispatcher")
public class StubProviderRestController {

    public record SimpleStubAnswer(int topProb, String code, String txt) {}

    public static Map<String, List<SimpleStubAnswer>> answers = Map.of(
            "q1", List.of(
                    new SimpleStubAnswer(90, "q1", "13"),
                    new SimpleStubAnswer(100, "q1", "42")),

            "q2", List.of(new SimpleStubAnswer(100, "q2", "Good")),
            "q3", List.of(new SimpleStubAnswer(100, "q3", "Good")),
            "q4", List.of(new SimpleStubAnswer(100, "q4", "Bad")),
            "answer square root of 9 with only digits", List.of(new SimpleStubAnswer(100, "q5", "3")),
            "List of fruits which can be grown in US. Output only name of fruit, put each name on new line", List.of(new SimpleStubAnswer(100, "q6",
                    """
                         apple
                         orange
                         banana""")),
            "Make short description of apple", List.of(new SimpleStubAnswer(100, "q7", "Apple is fruit.")),
            "Make short description of orange", List.of(new SimpleStubAnswer(100, "q8", "Orange is fruit.")),
            "Make short description of banana", List.of(new SimpleStubAnswer(100, "q9", "A banana is an elongated, edible fruit – botanically a berry"))
    );

    List<SimpleStubAnswer> defAnswer = List.of(new SimpleStubAnswer(100, "", "Unknown context of question #5"));

    public static final Random r = new Random();

    // http://localhost:8080/rest/v1/provider/simple/stub/question?q=qqqqq

    @RequestMapping(method={GET, POST}, value = "/question")
    //@PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public String question(@RequestParam(name = "q") String question){
        int rInt = r.nextInt(100);
        final String s = answers.getOrDefault(question, defAnswer).stream()
                .filter(o -> o.topProb >= rInt)
                .findFirst()
                .map(o -> o.txt)
                .orElseThrow(IllegalStateException::new);
        return s;
    }

}
