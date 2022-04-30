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

import ai.metaheuristic.ai.Enums;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 4/26/2020
 * Time: 6:18 PM
 */
public class StringVariableData {

    @Data
    @AllArgsConstructor
    public static class StringVariableItem {
        public final Map<String, String> inlines;
        public final String inlineKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class StringAsVar {
        // use key instead
        @Nullable
        @Deprecated
        public String group;
        @Nullable
        public String key;
        public String name;
        public String output;
        @Nullable
        public Enums.StringAsVariableSource source = Enums.StringAsVariableSource.inline;

        public StringAsVar(@Nullable String group, @Nullable String key, String name, String output) {
            this.group = group;
            this.key = key;
            this.name = name;
            this.output = output;
        }
    }

    @Data
    @NoArgsConstructor
    public static class Mapping {
        public final List<StringAsVar> mapping = new ArrayList<>();
    }
}
