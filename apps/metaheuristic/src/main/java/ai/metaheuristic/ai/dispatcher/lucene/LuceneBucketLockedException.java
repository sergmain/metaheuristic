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
 * Thrown when a caller tries to start a rebuildAtomic on a bucket that already
 * has a rebuild in progress. The MH-side policy is reject-second-request; callers
 * decide how to surface this to the user.
 */
public class LuceneBucketLockedException extends RuntimeException {
    public LuceneBucketLockedException(String message) {
        super(message);
    }
}
