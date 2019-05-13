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

package ai.metaheuristic.ai.comm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 19:07
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(value = {"launchpadUrl"})
public class Command implements Serializable {
    public String stationId;
    public String launchpadUrl;

    private Type type;
    private Map<String, String> params = new HashMap<>();
    private Map<String, String> response = new HashMap<>();

    public enum Type {
        Nop /* nop operation */,
        ReportStation /*  */,
        RequestStationId /*  */,
        AssignedStationId /*  */,
        ReAssignStationId /*  */,
        RequestTask /*  */,
        AssignedTask /*  */,
        ReportStationStatus /* returned after processing AssignedStationId and ReAssignStationId */,
        ReportTaskProcessingResult /* returned result of processing sequence at station */,
        ReportResultDelivering, /* results were delivered to the launchpad */
        WorkbookStatus, /* current exec status of experiments sent by launchpad every iteration with station */
        StationTaskStatus, /* sent by station, contains all active taskId which is present as station side*/
        CheckForMissingOutputResources, /*  */
        ResendTaskOutputResource, /* Task was completed but output resource wasn't received. so we request it*/
        ResendTaskOutputResourceResult;
    }

}
