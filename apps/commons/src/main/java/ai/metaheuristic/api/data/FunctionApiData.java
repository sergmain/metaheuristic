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

package ai.metaheuristic.api.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * @author Serge
 * Date: 5/10/2019
 * Time: 2:14 AM
 */
public class FunctionApiData {

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionExec {
        public SystemExecResult exec = new SystemExecResult();
        public List<SystemExecResult> preExecs;
        public List<SystemExecResult> postExecs;
        public @Nullable SystemExecResult generalExec;

        public boolean allFunctionsAreOk() {
            if (exec==null || !exec.isOk) {
                return false;
            }
            if (generalExec!=null && !generalExec.isOk) {
                return false;
            }
            if (preExecs!=null) {
                for (SystemExecResult preExec : preExecs) {
                    if (!preExec.isOk) {
                        return false;
                    }
                }
            }
            if (postExecs!=null) {
                for (SystemExecResult postExec : postExecs) {
                    if (!postExec.isOk) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FunctionConfigStatus {
        public boolean isOk;
        public String error;
    }

}
