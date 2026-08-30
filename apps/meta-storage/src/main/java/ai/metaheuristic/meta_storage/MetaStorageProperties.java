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

package ai.metaheuristic.meta_storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the meta storage, bound from {@code mh.meta-storage.*}.
 *
 * <p>The store is a file the product owns, creates and can delete and rebuild. Nothing in the
 * customer's own database is touched by it, which is why it may create its own schema at startup.
 *
 * @author Serge
 */
@Data
@ConfigurationProperties(prefix = "mh.meta-storage")
public class MetaStorageProperties {

    /** Path to the SQLite file. Relative paths resolve against the working directory. */
    private String dbPath = "meta-storage.sqlite";
}
