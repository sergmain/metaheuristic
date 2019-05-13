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

package ai.metaheuristic.ai;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Monitoring {

    public static void log(String tag, Enums.Monitor ... monitors) {
        if (monitors==null) {
            throw new IllegalStateException("monitors is null");
        }
        if (isMemory(monitors)) {
            log.debug("{} mem free: {}, total: {}, max: {}", tag, Runtime.getRuntime().freeMemory(), Runtime.getRuntime().maxMemory(), Runtime.getRuntime().totalMemory());
        }
    }

    private static boolean isMemory(Enums.Monitor ... monitors) {
        for (Enums.Monitor monitor : monitors) {
            if (monitor== Enums.Monitor.MEMORY) {
                return true;
            }
        }
        return false;
    }
}
