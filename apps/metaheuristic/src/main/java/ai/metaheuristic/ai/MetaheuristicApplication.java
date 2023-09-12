/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalDateTime;

@SpringBootApplication
@Slf4j
public class MetaheuristicApplication {

    public static String APP_UUID = Consts.APP_UUID_NONE;

    public static void main(String[] args) {
        initAppUuid(args);
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
            System.exit(-2);
        }
    }

    private static void initAppUuid(String[] args) {
        for (String arg : args) {
            if (arg.startsWith(Consts.UUID_ARG)) {
                String uuid = arg.substring(Consts.UUID_ARG.length());
                if (!S.b(uuid)) {
                    APP_UUID = uuid;
                }
            }
        }
    }
}
