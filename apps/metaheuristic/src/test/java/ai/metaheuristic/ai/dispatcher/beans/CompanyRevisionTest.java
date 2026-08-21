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

package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parsing of MH_COMPANY_REVISION.PARAMS.
 *
 * <p>The initial-revision DDL seeds both 'Management company' (UNIQUE_ID=1) and
 * 'Company #1' (UNIQUE_ID=2) with PARAMS='' — an empty string, not NULL. So a blank
 * PARAMS is a legitimate persisted state, and CompanyTopLevelService.editFormCommit
 * already treats it as one via S.b(currentHead.getParams()).
 *
 * @author Sergio Lissner
 */
@Execution(ExecutionMode.CONCURRENT)
class CompanyRevisionTest {

    @Test
    void getCompanyParamsYaml_withEmptyParams_returnsDefault() {
        CompanyRevision revision = new CompanyRevision();
        revision.setParams("");

        CompanyParamsYaml cpy = revision.getCompanyParamsYaml();

        assertNotNull(cpy);
        assertEquals(2, cpy.version);
        assertNull(cpy.ac);
        assertNull(cpy.vault);
    }

    @Test
    void getCompanyParamsYaml_withWhitespaceOnlyParams_returnsDefault() {
        CompanyRevision revision = new CompanyRevision();
        revision.setParams("   \n  ");

        CompanyParamsYaml cpy = revision.getCompanyParamsYaml();

        assertNotNull(cpy);
        assertEquals(2, cpy.version);
        assertNull(cpy.ac);
        assertNull(cpy.vault);
    }

    @Test
    void getCompanyParamsYaml_withNullParams_returnsDefault() {
        CompanyRevision revision = new CompanyRevision();

        CompanyParamsYaml cpy = revision.getCompanyParamsYaml();

        assertNotNull(cpy);
        assertEquals(2, cpy.version);
        assertNull(cpy.ac);
        assertNull(cpy.vault);
    }

    @Test
    void getCompanyParamsYaml_withRealParams_parsesAccessControl() {
        CompanyRevision revision = new CompanyRevision();
        revision.setParams("""
            version: 2
            createdOn: 1700000000000
            updatedOn: 1700000001000
            ac:
              groups: 'admins'
            """);

        CompanyParamsYaml cpy = revision.getCompanyParamsYaml();

        assertNotNull(cpy);
        assertEquals(2, cpy.version);
        assertNotNull(cpy.ac);
        assertEquals("admins", cpy.ac.groups);
        assertEquals(1700000000000L, cpy.createdOn);
        assertEquals(1700000001000L, cpy.updatedOn);
    }
}
