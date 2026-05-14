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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Signal kind — open-typed string carrier.
 *
 * Was an enum (BATCH, EXEC_CONTEXT, DOCUMENT_EXPORT, SYSTEM_NOTICE) up through
 * the early Signal Bus plans. Switched to a record carrying a single String so
 * that new kinds can be minted by callers without editing this type — the
 * closed enum forced an edit here every time a new kind was wanted.
 *
 * Well-known core kinds remain available as static constants so existing call
 * sites (SignalKind.BATCH, etc.) keep compiling unchanged. Validity of a kind
 * is decided dynamically at query time by {@link SignalKindRegistry}, not at
 * compile time.
 *
 * JSON wire format: a SignalKind serializes as the bare string ("BATCH"), not
 * as an object ({"kind":"BATCH"}) — preserves backwards compatibility with the
 * pre-record enum serialization and keeps the client's flat string type
 * accurate.
 *
 * <p><strong>Scope reminder:</strong> SignalKind constants exist only for
 * signals consumed by UI polling. For server-internal eventing (cache
 * invalidation, intra-Dispatcher fan-out, etc.) use plain Spring
 * {@code ApplicationEventPublisher} — do NOT add a new constant here. See
 * {@code SignalBus} class Javadoc for the rule.
 */
public record SignalKind(@JsonValue String kind) {

    @JsonCreator
    public SignalKind {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("668.005 SignalKind.kind must be non-blank");
        }
    }

    public static final SignalKind BATCH = new SignalKind("BATCH");
    public static final SignalKind EXEC_CONTEXT = new SignalKind("EXEC_CONTEXT");
    public static final SignalKind DOCUMENT_EXPORT = new SignalKind("DOCUMENT_EXPORT");
    public static final SignalKind SYSTEM_NOTICE = new SignalKind("SYSTEM_NOTICE");
}
