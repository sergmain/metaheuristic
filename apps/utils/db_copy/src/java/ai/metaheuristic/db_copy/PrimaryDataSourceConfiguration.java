/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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
package ai.metaheuristic.db_copy;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;
import jakarta.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager",
        basePackages = {"ai.metaheuristic.db_copy.primary.repo"})
public class PrimaryDataSourceConfiguration {

    @Primary
    @Bean(name = "primaryDataSourceProperties")
    @ConfigurationProperties("spring.datasource-primary")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "primaryDataSource")
    @ConfigurationProperties("spring.datasource-primary.configuration")
    public DataSource primaryDataSource(@Qualifier("primaryDataSourceProperties") DataSourceProperties primaryDataSourceProperties) {
        return primaryDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Primary
    @Bean(name = "primaryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            EntityManagerFactoryBuilder primaryEntityManagerFactoryBuilder, @Qualifier("primaryDataSource") DataSource primaryDataSource) {

        Map<String, String> primaryJpaProperties = new HashMap<>();
        primaryJpaProperties.put("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
//        primaryJpaProperties.put("hibernate.hbm2ddl.auto", "create-drop");

        return primaryEntityManagerFactoryBuilder
                .dataSource(primaryDataSource)
                .packages("ai.metaheuristic.db_copy.primary.beans")
                .persistenceUnit("primaryDataSource")
                .properties(primaryJpaProperties)
                .build();
    }

    @Primary
    @Bean(name = "primaryTransactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory primaryEntityManagerFactory) {

        return new JpaTransactionManager(primaryEntityManagerFactory);
    }
}
