/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 1/6/2022
 * Time: 4:04 PM
 */
public class PreparingData {
    @Data
    public static class PreparingCodeData {
        public Processor processor = null;
        public String processorIdAsStr;

        @Nullable
        public Function fitFunction = null;
        @Nullable
        public Function predictFunction = null;
    }

    @Data
    public static class PreparingSourceCodeData {
        public SourceCodeImpl sourceCode = null;
        public Company company;
        public Function f1 = null;
        public Function f2 = null;
        public Function f3 = null;
        public Function f4 = null;
        public Function f5 = null;
        public GlobalVariable testGlobalVariable;
        public ExecContextParamsYaml execContextYaml;
        public ExecContextImpl execContextForTest = null;
    }
}
