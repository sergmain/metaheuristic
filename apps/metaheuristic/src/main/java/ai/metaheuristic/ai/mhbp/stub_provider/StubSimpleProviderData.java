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

package ai.metaheuristic.ai.mhbp.stub_provider;

import java.time.LocalDateTime;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 3:18 PM
 */
public class StubSimpleProviderData {
    public class QuestionToProvider {
        public long id;
        public long policyId;
        public String question;
    }

    public class AnswerFromProvider {
        public String guid;
        public String text;
        public String modelId;
        public LocalDateTime dateTime;

    }
}
