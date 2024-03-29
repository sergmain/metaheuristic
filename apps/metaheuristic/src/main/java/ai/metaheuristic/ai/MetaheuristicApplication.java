/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.standalone.StatusFileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalDateTime;

import static ai.metaheuristic.ai.MetaheuristicStatus.APP_UUID;
import static ai.metaheuristic.ai.MetaheuristicStatus.initAppStatus;

@SpringBootApplication
@Slf4j
public class MetaheuristicApplication {

    public static void main(String[] args) {
        initAppStatus(args);
        System.out.println("Metaheuristic was started at " + LocalDateTime.now()+", uuid: " + APP_UUID);
        final String encoding = System.getProperty("file.encoding");
        if (!StringUtils.equalsAnyIgnoreCase(encoding, "utf8", "utf-8")) {
            System.out.println("Must be run with -Dfile.encoding=UTF-8, actual file.encoding: " + encoding);
            System.exit(-1);
        }
        try {
            SpringApplication.run(MetaheuristicApplication.class, args);
        } catch (Throwable th) {
            log.error("error", th);
            StatusFileUtils.appendError(MetaheuristicStatus.metaheuristicStatusFilePath, th);
            System.exit(-2);
        }
    }

}
