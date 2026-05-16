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
 * Single search hit returned by {@link LuceneIndexService#search}.
 *
 * @param docId         the value that was passed to addOrUpdate as docId
 * @param score         relevance score
 * @param storedFields  values of any STORED fields on the matched document; ANALYZED/KEYWORD
 *                      values are not retrieved (re-hydrate from your DB if needed)
 */
public record LuceneHit(String docId, float score, Map<String, String> storedFields) {
}
