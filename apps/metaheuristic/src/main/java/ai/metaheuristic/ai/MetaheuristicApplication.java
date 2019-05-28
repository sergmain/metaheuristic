/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.lang.management.ManagementFactory;

@SpringBootApplication
@Slf4j
public class MetaheuristicApplication {

    private static ApplicationContext applicationContext = null;

    public static void main(String[] args) {
        String mode = args != null && args.length > 0 ? args[0] : null;

        log.debug("PID: {}, Application mode: {}, context: {}", ManagementFactory.getRuntimeMXBean().getName(), mode, applicationContext);
        if (applicationContext != null && "stop".equals(mode)) {
            System.exit(SpringApplication.exit(applicationContext, (ExitCodeGenerator) () -> 0));
        } else {
            SpringApplication app = new SpringApplication(MetaheuristicApplication.class);
            applicationContext = app.run(args!=null ? args : new String[0]);
            log.debug("PID: {}, Application started context: {}", ManagementFactory.getRuntimeMXBean().getName(), applicationContext);
        }
    }
}