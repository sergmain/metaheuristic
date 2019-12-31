/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.data;

import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.api.data.SimpleSelectOption;
import ai.metaheuristic.api.data.BaseDataClass;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

public class SnippetData {


    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class SnippetsResult extends BaseDataClass {
        public List<Snippet> snippets;
    }

    @Data
    public static class SnippetResult {
        public List<SimpleSelectOption> selectOptions = new ArrayList<>();
        public List<String> snippets = new ArrayList<>();

    }

}
