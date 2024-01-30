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

package ai.metaheuristic.ai.mhbp.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author Sergio Lissner
 * Date: 4/14/2023
 * Time: 5:32 PM
 */
public class ExecData {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString(exclude = {"console"})
    public static class SystemExecResult {
        public String functionCode;
        public boolean isOk;
        public int exitCode;
        public String console;
    }

/*
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GitInfo {
        public String repo;
        // right now it'll be always as origin
//        public String remote;
        public String branch;
        public String commit;
    }
*/
}
