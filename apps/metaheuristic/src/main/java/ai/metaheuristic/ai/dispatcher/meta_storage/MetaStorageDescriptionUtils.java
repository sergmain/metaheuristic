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

import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import ai.metaheuristic.commons.json.meta_storage.MetaStorageRegistryParamsUtils;
import org.jspecify.annotations.Nullable;

/**
 * Editing the description of one meta table, as plain functions.
 *
 * <p>The description is {@code desc} in the table's registry descriptor - one field of several. An
 * edit replaces that field and nothing else: the producer, the recKey and body formats, the
 * ExecContext and the consumer protocol were written by whoever produced the table, and a human
 * correcting one sentence on the browse screen has no business erasing them.
 *
 * <p>Spring-free and JPA-free, so the rule and the copy are exercised directly.
 *
 * <p>Error code prefix: {@code 01.954.} (unique to this class).
 *
 * @author Serge
 */
public final class MetaStorageDescriptionUtils {

    /**
     * Upper bound on a description, in characters. The description is rendered in a table cell on
     * every load of the index, and is meant to be a sentence or two - this keeps one form submit from
     * turning the index into a document. PARAMS itself is a CLOB, so the bound is a choice about the
     * screen, not about the column.
     */
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private MetaStorageDescriptionUtils() {
    }

    /**
     * @return null when {@code description} may be stored, otherwise why not
     */
    @Nullable
    public static String checkDescription(@Nullable String description) {
        // Blank is refused rather than stored: the index renders a blank desc as the "no description"
        // placeholder anyway, so storing one would look to the user like the edit did nothing.
        if (description==null || description.isBlank()) {
            return "01.954.020 A description can't be empty.";
        }
        final String stripped = description.strip();
        if (stripped.length() > MAX_DESCRIPTION_LENGTH) {
            return "01.954.040 A description can be at most " + MAX_DESCRIPTION_LENGTH + " characters, this one has "
                    + stripped.length() + ".";
        }
        return null;
    }

    /**
     * The params to store: every field of {@code existing}, with {@code desc} replaced by the stripped
     * {@code description}. With no existing descriptor, params carrying the description only.
     *
     * <p>❗ The copy goes through the registry's multi-versioning base field - serialize, parse back -
     * rather than through a field-by-field assignment. A field added to the params in a later version
     * is then carried across without this method being touched; a hand-written copy would silently
     * drop it on the first edit of a description.
     *
     * <p>{@code existing} is not modified: it may be the instance cached on the entity.
     */
    public static MetaStorageRegistryParams withDescription(@Nullable MetaStorageRegistryParams existing, String description) {
        final MetaStorageRegistryParams p = existing==null
                ? new MetaStorageRegistryParams()
                : MetaStorageRegistryParamsUtils.BASE_JSON_UTILS.to(MetaStorageRegistryParamsUtils.BASE_JSON_UTILS.toString(existing));
        p.desc = description.strip();
        return p;
    }
}
