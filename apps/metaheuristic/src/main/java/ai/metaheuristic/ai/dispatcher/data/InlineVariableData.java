/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 4/26/2020
 * Time: 6:18 PM
 */
public class InlineVariableData {

    @Data
    @AllArgsConstructor
    public static class InlineVariableItem {
        public final Map<String, String> inlines;
        public final String inlineKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class InlineAsVar {
        public String group;
        public String name;
        public String output;
    }

    @Data
    @NoArgsConstructor
    public static class Mapping {
        public final List<InlineAsVar> mapping = new ArrayList<>();
    }
}
