/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai;

import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Auto-configuration that discovers and registers Liquibase changelogs from all modules via SPI.
 * 
 * Each module (including main app) can provide changelogs by creating:
 * META-INF/liquibase-changelogs.txt
 * 
 * This file should contain changelog paths, one per line (e.g., classpath:database/changelog.yaml).
 * Lines starting with # are treated as comments.
 * 
 * This replaces Spring Boot's default LiquibaseAutoConfiguration (spring.liquibase.enabled=false).
 *
 * @author Serge
 * Date: 12/27/2024
 */
@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(SpringLiquibase.class)
@Import(MhLiquibaseAutoConfiguration.LiquibaseChangelogRegistrar.class)
public class MhLiquibaseAutoConfiguration {

    public static class LiquibaseChangelogRegistrar implements ImportBeanDefinitionRegistrar {

        private static final Logger log = LoggerFactory.getLogger(LiquibaseChangelogRegistrar.class);

        private static final String CHANGELOGS_LOCATION = "classpath*:META-INF/liquibase-changelogs.txt";

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            log.info("LiquibaseChangelogRegistrar: scanning for Liquibase changelogs");

            List<String> allChangelogs = new ArrayList<>();

            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = resolver.getResources(CHANGELOGS_LOCATION);

                log.info("Found {} liquibase-changelogs.txt files", resources.length);

                for (Resource resource : resources) {
                    log.info("Reading changelogs from: {}", resource.getURL());
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (!line.isEmpty() && !line.startsWith("#")) {
                                allChangelogs.add(line);
                                log.info("  Found changelog: {}", line);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error reading Liquibase changelogs", e);
            }

            if (allChangelogs.isEmpty()) {
                log.warn("LiquibaseChangelogRegistrar: no changelogs found!");
                return;
            }

            // Register a bean for each changelog
            // First changelog gets the standard name, others are numbered
            String previousBeanName = null;
            for (int index = 0; index < allChangelogs.size(); index++) {
                String changelog = allChangelogs.get(index);
                String beanName = index == 0 ? "springLiquibase" : "springLiquibase_" + index;
                
                BeanDefinitionBuilder builder = BeanDefinitionBuilder
                        .genericBeanDefinition(SpringLiquibase.class)
                        .addPropertyReference("dataSource", "dataSource")
                        .addPropertyValue("changeLog", changelog)
                        .addPropertyValue("shouldRun", true);
                
                // Each subsequent changelog depends on the previous one
                if (previousBeanName != null) {
                    builder.setDependsOn(previousBeanName);
                }
                
                BeanDefinition beanDefinition = builder.getBeanDefinition();
                
                registry.registerBeanDefinition(beanName, beanDefinition);
                log.info("Registered Liquibase bean '{}' for changelog: {}", beanName, changelog);
                
                previousBeanName = beanName;
            }

            log.info("LiquibaseChangelogRegistrar: completed, registered {} changelogs", allChangelogs.size());
        }
    }
}
