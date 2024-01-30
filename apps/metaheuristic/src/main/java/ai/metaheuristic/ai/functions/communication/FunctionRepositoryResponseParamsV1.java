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

package ai.metaheuristic.ai.functions.communication;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.GitInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 11/15/2023
 * Time: 7:09 PM
 */
@Data
public class FunctionRepositoryResponseParamsV1 implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    public static class ShortFunctionConfigV1 {
        public String code;
        public EnumsApi.FunctionSourcing sourcing;
        @javax.annotation.Nullable
        public GitInfo git;
    }

    // list of function codes which must to be prepared and ready
    @Nullable
    @JsonInclude(value= JsonInclude.Include.NON_NULL)
    public List<ShortFunctionConfigV1> functions = null;

    public boolean success;

}
