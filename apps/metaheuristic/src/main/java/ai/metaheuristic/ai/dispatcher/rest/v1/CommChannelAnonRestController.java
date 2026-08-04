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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.comm_channel.CommChannelData;
import ai.metaheuristic.ai.dispatcher.comm_channel.CommChannelTxService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * Anonymous activation of a communication-channel token.
 *
 * <p>Mounted under {@code /rest/v1/dispatcher/anon}, which is already
 * {@code permitAll}. That placement is the whole point: activation necessarily
 * happens BEFORE the outside party holds any credential, so requiring
 * authentication would make the token unusable by the only party that has it.
 *
 * <p><b>POST with the token in the BODY, never in the path or query string.</b>
 * A URL is written to access logs, proxy logs, browser history and
 * {@code Referer} headers as a matter of course. An unactivated token authorizes
 * minting an account, so putting it in a URL leaks a live credential into half a
 * dozen places nobody audits — and it stays live, because a token is only spent
 * once someone uses it.
 *
 * <p>The token is never written to a log by this class or by
 * {@link CommChannelTxService}, and {@code CommChannel} excludes it from
 * {@code toString()}.
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/anon/comm-channel")
@Profile("dispatcher")
@CrossOrigin
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class CommChannelAnonRestController {

    private final CommChannelTxService commChannelTxService;

    /** {@code toString()} must never render the token — this object reaches error paths. */
    @Data
    @NoArgsConstructor
    @ToString(exclude = {"token"})
    public static class ActivateRequest {
        @Nullable
        public String token;
    }

    /**
     * Activate a token and receive the credential once.
     *
     * <p>Always HTTP 200, success or refusal. A distinct status code for a
     * refusal is machine-readable evidence about whether a guessed token
     * existed, which is the same oracle the opaque error message exists to deny.
     */
    @PostMapping("/activate")
    public CommChannelData.ActivatedChannel activate(@RequestBody ActivateRequest request) {
        return commChannelTxService.activate(request.token==null ? "" : request.token);
    }
}
