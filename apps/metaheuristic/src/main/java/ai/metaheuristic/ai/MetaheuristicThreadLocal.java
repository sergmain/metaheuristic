/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai;

import lombok.AllArgsConstructor;

/**
 * @author Serge
 * Date: 12/17/2020
 * Time: 8:19 PM
 */
@AllArgsConstructor
public class MetaheuristicThreadLocal {

    public static final ThreadLocal<Boolean> schedule = ThreadLocal.withInitial(()->false);

    public static void setSchedule() {
        schedule.set(true);
    }

    public static void checkScheduler() {
        if (!schedule.get()) {
            throw new IllegalStateException("THis method must be used only in scheduler");
        }
    }
}
