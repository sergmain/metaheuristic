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

package ai.metaheuristic.ai.standalone.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Sergio Lissner
 * Date: 7/2/2023
 * Time: 9:51 PM
 */
@RestController
@RequestMapping("/rest/v1/standalone/anon")
@Slf4j
@Profile("standalone")
@CrossOrigin
public class StandaloneAnonRestController {

    public static final String SHUTDOWN_IN_PROGRESS = "Shutdown in progress ...";
    private final ApplicationContext appCtx;

    public StandaloneAnonRestController(@Autowired ApplicationContext appCtx) {
        this.appCtx = appCtx;
    }

    @GetMapping("/ping")
    public String ping() {
        return "Metaheuristic";
    }

    @GetMapping("/shutdown")
    public String shutdown() {
        new Thread(()-> {
            log.warn(SHUTDOWN_IN_PROGRESS);
            try {
                Thread.sleep(500);
            } catch (Throwable th) {
                //
            }
            System.exit(SpringApplication.exit(appCtx, () -> 0));
        }).start();
        return SHUTDOWN_IN_PROGRESS;
    }
}
