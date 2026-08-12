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

package ai.metaheuristic.ai.dispatcher.license;

import ai.metaheuristic.commons.spi.license.DeploymentValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The one place profile expressions meet the license manager. Spring-less: mapping a set of
 * strings to two strings needs no context to decide and none to test.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class DeploymentValuesResolverTest {

    @Test
    public void test_postgresqlProfile() {
        assertEquals(DeploymentValuesResolver.POSTGRES,
                DeploymentValuesResolver.database(Set.of("dispatcher", "postgresql")));
    }

    @Test
    public void test_mysqlAndMariadbBothMapToOneValue() {
        // MH selects one bean with @Profile({"dispatcher & (mysql | mariadb)"}), so a license
        // granting MYSQL must cover a MariaDB deployment too - they are one datasource choice.
        assertEquals(DeploymentValuesResolver.MYSQL,
                DeploymentValuesResolver.database(Set.of("dispatcher", "mysql")));
        assertEquals(DeploymentValuesResolver.MYSQL,
                DeploymentValuesResolver.database(Set.of("dispatcher", "mariadb")));
    }

    @Test
    public void test_noDatasourceProfile_isTheEmbeddedDefault() {
        // H2 IS the default, so there is no "unknown database" case - inventing one would produce
        // a deployment no license could ever cover.
        assertEquals(DeploymentValuesResolver.H2, DeploymentValuesResolver.database(Set.of("dispatcher")));
        assertEquals(DeploymentValuesResolver.H2, DeploymentValuesResolver.database(Set.of()));
    }

    @Test
    public void test_conflictingDatasourceProfiles_areDeterministic() {
        // a misconfiguration Spring would already reject; the answer is at least stable.
        assertEquals(DeploymentValuesResolver.POSTGRES,
                DeploymentValuesResolver.database(Set.of("dispatcher", "postgresql", "mysql")));
    }

    @Test
    public void test_s3_needsBothProfiles() {
        assertEquals(DeploymentValuesResolver.S3,
                DeploymentValuesResolver.storage(Set.of("dispatcher", "external-storage", "s3-storage")));
        assertNull(DeploymentValuesResolver.storage(Set.of("dispatcher", "external-storage")));
        assertNull(DeploymentValuesResolver.storage(Set.of("dispatcher", "s3-storage")));
    }

    @Test
    public void test_noStorageProfile_isNullNotAnEmptyValue() {
        // null means "nothing in use, so nothing to license" - which is NOT the same as running on
        // a storage backend no license grants. The union only checks the axis when a value exists.
        assertNull(DeploymentValuesResolver.storage(Set.of("dispatcher")));
    }

    @Test
    public void test_resolve_combinesBothAxes() {
        final DeploymentValues d = DeploymentValuesResolver.resolve(
                Set.of("dispatcher", "postgresql", "external-storage", "s3-storage"));

        assertEquals(DeploymentValuesResolver.POSTGRES, d.database());
        assertEquals(DeploymentValuesResolver.S3, d.storage());
    }

    @Test
    public void test_resolve_defaultDeployment() {
        final DeploymentValues d = DeploymentValuesResolver.resolve(Set.of("dispatcher"));

        assertEquals(DeploymentValuesResolver.H2, d.database());
        assertNull(d.storage());
    }
}
