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

package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.account.AccountService;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.company.CompanyTopLevelService;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.function.FunctionTxService;
import ai.metaheuristic.ai.dispatcher.repositories.*;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTxService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTopLevelService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableEntityManagerTxService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static ai.metaheuristic.ai.preparing.PreparingConsts.GLOBAL_TEST_VARIABLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 1/6/2022
 * Time: 3:28 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class PreparingSourceCodeInitService {

    private final Globals globals;
    private final PreparingSourceCodeService preparingSourceCodeService;
    private final CompanyTopLevelService companyTopLevelService;
    private final AccountService accountTopLevelService;
    private final GlobalVariableService globalVariableService;
    private final GlobalVariableEntityManagerTxService globalVariableEntityManagerTxService;
    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final SourceCodeCache sourceCodeCache;
    private final FunctionRepository functionRepository;
    private final FunctionTxService functionTxService;
    private final TxSupportForTestingService txSupportForTestingService;
    private final SourceCodeTxService sourceCodeTxService;
    private final CompanyRepository companyRepository;
    private final ExecContextCache execContextCache;
    private final ExecContextRepository execContextRepository;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final TaskRepositoryForTest taskRepositoryForTest;
    private final AccountRepository accountRepository;

    public PreparingData.PreparingSourceCodeData beforePreparingSourceCode(String params) {
        assertTrue(globals.testing);
        assertNotSame(globals.dispatcher.asset.mode, EnumsApi.DispatcherAssetMode.replicated);

        PreparingData.PreparingSourceCodeData data = new PreparingData.PreparingSourceCodeData();

        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(params);
        sourceCodeParamsYaml.checkIntegrity();

        preparingSourceCodeService.cleanUp(sourceCodeParamsYaml.source.uid);

        data.company = new Company();
        data.company.name = "Test company #2";
        companyTopLevelService.addCompany(data.company);
        data.company = Objects.requireNonNull(companyTopLevelService.getCompanyByUniqueId(data.company.uniqueId));

        AccountData.NewAccount account = new AccountData.NewAccount();
        account.username = "test-"+ UUID.randomUUID();
        // 123
        account.password = "$2a$10$jaQkP.gqwgenn.xKtjWIbeP4X.LDJx92FKaQ9VfrN2jgdOUTPTMIu";
        account.password2 = account.password;
        account.publicName = data.account.username;
        accountTopLevelService.addAccount(account, data.company.uniqueId);

        data.account = accountRepository.findByUsername(account.username);
        assertNotNull(data.account);


        data.company = Objects.requireNonNull(companyTopLevelService.getCompanyByUniqueId(data.company.uniqueId));



        assertNotNull(data.company.id);
        assertNotNull(data.company.uniqueId);

        // id==1L must be assigned only to master company
        assertNotEquals(Consts.ID_1, data.company.id);

        data.f1 = createFunction("function-01:1.1");
        data.f2 = createFunction("function-02:1.1");
        data.f3 = createFunction("function-03:1.1");
        data.f4 = createFunction("function-04:1.1");
        data.f5 = createFunction("function-05:1.1");

        SourceCodeApiData.SourceCodeResult scr = sourceCodeTopLevelService.createSourceCode(params, data.company.uniqueId);
        data.sourceCode = Objects.requireNonNull(sourceCodeCache.findById(scr.id));

        byte[] bytes = "A resource for input pool".getBytes();

        try {
            globalVariableService.deleteByVariable(GLOBAL_TEST_VARIABLE);
        } catch (Throwable th) {
            log.error("error preparing variables", th);
        }

        data.testGlobalVariable = globalVariableEntityManagerTxService.save(new ByteArrayInputStream(bytes), bytes.length, GLOBAL_TEST_VARIABLE,"file-01.txt");

        data.execContextYaml = new ExecContextParamsYaml();
        data.execContextYaml.variables.globals = new ArrayList<>();
        data.execContextYaml.variables.globals.add(GLOBAL_TEST_VARIABLE);

        return data;
    }

    private Function createFunction(String functionCode) {
        FunctionConfigYaml sc = new FunctionConfigYaml();
        sc.code = functionCode;
        sc.type = functionCode + "-type";
        sc.file = "some-file";
        sc.env = "env-for-test-function";
        sc.sourcing = EnumsApi.FunctionSourcing.processor;

//  metas:
//  - mh.task-params-version: '5'
        Objects.requireNonNull(sc.metas).add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "5"));
        Long functionId = functionRepository.findIdByCode(functionCode);
        if (functionId!=null) {
            txSupportForTestingService.deleteFunctionById(functionId);
        }

        byte[] bytes = "some code for testing".getBytes();
        Function f = functionTxService.persistFunction(sc, new ByteArrayInputStream(bytes), bytes.length);
        return f;
    }

    public void afterPreparingSourceCode(PreparingData.PreparingSourceCodeData data) {
        try {
            if (data.getSourceCode() !=null) {
                sourceCodeTxService.deleteSourceCodeById(data.getSourceCode().getId());
            }
        } catch (Throwable th) {
            log.error("Error while planCache.deleteById()", th);
        }
        if (data.getCompany() !=null && data.getCompany().id!=null) {
            try {
                companyRepository.deleteById(data.getCompany().id);
            } catch (Throwable th) {
                log.error("Error while companyRepository.deleteById()", th);
            }
        }
        deleteFunction(data.getF1());
        deleteFunction(data.getF2());
        deleteFunction(data.getF3());
        deleteFunction(data.getF4());
        deleteFunction(data.getF5());
        if (data.getExecContextForTest() != null) {
            if (execContextCache.findById(data.getExecContextForTest().getId()) != null) {
                try {
                    execContextRepository.deleteById(data.getExecContextForTest().getId());
                } catch (Throwable th) {
                    log.error("Error while execContextRepository.deleteById()", th);
                }
            }
            if (data.getExecContextForTest().execContextGraphId!=null) {
                try {
                    execContextGraphRepository.deleteById(data.getExecContextForTest().execContextGraphId);
                } catch (Throwable th) {
                    log.error("Error while execContextGraphRepository.deleteById()", th);
                }
            }
            if (data.getExecContextForTest().execContextTaskStateId!=null) {
                try {
                    execContextTaskStateRepository.deleteById(data.getExecContextForTest().execContextTaskStateId);
                } catch (Throwable th) {
                    log.error("Error while execContextTaskStateRepository.deleteById()", th);
                }
            }
            taskRepositoryForTest.deleteByExecContextId(data.getExecContextForTest().getId());
        }
        try {
            globalVariableService.deleteByVariable(PreparingConsts.GLOBAL_TEST_VARIABLE);
        } catch (Throwable th) {
            log.error("error", th);
        }
    }

    private void deleteFunction(@Nullable Function s) {
        if (s!=null) {
            try {
                txSupportForTestingService.deleteFunctionById(s.id);
            } catch (Throwable th) {
                log.error("Error", th);
            }
        }
    }


}
