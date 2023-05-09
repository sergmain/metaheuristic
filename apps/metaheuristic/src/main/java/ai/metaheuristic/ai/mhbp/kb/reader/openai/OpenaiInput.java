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

package ai.metaheuristic.ai.mhbp.kb.reader.openai;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 4/20/2023
 * Time: 4:42 PM
 */
public class OpenaiInput {

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

    public static class Input {
        public String role;
        public String content;
        public String name;
    }

    public Object ideal;
    public final List<Input> input = new ArrayList<>();

    public String getIdeal() {
        if (ideal instanceof String s) {
            return s;
        }
        else if (ideal instanceof List l) {
            final Object o = l.get(0);
            if (o instanceof String s1) {
                return s1;
            }
            throw new RuntimeException("Actual class: " + o.getClass().getName());
        }
        throw new RuntimeException("Not supported class: " + ideal.getClass().getName());
    }
}
