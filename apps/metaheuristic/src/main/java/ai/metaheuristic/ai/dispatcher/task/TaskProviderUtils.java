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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.event.events.TaskFinishWithErrorEvent;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Sergio Lissner
 * Date: 5/26/2023
 * Time: 1:20 PM
 */
@Slf4j
public class TaskProviderUtils {

    @Nullable
    public static String initEmptiness(
            Long coreId, int taskParamsVersion, String taskParams, Long taskId,
            Function<Long, Variable> variableFunction, Consumer<TaskFinishWithErrorEvent> eventPublisherFunc) {
        String params;
        try {
            TaskParamsYaml tpy = TaskParamsYamlUtils.UTILS.to(taskParams);

            for (TaskParamsYaml.InputVariable input : tpy.task.inputs) {
                if (input.context!=EnumsApi.VariableContext.global) {
                    Variable sv = variableFunction.apply(input.id);
                    if (sv==null) {
                        final String es = S.f("211.120 Can't find a %s variable %s, #%d", input.context.toString(), input.name, input.id);
                        log.error(es);
                        eventPublisherFunc.accept(new TaskFinishWithErrorEvent(taskId, es));
                        return null;
                    }
                    input.empty = sv.nullified;
                }
            }
            params = TaskParamsYamlUtils.UTILS.toStringAsVersion(tpy, taskParamsVersion);

        } catch (DowngradeNotSupportedException e) {
            log.warn("211.600 Task #{} can't be assigned to core #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                    taskId, coreId, taskParamsVersion);
            return null;
        }
        return params;
    }
}
