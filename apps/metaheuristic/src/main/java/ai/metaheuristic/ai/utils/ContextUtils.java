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

package ai.metaheuristic.ai.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.thymeleaf.util.StringUtils;

/**
 * @author Serge
 * Date: 4/7/2020
 * Time: 6:01 PM
 */
@Slf4j
public class ContextUtils {

    public static final String CONTEXT_SEPARATOR = "#";
    public static final char CONTEXT_DIGIT_SEPARATOR = ',';

    public static String getTaskContextId(String processContextId, String subContext) {
        return processContextId + CONTEXT_SEPARATOR + subContext;
    }

    public static String getWithoutSubContext(String taskContextId) {
        int idx = taskContextId.indexOf(CONTEXT_SEPARATOR);
        return idx==-1 ?  taskContextId : taskContextId.substring(0, idx);
    }

    @Nullable
    public static String getSubContext(String taskContextId) {
        if (StringUtils.contains(taskContextId, CONTEXT_SEPARATOR)) {
            return taskContextId.substring(taskContextId.indexOf(CONTEXT_SEPARATOR)+CONTEXT_SEPARATOR.length());
        }
        return null;
    }

    public static String getCurrTaskContextIdForSubProcesses(Long taskId, String currTaskContextId, String processContextId) {
        String subProcessContextId = processContextId;
        String subContext = ContextUtils.getSubContext(currTaskContextId);
        if (subContext!=null) {
            subProcessContextId = subProcessContextId + ContextUtils.CONTEXT_DIGIT_SEPARATOR + subContext;
        }
        else {
            log.warn("#971.020 subContext wasn't found for task #{}, taskContextId: {}. Resulted processContextId: {}", taskId, currTaskContextId, processContextId);
        }
        return subProcessContextId;
    }
}
