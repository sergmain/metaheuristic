/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.signal_bus;

import ai.metaheuristic.api.data.BaseDataClass;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Response envelope for GET /rest/v1/dispatcher/signals.
 *
 * serverRev  — watermark for the client to echo back via afterRev next poll.
 * polledAt   — server time; helpful for logging/debugging.
 * signals    — entries matching the query, sorted by revision ascending.
 * truncated  — true iff the result was capped by {@code max} or the hard cap (2000).
 * Inherits errorMessages / infoMessages from BaseDataClass. infoMessages
 * carries warnings like SIG.110 (unknown kind ignored) and SIG.120
 * (unparseable topic glob ignored).
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class SignalPollResponse extends BaseDataClass {
    public long serverRev;
    public @Nullable Instant polledAt;
    public @Nullable List<SignalEntry> signals;
    public boolean truncated;

    @JsonCreator
    public SignalPollResponse(
        @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
        @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
        this.errorMessages = errorMessages;
        this.infoMessages = infoMessages;
    }
}
