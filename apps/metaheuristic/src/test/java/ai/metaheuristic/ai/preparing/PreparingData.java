/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import org.jspecify.annotations.Nullable;

/**
 * @author Serge
 * Date: 1/6/2022
 * Time: 4:04 PM
 */
public class PreparingData {

    @Data
    public static class ProcessorIdAndCoreIds {
        public final Long processorId;
        public final String sessionId;
        public final Long coreId1;
        public final Long coreId2;
    }

    @Data
    public static class PreparingCodeData {
        public Processor processor = null;
        public ProcessorCore core1 = null;
        public ProcessorCore core2 = null;

        @Nullable
        public Function fitFunction = null;
        @Nullable
        public Function predictFunction = null;
    }

    @Data
    public static class PreparingSourceCodeData {
        public @Nullable SourceCodeImpl sourceCode = null;
        public Company company;
        public Account account;
        public @Nullable Function f1 = null;
        public @Nullable Function f2 = null;
        public @Nullable Function f3 = null;
        public @Nullable Function f4 = null;
        public @Nullable Function f5 = null;
        public GlobalVariable testGlobalVariable;
        public ExecContextParamsYaml execContextYaml;
        public @Nullable ExecContextImpl execContextForTest = null;
    }
}
