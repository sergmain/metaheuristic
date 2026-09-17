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
     *
     * <p>Deliberately says nothing about length - {@link #META_TABLE_NAME_MAX_LENGTH} does, separately,
     * so a rejection can say WHICH of the two rules was broken. Folding {@code {0,49}} in here would
     * make a too-long name and an illegal character the same failure to the caller.
     */
    public static final Pattern META_TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9\\-_.]*$");

    /**
     * The width of every column that stores a meta table name: {@code MH_META_STORAGE.TYPE},
     * {@code MH_META_STORAGE_SYNTHETIC.TYPE} and {@code MH_META_STORAGE_REGISTRY.META_TABLE} are all
     * {@code VARCHAR(50)}. ❗ Widening those columns means changing this, and vice versa - the two
     * numbers are one fact.
     *
     * <p>Why the check is here and not left to the database: the dialects disagree about what happens
     * next, and one of the answers is silent. H2 and PostgreSQL reject the insert; MySQL, outside
     * strict mode, TRUNCATES. A truncated name is the bad case - the records go in under the first 50
     * characters, the descriptor goes in under the same truncated name, and nothing anywhere reports
     * that the name the caller used is not the name the data is under.
     *
     * <p>⚠️ VARCHAR counts characters rather than bytes in these engines, and this check counts
     * characters too. That is only safe because the pattern above already excludes everything
     * non-ASCII, so one character is one byte here and the two measures cannot diverge. Relax the
     * charset and this bound stops matching the column.
     */
    public static final int META_TABLE_NAME_MAX_LENGTH = 50;

    private MetaStorageNameUtils() {
    }

    /** Null and blank are invalid rather than exceptional - they arrive from the same callers. */
    public static boolean isValidMetaTableName(@Nullable String name) {
        return name!=null
                && name.length() <= META_TABLE_NAME_MAX_LENGTH
                && META_TABLE_NAME_PATTERN.matcher(name).matches();
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
        if (name==null || !META_TABLE_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "01.946.020 Invalid meta table name: '" + name + "'. A name must start with a latin letter and "
                            + "may contain only latin letters, digits, hyphen, underscore and dot.");
        }
        // Checked second, so a name that breaks both rules is reported on the more fundamental one.
        if (name.length() > META_TABLE_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "01.946.040 Meta table name is too long: '" + name + "' is " + name.length()
                            + " characters, the maximum is " + META_TABLE_NAME_MAX_LENGTH + ".");
        }
    }
}
