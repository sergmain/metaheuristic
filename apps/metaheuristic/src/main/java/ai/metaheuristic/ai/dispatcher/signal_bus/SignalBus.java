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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * In-memory store for ephemeral signals. Thread-safe via a RW lock.
 * Identity is (kind, signalId); a new publish overwrites the existing entry.
 * See docs/mh/signal-bus-01-architecture.md §7.
 */
public class SignalBus {

    private final Map<SignalKind, Map<String, SignalEntry>> snapshot = new HashMap<>();
    private final AtomicLong revisionCounter = new AtomicLong(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final SignalKindRegistry registry;
    private final Clock clock;

    public SignalBus(SignalKindRegistry registry) {
        this(registry, Clock.systemUTC());
    }

    public SignalBus(SignalKindRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    public void put(SignalKind kind, String signalId, ScopeRef scope,
                    Map<String, Object> info, boolean terminal) {
        lock.writeLock().lock();
        try {
            Map<String, SignalEntry> inner = snapshot.computeIfAbsent(
                kind, k -> new HashMap<>());

            SignalEntry existing = inner.get(signalId);
            Instant nowInstant = Instant.now(clock);

            // Coalescing: in-window non-terminal publishes update info in place
            // without bumping the revision (pollers do not see the intermediate).
            // Terminal publishes always push through — see §7.2.
            if (shouldCoalesce(kind, existing, nowInstant, terminal)) {
                String existingTopic = existing.topic();
                inner.put(signalId, new SignalEntry(
                    existing.kind(), existing.signalId(), existing.revision(),
                    nowInstant, existing.scope(), info, existing.terminal(),
                    existingTopic));
                return;
            }

            long rev = revisionCounter.incrementAndGet();
            String topic = registry.topicBuilderFor(kind).build(kind, signalId, info);
            inner.put(signalId, new SignalEntry(
                kind, signalId, rev, nowInstant, scope, info, terminal, topic));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove all entries whose createdAt is strictly before {@code cutoff}.
     * Called by {@link SignalBusSweeper} on a fixed schedule.
     */
    public void evictOlderThan(java.time.Instant cutoff) {
        lock.writeLock().lock();
        try {
            snapshot.values().forEach(inner ->
                inner.values().removeIf(e -> e.createdAt().isBefore(cutoff)));
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean shouldCoalesce(SignalKind kind, SignalEntry existing,
                                   Instant now, boolean terminal) {
        if (existing == null) return false;
        if (terminal) return false;       // terminal pushes through
        Duration minInterval = registry.coalescePolicyFor(kind).minInterval();
        if (minInterval.isZero() || minInterval.isNegative()) return false;
        Duration sincePrev = Duration.between(existing.createdAt(), now);
        return sincePrev.compareTo(minInterval) < 0;
    }

    public QueryResult query(ScopeRef scope, long afterRev,
                             Set<SignalKind> kinds, List<GlobPattern> topics) {
        lock.readLock().lock();
        try {
            long highWater = revisionCounter.get();
            Set<SignalKind> effectiveKinds = (kinds == null || kinds.isEmpty())
                ? registry.knownKinds() : kinds;

            List<SignalEntry> out = effectiveKinds.parallelStream()
                .map(snapshot::get)
                .filter(Objects::nonNull)
                .flatMap(inner -> inner.values().parallelStream())
                .filter(e -> e.revision() > afterRev && e.revision() <= highWater)
                .filter(e -> scopeMatches(e.scope(), scope))
                .filter(e -> topicMatches(e.topic(), topics))
                .sorted(Comparator.comparingLong(SignalEntry::revision))
                .collect(Collectors.toList());

            return new QueryResult(out, highWater);
        } finally {
            lock.readLock().unlock();
        }
    }

    private static boolean scopeMatches(ScopeRef entryScope, ScopeRef principalScope) {
        // v1: match iff companyId matches
        return entryScope.companyId() == principalScope.companyId();
    }

    private static boolean topicMatches(String topic, List<GlobPattern> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        for (GlobPattern p : patterns) {
            if (p.matches(topic)) {
                return true;
            }
        }
        return false;
    }
}
