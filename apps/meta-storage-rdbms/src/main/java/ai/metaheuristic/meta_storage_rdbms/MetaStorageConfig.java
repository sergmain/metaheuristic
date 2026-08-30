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

import ai.metaheuristic.meta_storage_rdbms.repositories.MetaStorageRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the meta storage as a library: a caller adds this jar and gets a {@link MetaStorageSpi}
 * bean. There is no {@code main}, no {@code @SpringBootApplication} and no repackaged fat jar here -
 * the host application owns the context.
 *
 * <p>❗ <b>No {@code DataSource} bean, no embedded engine, no second connection pool.</b> The store
 * lives in the host's own database, on the single auto-configured {@code DataSource}, reached
 * through JPA. That removes, by construction, the whole class of problems the SQLite variant has to
 * manage:
 *
 * <ul>
 *   <li>no second {@code DataSource} bean, so {@code DataSourceAutoConfiguration}, the JPA
 *       {@code EntityManagerFactory}, the transaction manager and {@code SpringLiquibase} each have
 *       exactly one candidate to resolve by type;</li>
 *   <li>no dual write - a meta-storage write joins the caller's transaction and rolls back with
 *       it;</li>
 *   <li>no native library, so no {@code --enable-native-access} flag, no extract-to-temp, no
 *       AppLocker or {@code noexec} exposure, and nothing extra for a certification reviewer;</li>
 *   <li>no hand-written dialect SQL - Hibernate 7.4 ships dialects for H2, MySQL, MariaDB and
 *       PostgreSQL and none for SQLite;</li>
 *   <li>one backup and restore procedure rather than two, and the pool is monitored like every
 *       other pool.</li>
 * </ul>
 *
 * <p>⚠️ What it gives up: meta-storage rows share a database with the host's own data, so isolating
 * their volume means pointing this at a separate schema or database rather than a separate file.
 *
 * <p>The MCP tool bean is only a supplier of tool specifications; standing up the MCP transport
 * stays with the host, which already does it in {@code MhMcpServerConfig}.
 *
 * <p>Error code prefix: {@code 01.944.} (unique to this class).
 *
 * @author Serge
 */
@Configuration
@Slf4j
public class MetaStorageConfig {

    @Bean
    public MetaStorageSpi metaStorageSpi(MetaStorageRecordRepository metaStorageRecordRepository,
                                         JpaMetaStorageTxService jpaMetaStorageTxService) {
        log.info("01.944.020 meta storage SPI is ready (RDBMS/JPA, no embedded engine)");
        return new JpaMetaStorageService(metaStorageRecordRepository, jpaMetaStorageTxService);
    }

    @Bean
    public MetaStorageMcpToolDefinitions metaStorageMcpToolDefinitions(MetaStorageSpi metaStorageSpi) {
        return new MetaStorageMcpToolDefinitions(metaStorageSpi);
    }
}
