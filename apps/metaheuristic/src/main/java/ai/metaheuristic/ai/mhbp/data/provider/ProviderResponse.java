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

package ai.metaheuristic.ai.mhbp.data.provider;

import ai.metaheuristic.ai.Enums;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

/**
 * @author Sergio Lissner
 * Date: 3/5/2023
 * Time: 1:24 AM
 */
public class ProviderResponse {
    public String providerCode;
    public long requestId;
    public long sessionId;
    public String text;
    public LocalDateTime dateTime;
    public boolean safe;
    @Nullable
    public String modelId;

    public Enums.ResponseType type;

    @Nullable
    // will be inited in async manner
    public Long questionId;
}
