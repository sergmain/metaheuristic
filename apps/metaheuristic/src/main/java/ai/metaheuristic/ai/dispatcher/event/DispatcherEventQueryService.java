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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.dispatcher.event.DispatcherEventQueryUtils.EventPage;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventQueryUtils.EventTypeStat;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventQueryUtils.StoredEvent;
import ai.metaheuristic.ai.dispatcher.repositories.DispatcherEventRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Reads dispatcher events (MH_EVENT) for analysis: which event types a range of months holds, and the events
 * themselves, filtered by type and contextId and paged by id. Read-only.
 *
 * <p>Events are selected as plain values, not loaded as {@code DispatcherEvent} entities, so an analysis read
 * neither populates the second-level cache nor carries entity state. Everything that needs no database is in
 * {@link DispatcherEventQueryUtils}.
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class DispatcherEventQueryService {

    /** Events read per database round trip when a contextId filter drops some of them. */
    public static final int CHUNK_SIZE = 500;
    /** The most events one call reads while looking for a contextId. */
    public static final int MAX_SCANNED = 20_000;

    private final DispatcherEventRepository dispatcherEventRepository;

    public List<EventTypeStat> listEventTypes(int fromPeriod, int toPeriod) {
        return dispatcherEventRepository.countByEventInPeriods(fromPeriod, toPeriod).stream()
                .map(o -> new EventTypeStat((String) o[0], ((Number) o[1]).longValue(),
                        ((Number) o[2]).intValue(), ((Number) o[3]).intValue()))
                .toList();
    }

    public EventPage listEvents(int fromPeriod, int toPeriod, @Nullable Collection<String> eventTypes,
                                @Nullable String eventTypePrefix, @Nullable String contextId, long afterId, int limit) {
        final Set<String> events = DispatcherEventQueryUtils.resolveEventTypes(eventTypes, eventTypePrefix,
                () -> listEventTypes(fromPeriod, toPeriod).stream().map(EventTypeStat::event).toList());
        if (events != null && events.isEmpty()) {
            return new EventPage(List.of(), 0, afterId, true);
        }
        // without a contextId every event read is returned, so one round trip of exactly `limit` is enough
        final int chunkSize = contextId == null ? limit : CHUNK_SIZE;
        final PageRequest chunk = PageRequest.ofSize(chunkSize);
        return DispatcherEventQueryUtils.scan(afterId, limit, contextId, chunkSize, MAX_SCANNED,
                cursor -> toStored(events == null
                        ? dispatcherEventRepository.findStoredAfter(cursor, fromPeriod, toPeriod, chunk)
                        : dispatcherEventRepository.findStoredAfterOfEvents(cursor, fromPeriod, toPeriod, events, chunk)));
    }

    private static List<StoredEvent> toStored(List<Object[]> selected) {
        return selected.stream()
                .map(o -> new StoredEvent(((Number) o[0]).longValue(), ((Number) o[1]).intValue(), (String) o[2],
                        o[3] == null ? null : ((Number) o[3]).longValue(), (String) o[4]))
                .toList();
    }
}
