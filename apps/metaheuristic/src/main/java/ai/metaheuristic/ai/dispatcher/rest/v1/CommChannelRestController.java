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

import ai.metaheuristic.ai.dispatcher.beans.CommChannel;
import ai.metaheuristic.ai.dispatcher.comm_channel.CommChannelData;
import ai.metaheuristic.ai.dispatcher.comm_channel.CommChannelServiceRegistry;
import ai.metaheuristic.ai.dispatcher.comm_channel.CommChannelTokenUtils;
import ai.metaheuristic.ai.dispatcher.comm_channel.CommChannelTxService;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.repositories.CommChannelRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.account.CommChannelServiceProvider.CommChannelService;
import ai.metaheuristic.commons.account.UserContext;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * Administration of communication channels: list services, issue, revoke.
 *
 * <p>Every operation is scoped to the CALLER'S OWN company, taken from the
 * authenticated principal and never from a request parameter, so an admin of one
 * company cannot issue a channel into another's tenancy and there is no
 * parameter through which they could try.
 *
 * <p>Activation is deliberately NOT here: the outside party has no credential at
 * that point by definition, so it lives on the anonymous surface.
 *
 * <p>Error code prefix: {@code 01.241.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/comm-channel")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@PreAuthorize("hasAnyRole('ADMIN')")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class CommChannelRestController {

    private final CommChannelTxService commChannelTxService;
    private final CommChannelServiceRegistry serviceRegistry;
    private final CommChannelRepository commChannelRepository;
    private final UserContextService userContextService;

    @Data
    @NoArgsConstructor
    public static class NewChannelRequest {
        /** Service tag from {@link #services()}. */
        @Nullable
        public String serviceTag;
        /** Who this token is meant for — audit only, gates nothing. */
        @Nullable
        public String intendedFor;
        @Nullable
        public String description;
        /** Time-to-live in hours; falls back to the default when absent or non-positive. */
        @Nullable
        public Integer ttlHours;
    }

    /** The services this deployment offers channels to. */
    @GetMapping("/services")
    public List<CommChannelService> services() {
        return serviceRegistry.getServices();
    }

    @PostMapping("/channel-issue-commit")
    public CommChannelData.IssuedChannel issue(@RequestBody NewChannelRequest request, Authentication authentication) {
        final UserContext context = userContextService.getContext(authentication);

        if (request.serviceTag==null || request.serviceTag.isBlank()) {
            return CommChannelData.IssuedChannel.error("01.241.020 serviceTag must not be blank");
        }
        final CommChannelService service = serviceRegistry.findByTag(request.serviceTag);
        if (service==null) {
            return CommChannelData.IssuedChannel.error(
                    "01.241.040 Unknown service: " + request.serviceTag);
        }

        final long ttlMillis = request.ttlHours==null || request.ttlHours <= 0
                ? CommChannelTokenUtils.DEFAULT_TTL_MILLIS
                : request.ttlHours * 3600_000L;

        return commChannelTxService.issue(
                context.getCompanyId(), service, context.getAccountId(),
                request.intendedFor, request.description, ttlMillis);
    }

    @PostMapping("/channel-revoke-commit/{channelId}")
    public OperationStatusRest revoke(@PathVariable Long channelId, Authentication authentication) {
        final UserContext context = userContextService.getContext(authentication);

        final CommChannel channel = commChannelRepository.findById(channelId).orElse(null);
        if (channel==null || !Objects.equals(channel.companyId, context.getCompanyId())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "01.241.060 Channel wasn't found, channelId: " + channelId);
        }
        return commChannelTxService.revoke(channel);
    }
}
