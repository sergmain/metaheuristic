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

package ai.metaheuristic.ai.tx;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 5/25/2023
 * Time: 7:49 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
//@ActiveProfiles({"dispatcher", "mysql"})
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class SqlQueryTest extends PreparingSourceCode {

    @Autowired
    private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired
    private ExecContextCache execContextCache;
    @Autowired
    private ExecContextRepository execContextRepository;
    @Autowired
    private EntityManager em;
    @Autowired
    private TxTestingService txTestingService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testSingleThread() {
        ExecContextCreatorService.ExecContextCreationResult r = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertNotNull(r.execContext);
        setExecContextForTest(r.execContext);

        System.out.println("### warm cache");
        ExecContextImpl ec = execContextCache.findById(getExecContextForTest().id);
        ec = execContextCache.findById(getExecContextForTest().id);

        System.out.println("### call via execContextCache");
        ec = execContextCache.findById(getExecContextForTest().id);

        System.out.println("### call via state only");
        int state = execContextRepository.findState(getExecContextForTest().id);

        // =====================

        System.out.println("### The end.");
    }

}
