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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * Wires the meta storage as a library: a caller adds this jar and gets a {@link MetaStorageSpi}
 * bean. There is no {@code main}, no {@code @SpringBootApplication} and no repackaged fat jar here
 * - the host application owns the context.
 *
 * <p>The MCP tool bean ({@link MetaStorageMcpToolDefinitions}) is likewise only a supplier of tool
 * specifications; standing up the MCP transport stays with the host, which already does it in
 * {@code MhMcpServerConfig}.
 *
 * <p>Error code prefix: {@code 01.944.} (unique to this class).
 *
 * @author Serge
 */
@Configuration
@EnableConfigurationProperties(MetaStorageProperties.class)
@Slf4j
public class MetaStorageConfig {

    /**
     * SQLite over Hikari with the pool pinned to ONE connection.
     *
     * <p>Not a tuning choice. SQLite serialises writers, so a larger pool converts contention into
     * SQLITE_BUSY errors surfacing in the caller instead of a short wait inside the pool. Pinning it
     * here is what lets {@link MetaStorageSpi} promise that concurrency is not the caller's problem.
     */
    @Bean
    public DataSource metaStorageDataSource(MetaStorageProperties props) {
        final Path dbPath = Path.of(props.getDbPath()).toAbsolutePath();
        final HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbPath);
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(1);
        cfg.setPoolName("meta-storage");
        cfg.setAutoCommit(true);
        log.info("01.944.020 meta storage db: {}", dbPath);
        return new HikariDataSource(cfg);
    }

    @Bean
    public MetaStorageSpi metaStorageSpi(DataSource metaStorageDataSource) {
        final SqliteMetaStorageService service = new SqliteMetaStorageService(metaStorageDataSource);
        service.initSchema();
        return service;
    }

    @Bean
    public MetaStorageMcpToolDefinitions metaStorageMcpToolDefinitions(MetaStorageSpi metaStorageSpi) {
        return new MetaStorageMcpToolDefinitions(metaStorageSpi);
    }
}
