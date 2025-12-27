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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 3/24/2021
 * Time: 3:49 PM
 */
public class TaskLastProcessingHelper {

    @Nullable
    public static Long lastTaskId=null;

    // this code is only for testing
    public static boolean taskProcessed(Long id) {
        return id.equals(lastTaskId);
    }

    // this code is only for testing
    public static void resetLastTask() {
        lastTaskId=null;
    }


}
