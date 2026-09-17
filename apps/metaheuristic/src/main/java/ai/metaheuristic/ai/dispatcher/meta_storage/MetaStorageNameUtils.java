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

package ai.metaheuristic.ai.dispatcher.meta_storage;

import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * What a meta table may be called.
 *
 * <p>A meta table name is not an ordinary string: it is minted by whoever launches a workflow, it
 * ends up in a URL path segment on the browse screen, in MCP tool arguments, in log lines and in a
 * registry row that outlives the run. ❗ There is also no CREATE TABLE anywhere - a table exists
 * because records carry its name - so nothing downstream will ever reject a malformed one. A typo
 * does not fail; it silently creates a second table beside the intended one, and the records written
 * under it are found by nobody.
 *
 * <p>The rule: a letter, then letters, digits, hyphen, underscore and dot. Deliberately narrower than
 * what the column would accept, and the exclusions are the point rather than an accident of the
 * regex - no leading digit or dot (so a name is never mistaken for a number or a relative path), no
 * whitespace (so a name survives being logged, split on, or pasted into a shell), no slash or percent
 * (so it survives a URL path segment without escaping), no quote or backslash (so it survives JSON
 * and YAML unescaped).
 *
 * <p>Spring-free and JPA-free on purpose: the rule is worth exercising as plain functions, and the
 * classes that enforce it need a Spring context to instantiate.
 *
 * <p>Error code prefix: {@code 01.946.} (unique to this class).
 *
 * @author Serge
 */
public final class MetaStorageNameUtils {

    /**
     * ❗ Anchored at both ends. An unanchored pattern with {@code find()} semantics would accept a
     * name that merely CONTAINS something legal, which is the opposite of what is wanted here.
     */
    public static final Pattern META_TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9\\-_.]*$");

    private MetaStorageNameUtils() {
    }

    /** Null and blank are invalid rather than exceptional - they arrive from the same callers. */
    public static boolean isValidMetaTableName(@Nullable String name) {
        return name!=null && META_TABLE_NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Throws when the name would create a table nobody can address.
     *
     * <p>{@link IllegalArgumentException} rather than a returned status because every caller is a
     * write path and none of them has a meaningful way to continue: a rejected name means the
     * records have nowhere to go. The message quotes the offending name in full, since the usual
     * cause is an invisible character - a trailing space, a stray newline from a shell here-doc -
     * and a message that paraphrased it would hide exactly the thing to look at.
     */
    public static void validateMetaTableName(@Nullable String name) {
        if (!isValidMetaTableName(name)) {
            throw new IllegalArgumentException(
                    "01.946.020 Invalid meta table name: '" + name + "'. A name must start with a latin letter and "
                            + "may contain only latin letters, digits, hyphen, underscore and dot.");
        }
    }
}
