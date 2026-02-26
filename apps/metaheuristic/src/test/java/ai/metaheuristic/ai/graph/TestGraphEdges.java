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

package ai.metaheuristic.ai.graph;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskApiData;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 7/16/2019
 * Time: 8:53 PM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestGraphEdges extends PreparingSourceCode {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @BeforeAll
    static void setSystemProperties() {
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

    @Autowired private ExecContextCache execContextCache;
    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TestGraphService testGraphService;
    @Autowired private ExecContextGraphService execContextGraphService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void test() {
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());

        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);

        assertNotNull(getExecContextForTest());
        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, ()->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId, ()-> {
                            OperationStatusRest osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(),
                                    List.of(new TaskApiData.TaskWithContext(1L, "123###1")));

                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);

                            long count = preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest());
                            assertEquals(1, count);

                            osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,List.of(1L),
                                List.of(new TaskApiData.TaskWithContext(21L, "123###1"),
                                    new TaskApiData.TaskWithContext(22L, "123###1"),
                                    new TaskApiData.TaskWithContext(23L, "123###1")));
                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);
                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            List<ExecContextData.TaskVertex> leafs = testGraphService.findLeafs(getExecContextForTest());

                            assertEquals(3, leafs.size());

                            assertTrue(leafs.contains(new ExecContextData.TaskVertex(21L, "123###1")));
                            assertTrue(leafs.contains(new ExecContextData.TaskVertex(22L, "123###1")));
                            assertTrue(leafs.contains(new ExecContextData.TaskVertex(23L, "123###1")));

                            osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(21L),
                                List.of(new TaskApiData.TaskWithContext(311L, "123###1"),
                                    new TaskApiData.TaskWithContext(312L, "123###1"),
                                    new TaskApiData.TaskWithContext(313L, "123###1")));
                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);
                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            Set<ExecContextData.TaskVertex> descendands = execContextGraphService.findDescendants(getExecContextForTest().id, getExecContextForTest().execContextGraphId, 1L);
                            assertEquals(6, descendands.size());

                            descendands = execContextGraphService.findDescendants(getExecContextForTest().id, getExecContextForTest().execContextGraphId, 21L);
                            assertEquals(3, descendands.size());

                            leafs = testGraphService.findLeafs(getExecContextForTest());
                            assertEquals(5, leafs.size());
                        })));
    }
}
