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
package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.account.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.jspecify.annotations.Nullable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public abstract class PreparingCore {

    @DynamicPropertySource
    static void sharedProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",  () -> SharedItEnv.DB_URL);
        r.add("mh.home",                () -> SharedItEnv.MH_HOME);
        r.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    public record SourceCodeUriAndLang(String uri, EnumsApi.SourceCodeLang lang, @Nullable String sourceCode) {}
    public record SourceCodeAndLang(String sourceCode, EnumsApi.SourceCodeLang lang) {}

    @Autowired private Globals globals;
    @Autowired private PreparingCoreInitService preparingCoreService;
    @Autowired private ExecContextRepository execContextRepository;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextTopLevelService execContextTopLevelService;

    /**
     * Result of the infra phase: company + account + registered functions +
     * validated SourceCode. Carries everything the business-logic phase needs
     * to create an ExecContext on top.
     */
    public record Infra(
        Company company,
        Account account,
        List<Function> functions
    ) {
        public UserContext asUserContext() {
            var ctx = new UserContext() {
                public Long getAccountId() {
                    return account.getId();
                }
                public Long getCompanyId() {
                    return company.getUniqueId();
                }
                public String getUsername() {
                    return account.getUsername();
                }
            };
            return ctx;
        }
    }
    public static @Nullable Infra sharedInfra = null;

    // V3: infra (processor + cores + functions) is built ONCE and reused across the whole
    // run; never deleted, never nulled. Per-test isolation is by unique codes + stopping
    // non-finished ExecContexts, not by per-test deletion.
    public static PreparingData.@Nullable PreparingCodeData sharedCodeData = null;
//    public abstract SourceCodeUriAndLang getSourceCodeAndLang();

    public abstract SourceCodeUriAndLang getSourceCodeAndLang();

    public PreparingData.PreparingCodeData preparingCodeData;

    public Processor getProcessor() {
        return preparingCodeData.processor;
    }

    public ProcessorCore getCore1() {
        return preparingCodeData.core1;
    }

    @Nullable
    public Function getFitFunction() {
        return preparingCodeData.fitFunction;
    }

    @Nullable
    public Function getPredictFunction() {
        return preparingCodeData.predictFunction;
    }

    @BeforeEach
    public void beforePreparingCore() {
        assertTrue(globals.testing);
        if (sharedCodeData == null) {
            sharedCodeData = preparingCoreService.beforePreparingCore();
        }
        preparingCodeData = sharedCodeData;
    }

    @AfterEach
    public void afterPreparingCore() {
        try {
            // V3: the shared context/DB is never recreated, so we DON'T delete the infra here.
            // We only stop any ExecContext left non-terminal by the @Test so the next test's
            // pipeline driver can't pick it up from the shared DB (DESCRIPTION-TEST-PIPELINE-V2-V3.md, 2.4).
            stopAllNonFinishedExecContexts();
            preparingCoreService.resetTransientStatePerTest();
        }
        catch (Throwable th) {
            log.error("Error", th);
        }
    }

    protected void stopAllNonFinishedExecContexts() {
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
