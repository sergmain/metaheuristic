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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 3/14/2020
 * Time: 8:53 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class TestSingleInternalFunction extends FeatureMethods {

    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private TaskRepository taskRepository;

    @Override
    @SneakyThrows
    public String getSourceCodeYamlAsString() {
        return IOUtils.resourceToString("/source_code/yaml/for-testing-single-internal-function.yaml", StandardCharsets.UTF_8);
    }

    @Test
    public void test() {
        System.out.println("start step_0_0_produce_tasks_and_start()");
        step_0_0_produce_tasks_and_start();

        List<Object[]> list = taskRepositoryForTest.findAllExecStateAndParamsByExecContextId(getExecContextForTest().id);
        assertEquals(2, list.size());

        final List<String> codes = List.of(getFunctionCode(list.get(0)[0]), getFunctionCode(list.get(1)[0]));
        assertTrue(codes.contains(Consts.MH_FINISH_FUNCTION));
        assertTrue(codes.contains(Consts.MH_BATCH_SPLITTER_FUNCTION));
    }

    private String getFunctionCode(Object object) {
        TaskImpl task = taskRepository.findById((Long) object).orElse(null);
        assertNotNull(task);
        TaskParamsYaml taskParamsYaml = task.getTaskParamsYaml();
        return taskParamsYaml.task.function.code;
    }
}



