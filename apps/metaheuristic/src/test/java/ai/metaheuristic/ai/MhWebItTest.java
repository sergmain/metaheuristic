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

package ai.metaheuristic.ai;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * V3 base for web/security/MockMvc integration tests that CANNOT join the single shared pipeline
 * context (they fork it by design — {@code @MockitoBean}-mocked beans, test {@code @RestController}s,
 * etc.). Such a forked context still owns the full dispatcher (task-assigner + {@code TaskCheckCachingService}),
 * and if it shared {@link SharedItEnv#DB_URL} its schedulers would scan and mutate the pipeline tests'
 * tasks in the shared DB — e.g. advancing a CHECK_CACHE task that another context is holding via the
 * per-instance {@code TaskCheckCachingService.disableCacheChecking} flag (which can't hold across contexts).
 *
 * <p>So this base points such tests at an ISOLATED H2 ({@link SharedItEnv#WEB_DB_URL}); they don't need
 * the shared pipeline data (they mock the cache / hit security endpoints), and an empty isolated DB
 * keeps their schedulers from touching the canonical pipeline DB.
 *
 * @author Sergio Lissner
 */
@Slf4j
public abstract class MhWebItTest {

    @DynamicPropertySource
    static void webProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",  () -> SharedItEnv.WEB_DB_URL);
        r.add("mh.home",                () -> SharedItEnv.MH_HOME);
        r.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @Autowired(required = false) private ExecContextRepository execContextRepository;
    @Autowired(required = false) private ExecContextCache execContextCache;
    @Autowired(required = false) private ExecContextTopLevelService execContextTopLevelService;

    @AfterEach
    public void resetWebItStatePerTest() {
        try {
            stopNonFinishedExecContexts();
            MhSpi.cleanUpOnShutdown();
        }
        catch (Throwable th) {
            log.error("Error in resetWebItStatePerTest", th);
        }
    }

    protected void stopNonFinishedExecContexts() {
        if (execContextRepository == null || execContextCache == null || execContextTopLevelService == null) {
            return;
        }
        for (Long ecId : execContextRepository.findIdsByExecState(EnumsApi.ExecContextState.STARTED.code)) {
            try {
                ExecContextImpl ec = execContextCache.findById(ecId, true);
                if (ec == null) {
                    continue;
                }
                execContextTopLevelService.execContextTargetState(ecId, EnumsApi.ExecContextState.STOPPED, ec.companyId);
            }
            catch (Throwable th) {
                log.error("Error stopping leftover ExecContext #" + ecId, th);
            }
        }
    }
}
