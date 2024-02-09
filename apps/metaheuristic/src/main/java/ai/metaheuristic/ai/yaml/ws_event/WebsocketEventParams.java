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

package ai.metaheuristic.ai.yaml.ws_event;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Sergio Lissner
 * Date: 2/9/2024
 * Time: 3:46 PM
 */
@Data
@NoArgsConstructor
public class WebsocketEventParams implements BaseParams {

    public final int version=1;

    public Enums.WebsocketEventType type;

    @Nullable
    public List<String> functions;

}
