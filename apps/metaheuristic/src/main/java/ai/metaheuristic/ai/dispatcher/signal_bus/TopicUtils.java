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

import java.util.regex.Pattern;

/**
 * Utility helpers for topic derivation.
 * stripVersion removes a trailing semver suffix from a SourceCode uid:
 * "mhdg-rg-flat-1.0.0" → "mhdg-rg-flat". Subscribers want to filter on the
 * uid prefix (all versions of a workflow), not the specific version.
 */
public final class TopicUtils {

    // anchored at end: "-N.N.N" where each N is one or more digits
    private static final Pattern VERSION_SUFFIX = Pattern.compile("-\\d+\\.\\d+\\.\\d+$");

    private TopicUtils() {}

    public static String stripVersion(String uid) {
        return VERSION_SUFFIX.matcher(uid).replaceFirst("");
    }
}
