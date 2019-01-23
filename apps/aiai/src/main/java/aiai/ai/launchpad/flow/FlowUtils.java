/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.flow;

public class FlowUtils {

    public static String getResourcePoolCode(String flowCode, long flowId, String processCode, int idx) {
        return String.format("%s-%d-%d-%s", flowCode, flowId, idx, processCode);
    }

    public static String getResourceCode(long flowId, long flowInstanceId, String processCode, String snippetName, int idx) {
        return String.format("%d-%d-%d-%s-%s", flowId, flowInstanceId, idx, snippetName, processCode);
    }
}
