/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

/**
 * @author Serge
 * Date: 4/7/2020
 * Time: 6:01 PM
 */
public class ContextUtils {

    public static final String CONTEXT_SEPARATOR = "###";

    public static String getTaskContextId(String processContextId, String subContext) {
        return processContextId + CONTEXT_SEPARATOR + subContext;
    }

    public static String getWithoutSubContext(String taskContextId) {
        int idx = taskContextId.indexOf(CONTEXT_SEPARATOR);
        return idx==-1 ?  taskContextId : taskContextId.substring(0, idx);
    }
}
