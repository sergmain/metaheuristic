/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("WeakerAccess")
@Slf4j
public abstract class PreparingSourceCode extends PreparingCore {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private Globals globals;

    public SourceCodeImpl getSourceCode() {
        return preparingSourceCodeData.sourceCode;
    }

    public Function getF1() {
        return preparingSourceCodeData.f1;
    }

    public Function getF2() {
        return preparingSourceCodeData.f2;
    }

    public Function getF3() {
        return preparingSourceCodeData.f3;
    }

    public Function getF4() {
        return preparingSourceCodeData.f4;
    }

    public Function getF5() {
        return preparingSourceCodeData.f5;
    }

    public ExecContextImpl getExecContextForTest() {
        return preparingSourceCodeData.execContextForTest;
    }

    public void setExecContextForTest(ExecContextImpl execContextForTest) {
        preparingSourceCodeData.execContextForTest = execContextForTest;
    }

    public ExecContextParamsYaml getExecContextYaml() {
        return preparingSourceCodeData.execContextYaml;
    }

    public Company getCompany() {
        return preparingSourceCodeData.company;
    }

    public GlobalVariable getTestGlobalVariable() {
        return preparingSourceCodeData.testGlobalVariable;
    }

    public PreparingData.PreparingSourceCodeData preparingSourceCodeData;

    public abstract String getSourceCodeYamlAsString();

    @SneakyThrows
    public static String getSourceCodeV1() {
        return IOUtils.resourceToString("/source_code/yaml/default-source-code-for-testing.yaml", StandardCharsets.UTF_8);
    }

    public static String getSourceParamsYamlAsString_Simple() {
        return getSourceCodeV1();
    }

    @BeforeEach
    public void beforePreparingSourceCode() {
        assertTrue(globals.testing);
        assertNotSame(globals.dispatcher.asset.mode, EnumsApi.DispatcherAssetMode.replicated);

        String params = getSourceCodeYamlAsString();
        preparingSourceCodeData = preparingSourceCodeInitService.beforePreparingSourceCode(params);
    }

    @AfterEach
    public void afterPreparingSourceCode() {
        if (preparingSourceCodeData!=null) {
            preparingSourceCodeInitService.afterPreparingSourceCode(preparingSourceCodeData);
        }
    }
}
