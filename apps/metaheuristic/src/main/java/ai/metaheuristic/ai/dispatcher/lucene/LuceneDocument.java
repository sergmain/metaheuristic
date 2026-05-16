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

import java.util.Map;

/**
 * Caller-supplied input for indexing one document into a Lucene bucket.
 *
 * @param docId   opaque identifier the caller chose for this document. Stored verbatim and
 *                used as the update key (addOrUpdate uses {@code _docId} term to overwrite).
 *                Must be unique within a bucket.
 * @param fields  arbitrary field name -> value/kind map. See {@link LuceneFieldValue}.
 *                The bare {@code _docId} field name is reserved.
 */
public record LuceneDocument(String docId, Map<String, LuceneFieldValue> fields) {
}
