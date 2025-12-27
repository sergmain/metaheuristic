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

package ai.metaheuristic.ai.mhbp.data;

import ai.metaheuristic.api.data.BaseDataClass;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.lang.Nullable;

/**
 * @author Sergio Lissner
 * Date: 3/26/2023
 * Time: 3:00 AM
 */
public class SessionData {

    public record SessionStatus(
            long sessionId, long startedOn, @Nullable Long finishedOn, String sessionStatus,
            @Nullable String safe,
            float normalPercent, float failPercent, float errorPercent,
            String providerCode, String apiInfo, long evaluationId, String chapters) {
    }

    @RequiredArgsConstructor
    public static class SessionStatuses extends BaseDataClass {
        public final Slice<SessionStatus> sessions;
    }
}
