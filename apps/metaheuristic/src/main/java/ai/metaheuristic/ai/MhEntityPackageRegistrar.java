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
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registers Metaheuristic entity packages with Spring Boot's EntityScanPackages.
 * 
 * This replaces @EntityScan annotation to enable plugin architecture.
 * EntityScanPackages.register() is ADDITIVE, while @EntityScan REPLACES all packages.
 * 
 * Plugins can add their own entity packages by also using EntityScanPackages.register()
 * in their own ImportBeanDefinitionRegistrar.
 *
 * @author Serge
 * Date: 12/27/2024
 */
public class MhEntityPackageRegistrar implements ImportBeanDefinitionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(MhEntityPackageRegistrar.class);

    private static final String[] ENTITY_PACKAGES = {
        "ai.metaheuristic.ai.dispatcher.beans",
        "ai.metaheuristic.ai.mhbp.beans"
    };

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        log.info("MhEntityPackageRegistrar.registerBeanDefinitions() called");
        
        for (String pkg : ENTITY_PACKAGES) {
            log.info("Registering entity package: {}", pkg);
            EntityScanPackages.register(registry, pkg);
        }
        
        log.info("MhEntityPackageRegistrar completed registration");
    }
}
