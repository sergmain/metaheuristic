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

import org.springframework.util.AntPathMatcher;

/**
 * Thin wrapper around Spring's {@link AntPathMatcher} for topic matching.
 * Topics use dot separators instead of slashes; constructor rewrites '.' → '/'
 * for the underlying matcher.
 */
public final class GlobPattern {

    private static final AntPathMatcher MATCHER;
    static {
        AntPathMatcher m = new AntPathMatcher();
        m.setPathSeparator(".");
        MATCHER = m;
    }

    private final String pattern;

    public GlobPattern(String pattern) {
        this.pattern = pattern;
    }

    public boolean matches(String topic) {
        return MATCHER.match(pattern, topic);
    }

    public String pattern() {
        return pattern;
    }
}
