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

package ai.metaheuristic.meta_storage_rdbms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * V3 test harness base for the single shared Spring context - the meta-storage copy of MH's
 * {@code MhSharedItTest}.
 *
 * <p>It declares the ONE {@code @DynamicPropertySource} that wires every test to the single
 * {@link MetaStorageSharedItEnv} H2 file DB, {@code mh.home} and the Liquibase changelog. Every subclass resolves the SAME inherited {@code Method}, so Spring builds
 * one {@code DynamicPropertiesContextCustomizer} -> one cached ApplicationContext -> one DB for the
 * whole run.
 *
 * <p>❗ A per-class {@code @DynamicPropertySource}, even a byte-identical one, is a distinct
 * {@code Method} and therefore a distinct context-cache key - a fresh context and a fresh DB per
 * class, which is exactly what V3 removes. Same for {@code @DirtiesContext}: it forks the cache key
 * too. Neither belongs in a subclass.
 *
 * @author Serge
 */
@Slf4j
public abstract class MetaStorageSharedItTest {

    @DynamicPropertySource
    static void sharedProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",        () -> MetaStorageSharedItEnv.DB_URL);
        r.add("spring.datasource.username",   () -> "sa");
        r.add("spring.datasource.password",   () -> "");
        r.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        r.add("spring.liquibase.enabled",     () -> "true");
        r.add("spring.liquibase.change-log",  () -> "classpath:database/meta-storage-rdbms/meta-storage-rdbms-changelog.yaml");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        r.add("mh.home",                      () -> MetaStorageSharedItEnv.MH_HOME);
    }
}
