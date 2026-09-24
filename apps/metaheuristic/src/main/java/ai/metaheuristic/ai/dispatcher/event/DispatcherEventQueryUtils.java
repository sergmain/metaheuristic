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

import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import ai.metaheuristic.commons.yaml.event.DispatcherEventYamlUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * Reading dispatcher events (MH_EVENT) for analysis - the part that needs no database.
 *
 * <p>An event's type (EVENT) and month (PERIOD) are columns, so the database selects by them. Its contextId is
 * not: it lives inside the stored document (PARAMS). {@link #scan} therefore walks the selected events in id
 * order, parses each one, keeps those of the asked-for contextId, and says where to continue - bounded per call,
 * because the few events of one contextId can sit among very many others.
 *
 * <p>The database is reached only through the function passed to {@link #scan}, so all of this runs Spring-less.
 */
public final class DispatcherEventQueryUtils {

    private DispatcherEventQueryUtils() {}

    /** An event as selected from MH_EVENT, before its stored document is parsed. */
    public record StoredEvent(long id, int period, String event, @Nullable Long companyId, @Nullable String params) {}

    /** One event type of a range of months: how many events it has, and the first and last month it occurs in. */
    public record EventTypeStat(String event, long count, int firstPeriod, int lastPeriod) {}

    /**
     * One event as returned for analysis. {@code params} is the event's own payload ({@link DispatcherEventYaml#params}),
     * not the stored document. When the stored document can't be read, {@code error} says why and the fields parsed
     * from it are null.
     */
    public record EventRecord(long id, int period, String event, @Nullable Long companyId, @Nullable String createdOn,
                              @Nullable String contextId, @Nullable String params,
                              DispatcherEventYaml.@Nullable BatchEventData batchData,
                              DispatcherEventYaml.@Nullable TaskEventData taskData,
                              @Nullable String error) {}

    /**
     * A page of events. {@code nextAfterId} is the id of the last event read, whether it was returned or not - the
     * next page starts after it. {@code complete} is true only when nothing after it was left unread.
     */
    public record EventPage(List<EventRecord> events, int scanned, long nextAfterId, boolean complete) {}

    /** A month as yyyyMM; anything else is rejected, naming the parameter. */
    public static int toPeriod(String name, long value) {
        final long month = value % 100;
        if (value < 100001 || value > 999912 || month < 1 || month > 12) {
            throw new IllegalArgumentException("Parameter '" + name + "' must be a month as yyyyMM, e.g. 202609, was: " + value);
        }
        return (int) value;
    }

    public static void requireRange(int fromPeriod, int toPeriod) {
        if (fromPeriod > toPeriod) {
            throw new IllegalArgumentException("fromPeriod " + fromPeriod + " is after toPeriod " + toPeriod);
        }
    }

    /**
     * The event types a query covers. null - every type - when neither exact types nor a prefix is given; otherwise
     * the union of the exact types and the types in range that start with the prefix, compared literally. An empty
     * result means that no type matches, which is not the same as null. {@code typesInRange} is read only for a prefix.
     */
    public static @Nullable Set<String> resolveEventTypes(
            @Nullable Collection<String> eventTypes, @Nullable String eventTypePrefix, Supplier<List<String>> typesInRange) {
        final boolean noExactTypes = eventTypes == null || eventTypes.isEmpty();
        final boolean noPrefix = eventTypePrefix == null || eventTypePrefix.isBlank();
        if (noExactTypes && noPrefix) {
            return null;
        }
        final Set<String> result = new TreeSet<>();
        if (eventTypes != null) {
            result.addAll(eventTypes);
        }
        if (eventTypePrefix != null && !eventTypePrefix.isBlank()) {
            final String prefix = eventTypePrefix;
            typesInRange.get().stream().filter(t -> t.startsWith(prefix)).forEach(result::add);
        }
        return result;
    }

    /** An event for analysis. A stored document that can't be read is reported in {@code error}, never dropped. */
    public static EventRecord toRecord(StoredEvent e) {
        final String stored = e.params();
        if (stored == null || stored.isBlank()) {
            return new EventRecord(e.id(), e.period(), e.event(), e.companyId(), null, null, null, null, null,
                    "the stored document is blank");
        }
        try {
            final DispatcherEventYaml y = DispatcherEventYamlUtils.BASE_YAML_UTILS.to(stored);
            return new EventRecord(e.id(), e.period(), e.event(), e.companyId(), y.createdOn, y.contextId, y.params,
                    y.batchData, y.taskData, null);
        }
        catch (Throwable th) {
            return new EventRecord(e.id(), e.period(), e.event(), e.companyId(), null, null, null, null, null,
                    th.getClass().getSimpleName() + ": " + th.getMessage());
        }
    }

    /**
     * Reads the events after {@code afterId} in id order, a chunk at a time, and returns those of {@code contextId} -
     * all of them when it is null - until {@code limit} are found, the events run out, or {@code maxScanned} have
     * been read. {@code fetchAfter} returns at most {@code chunkSize} events with an id greater than its argument,
     * in id order; fewer than {@code chunkSize} means there are no more.
     */
    public static EventPage scan(long afterId, int limit, @Nullable String contextId, int chunkSize, int maxScanned,
                                 LongFunction<List<StoredEvent>> fetchAfter) {
        final List<EventRecord> events = new ArrayList<>();
        long cursor = afterId;
        int scanned = 0;
        while (true) {
            final List<StoredEvent> chunk = fetchAfter.apply(cursor);
            for (StoredEvent e : chunk) {
                scanned++;
                cursor = e.id();
                final EventRecord r = toRecord(e);
                if (contextId == null || contextId.equals(r.contextId())) {
                    events.add(r);
                    if (events.size() >= limit) {
                        return new EventPage(events, scanned, cursor, false);
                    }
                }
            }
            if (chunk.size() < chunkSize) {
                return new EventPage(events, scanned, cursor, true);
            }
            if (scanned >= maxScanned) {
                return new EventPage(events, scanned, cursor, false);
            }
        }
    }
}
