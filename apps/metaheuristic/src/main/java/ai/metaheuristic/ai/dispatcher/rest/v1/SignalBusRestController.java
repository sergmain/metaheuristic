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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.signal_bus.GlobPattern;
import ai.metaheuristic.ai.dispatcher.signal_bus.QueryResult;
import ai.metaheuristic.ai.dispatcher.signal_bus.ScopeRef;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalBus;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalEntry;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalKind;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalKindRegistry;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalPollResponse;
import ai.metaheuristic.commons.account.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GET /rest/v1/dispatcher/signals — poll endpoint for the Signal Bus.
 * See docs/mh/signal-bus-02-rest-api.md.
 * Error code prefix: SIG.
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/signals")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SignalBusRestController {

    private static final int HARD_CAP = 2000;

    // DEBUG: per-process poll counter, increments on every arriving GET.
    private static final java.util.concurrent.atomic.AtomicLong __pollN =
        new java.util.concurrent.atomic.AtomicLong(0);

    private final SignalBus signalBus;
    private final SignalKindRegistry signalKindRegistry;
    private final UserContextService userContextService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public SignalPollResponse get(
            @RequestParam(defaultValue = "0") long afterRev,
            @RequestParam(required = false) String kinds,
            @RequestParam(required = false) String topics,
            @RequestParam(defaultValue = "500") int max,
            Authentication authentication) {

        long n = __pollN.incrementAndGet();
        long t0 = System.nanoTime();
        log.warn("SIG-DBG poll#{} ENTER afterRev={} kinds={} topics={} max={} principal={}",
            n, afterRev, kinds, topics, max,
            authentication != null ? authentication.getName() : "<null>");

        UserContext ctx = userContextService.getContext(authentication);
        if (!(ctx instanceof DispatcherContext dctx)) {
            SignalPollResponse err = new SignalPollResponse();
            err.addErrorMessage("SIG.100 (!(context instanceof DispatcherContext dispatcherContext))");
            log.warn("SIG-DBG poll#{} EXIT SIG.100 bad principal, dtMs={}", n, (System.nanoTime() - t0) / 1_000_000);
            return err;
        }

        ParseResult parsed = parseFilters(kinds, topics, signalKindRegistry);
        ScopeRef scope = new ScopeRef(dctx.getCompanyId());
        QueryResult qr = signalBus.query(scope, afterRev, parsed.kinds, parsed.topicGlobs);

        int effectiveCap = Math.min(Math.max(max, 0), HARD_CAP);
        List<SignalEntry> all = qr.signals();
        boolean truncated = all.size() > effectiveCap;
        List<SignalEntry> capped = truncated ? all.subList(0, effectiveCap) : all;

        SignalPollResponse response = new SignalPollResponse(
            qr.serverRev(), Instant.now(), capped, truncated);
        if (!parsed.warnings.isEmpty()) {
            parsed.warnings.forEach(response::addInfoMessage);
        }
        log.warn("SIG-DBG poll#{} EXIT companyId={} serverRev={} signals={} truncated={} warnings={} dtMs={}",
            n, dctx.getCompanyId(), qr.serverRev(), capped.size(), truncated,
            parsed.warnings.size(), (System.nanoTime() - t0) / 1_000_000);
        return response;
    }

    private static ParseResult parseFilters(String kinds, String topics, SignalKindRegistry registry) {
        // SignalKind is now an open record (was enum). Validity is decided by
        // membership in registry.knownKinds() — preserves the SIG.110 warning
        // for unknown kinds without keeping the kind universe closed at MH.
        Set<SignalKind> known = registry.knownKinds();
        Set<SignalKind> parsedKinds = new HashSet<>();
        List<String> warnings = new ArrayList<>();

        if (kinds != null && !kinds.isBlank()) {
            for (String raw : kinds.split(",")) {
                String k = raw.trim();
                if (k.isEmpty()) continue;
                SignalKind candidate = new SignalKind(k);
                if (known.contains(candidate)) {
                    parsedKinds.add(candidate);
                } else {
                    warnings.add("SIG.110 unknown kind ignored: " + k);
                }
            }
        }

        List<GlobPattern> topicGlobs = new ArrayList<>();
        if (topics != null && !topics.isBlank()) {
            for (String raw : topics.split(",")) {
                String t = raw.trim();
                if (t.isEmpty()) continue;
                try {
                    topicGlobs.add(new GlobPattern(t));
                } catch (RuntimeException e) {
                    warnings.add("SIG.120 unparseable topic glob ignored: " + t);
                }
            }
        }

        // Empty kinds → SignalBus.query treats as "all kinds"; preserve that semantics
        Set<SignalKind> effectiveKinds = parsedKinds.isEmpty() ? null : parsedKinds;
        return new ParseResult(effectiveKinds, topicGlobs, Collections.unmodifiableList(warnings));
    }

    private record ParseResult(Set<SignalKind> kinds, List<GlobPattern> topicGlobs, List<String> warnings) {}
}
