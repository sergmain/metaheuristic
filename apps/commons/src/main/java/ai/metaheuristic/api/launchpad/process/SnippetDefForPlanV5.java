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

package ai.metaheuristic.api.launchpad.process;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Serge
 * Date: 6/19/2019
 * Time: 1:02 PM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnippetDefForPlanV5 {
    public String code;
    public String params;

    // TODO 2019-11-03 instead of this field
    //  there is meta ai.metaheuristic.api.ConstsApi.META_MH_SNIPPET_PARAMS_AS_FILE_META
    @Deprecated(forRemoval = true)
    public boolean paramsAsFile;

    public SnippetDefForPlanV5(String code) {
        this.code = code;
    }
}
