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

package ai.metaheuristic.ai.standalone;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.commons.CommonConsts;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * @author Sergio Lissner
 * Date: 8/31/2023
 * Time: 11:43 PM
 */
@Slf4j
@Service
@Profile("standalone & !disable-check-frontend")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FrontendCheckerService {

    private final ApplicationContext appCtx;
    private final Globals globals;

    private Request request = null;
    private int errors = 0;

    @SneakyThrows
    public void checkFrontend() {

        if (request==null) {
            URI uri = new URIBuilder(CommonConsts.FRONTEND_URL+"/ping").setCharset(StandardCharsets.UTF_8).build();
            request = Request.get(uri).connectTimeout(Timeout.ofSeconds(1));
        }
        final Executor executor = Executor.newInstance();
        try {
            Response response = executor.execute(request);
            final int code = response.returnResponse().getCode();
            if (code>=200 && code<=500) {
                errors = 0;
            }
        }
        catch (IOException e) {
            errors++;
            log.error("367.040 checkFrontend, {}", e.toString());
        }
        catch (Throwable th) {
            log.error("367.080 ConnectException", th);
        }
        if (errors>2) {
            globals.state.shutdownInProgress = true;
            log.warn("367.120 Front-end wasn't found, shutdown server-side");
            System.exit(SpringApplication.exit(appCtx, () -> -100));
        }
    }
}
