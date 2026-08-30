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

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The one {@code @SpringBootApplication} for the module's tests - the analogue of MH's
 * {@code MhComplexTestConfig}.
 *
 * <p>Scanning {@code ai.metaheuristic.meta_storage} picks up {@code MetaStorageConfig}, the entity
 * and the repository, and lets Boot auto-configure the single {@code DataSource}, JPA and Liquibase
 * from the properties {@code MetaStorageSharedItTest} supplies.
 *
 * @author Serge
 */
@SpringBootApplication(scanBasePackages = {"ai.metaheuristic.meta_storage"})
public class MetaStorageTestConfig {
}
