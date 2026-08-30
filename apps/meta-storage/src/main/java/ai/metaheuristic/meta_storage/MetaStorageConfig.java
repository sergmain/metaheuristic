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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the meta storage as a library: a caller adds this jar and gets a {@link MetaStorageSpi}
 * bean. There is no {@code main}, no {@code @SpringBootApplication} and no repackaged fat jar here -
 * the host application owns the context.
 *
 * <p>❗ <b>This configuration publishes NO {@code DataSource} bean, deliberately.</b> The host's
 * database - the one Liquibase migrates and Hibernate maps, including
 * {@code MetaStorageStubRepository} - is the single auto-configured {@code DataSource}. The SQLite
 * pool behind {@link MetaStorageSpi} is private to {@link SqliteMetaStorageService}.
 *
 * <p>Two Hikari pools in one JVM are ordinary; two {@code DataSource} BEANS are not. Boot's
 * {@code DataSourceAutoConfiguration} is {@code @ConditionalOnMissingBean(DataSource.class)}, and
 * JPA, the transaction manager and Liquibase all resolve {@code DataSource} by type - so a second
 * bean either suppresses the real database or leaves them choosing blindly between two. Keeping the
 * SQLite pool out of the context removes the question rather than answering it with
 * {@code @Primary}.
 *
 * <p>⚠️ A consequence worth stating because it is invisible at the call site: the SQLite pool runs
 * with {@code autoCommit=true} and is bound to no {@code PlatformTransactionManager}. A meta-storage
 * write inside a {@code @Transactional} method commits immediately and is NOT rolled back when that
 * transaction rolls back.
 *
 * <p>The MCP tool bean is likewise only a supplier of tool specifications; standing up the MCP
 * transport stays with the host, which already does it in {@code MhMcpServerConfig}.
 *
 * <p>Error code prefix: {@code 01.944.} (unique to this class).
 *
 * @author Serge
 */
@Configuration
@EnableConfigurationProperties(MetaStorageProperties.class)
@Slf4j
public class MetaStorageConfig {

    @Bean
    public MetaStorageSpi metaStorageSpi(MetaStorageProperties props) {
        final SqliteMetaStorageService service = new SqliteMetaStorageService(props);
        service.initSchema();
        log.info("01.944.020 meta storage SPI is ready");
        return service;
    }

    @Bean
    public MetaStorageMcpToolDefinitions metaStorageMcpToolDefinitions(MetaStorageSpi metaStorageSpi) {
        return new MetaStorageMcpToolDefinitions(metaStorageSpi);
    }
}
