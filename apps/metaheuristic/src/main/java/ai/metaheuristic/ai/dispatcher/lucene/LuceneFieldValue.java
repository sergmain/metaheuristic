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

package ai.metaheuristic.ai.dispatcher.lucene;

/**
 * Generic representation of a Lucene field value used by {@link LuceneIndexService}.
 *
 * Three kinds are exposed:
 *  - ANALYZED:  full-text analyzed field (TextField). Tokenized, used for free-text search.
 *  - KEYWORD:   non-tokenized indexed field (StringField). Exact match only, useful for
 *               filter columns like project codes, tags, statuses.
 *  - STORED:    not indexed, only retained on the document so callers can read it back
 *               on hits (the typical use is the row's primary id).
 *
 * MH knows nothing about the business meaning of any field — what is analyzed vs keyword
 * vs stored is purely the caller's decision.
 *
 * @param value the value to store
 * @param kind  how Lucene should treat it
 */
public record LuceneFieldValue(String value, Kind kind) {

    public enum Kind { ANALYZED, KEYWORD, STORED }

    public static LuceneFieldValue analyzed(String value) {
        return new LuceneFieldValue(value, Kind.ANALYZED);
    }

    public static LuceneFieldValue keyword(String value) {
        return new LuceneFieldValue(value, Kind.KEYWORD);
    }

    public static LuceneFieldValue stored(String value) {
        return new LuceneFieldValue(value, Kind.STORED);
    }
}
