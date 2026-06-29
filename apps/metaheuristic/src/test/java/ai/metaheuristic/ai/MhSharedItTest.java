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
 * V3 test harness base for the single shared Spring context.
 *
 * <p>It declares the ONE {@code @DynamicPropertySource} (sharedProps) that wires every test to the
 * single {@link SharedItEnv} H2 file DB + {@code mh.home} + the dispatcher profile. Both
 * {@code PreparingCore} (pipeline tests) and standalone {@code @SpringBootTest} classes extend this,
 * so every subclass resolves the SAME inherited {@code Method} -> Spring builds one
 * {@code DynamicPropertiesContextCustomizer} -> one cached ApplicationContext + one DB for the whole
 * run. A per-class {@code @DynamicPropertySource} (even a byte-identical one) would be a distinct
 * {@code Method} -> a distinct context-cache key -> a fresh context per class, which is exactly what
 * V3 removes.
 *
 * @author Sergio Lissner
 */
@Slf4j
public abstract class MhSharedItTest {

    @DynamicPropertySource
    static void sharedProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",  () -> SharedItEnv.DB_URL);
        r.add("mh.home",                () -> SharedItEnv.MH_HOME);
        r.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @Autowired(required = false) private ExecContextRepository execContextRepository;
    @Autowired(required = false) private ExecContextCache execContextCache;
    @Autowired(required = false) private ExecContextTopLevelService execContextTopLevelService;

    // V3: per-test reset for EVERY main-group test (PreparingCore-based AND standalone classes
    // like the MockMvc / *ControllerTest ones). Under the single never-recreated context + shared
    // DB, a class that doesn't stop its STARTED ExecContexts and clear caches leaves state that
    // accumulates and is 'stolen' by a later test's pipeline driver. Centralizing it on the shared
    // base guarantees no class can skip it.
    @AfterEach
    public void resetSharedItStatePerTest() {
        try {
            stopNonFinishedExecContexts();
            MhSpi.cleanUpOnShutdown();
        }
        catch (Throwable th) {
            log.error("Error in resetSharedItStatePerTest", th);
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
