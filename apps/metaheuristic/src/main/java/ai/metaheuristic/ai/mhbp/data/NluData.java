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

package ai.metaheuristic.ai.mhbp.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 9:14 PM
 */
public class NluData {

    @Data
    @AllArgsConstructor
    public static class Property {
        public String name;
        public String value;
    }

    @Data
    @AllArgsConstructor
    public static class QueriedPrompt {
        public String text;
    }

    @Data
    @EqualsAndHashCode
    public static class ProcessedQuery {
        public QueriedPrompt prompt;
        public String text;
    }
}
