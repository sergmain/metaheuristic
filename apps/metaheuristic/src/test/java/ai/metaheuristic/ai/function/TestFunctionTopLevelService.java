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

package ai.metaheuristic.ai.function;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.function.FunctionTopLevelService;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 4/24/2022
 * Time: 2:25 AM
 */
public class TestFunctionTopLevelService {

    @Test
    public void isProcessorFunctionDownloadStatusDifferent() {

        List<Function> functions = new ArrayList<>();
        KeepAliveRequestParamYaml params = new KeepAliveRequestParamYaml();
        assertFalse(FunctionTopLevelService.isProcessorFunctionDownloadStatusDifferent(functions, params));

        final KeepAliveRequestParamYaml.Processor processorRequest = params.processor;
        processorRequest.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(42L, "123456", System.currentTimeMillis());
        assertFalse(FunctionTopLevelService.isProcessorFunctionDownloadStatusDifferent(functions, params));

        final KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status status = new KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status("func-1", EnumsApi.FunctionState.ok);
        params.functions.statuses.add(status);
        assertFalse(FunctionTopLevelService.isProcessorFunctionDownloadStatusDifferent(functions, params));

        final Function fx = new Function();
        fx.id = 2L;
        fx.version = 0;
        fx.code = "func-x";
        fx.type = "func-x-type";
        fx.params = "";
        functions.add(fx);

        assertFalse(FunctionTopLevelService.isProcessorFunctionDownloadStatusDifferent(functions, params));

        final Function f1 = new Function();
        f1.id = 3L;
        f1.version = 0;
        f1.code = "func-1";
        f1.type = "func-1-type";
        f1.params = "";
        functions.add(f1);
        assertTrue(FunctionTopLevelService.isProcessorFunctionDownloadStatusDifferent(functions, params));

    }
}
