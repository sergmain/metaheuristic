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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
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
 * Registers JPA entity packages from all modules using SPI mechanism.
 * 
 * Each module (including plugins) can provide entity packages by creating:
 * META-INF/jpa-entity-packages.txt
 * 
 * This file should contain package names, one per line.
 * Lines starting with # are treated as comments.
 * 
 * This registrar collects ALL such files from classpath and registers
 * all packages with EntityScanPackages.
 *
 * @author Serge
 * Date: 12/27/2024
 */
public class MhEntityPackageRegistrar implements ImportBeanDefinitionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(MhEntityPackageRegistrar.class);

    private static final String ENTITY_PACKAGES_LOCATION = "classpath*:META-INF/jpa-entity-packages.txt";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        log.info("MhEntityPackageRegistrar: scanning for entity packages");
        
        List<String> allPackages = new ArrayList<>();
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(ENTITY_PACKAGES_LOCATION);
            
            log.info("Found {} jpa-entity-packages.txt files", resources.length);
            
            for (Resource resource : resources) {
                log.info("Reading entity packages from: {}", resource.getURL());
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            allPackages.add(line);
                            log.info("  Found package: {}", line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error reading entity packages", e);
        }
        
        if (!allPackages.isEmpty()) {
            log.info("Registering {} entity packages with EntityScanPackages", allPackages.size());
            EntityScanPackages.register(registry, allPackages.toArray(new String[0]));
        } else {
            log.warn("No entity packages found!");
        }
        
        log.info("MhEntityPackageRegistrar: completed");
    }
}
