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

package ai.metaheuristic.ai.dispatcher.test.tx;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.function.FunctionTxService;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 2:36 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TxTestingTopLevelService {

    private final TxTestingService txTestingService;
    private final FunctionRepository functionRepository;
    private final FunctionTxService functionTxService;

    @Transactional(propagation = Propagation.NEVER)
    public String updateWithSyncSingle(Long execContextId, Long taskId) {
        return ExecContextSyncService.getWithSync(execContextId, () -> txTestingService.updateSingle(execContextId, taskId));
    }

    @Transactional(propagation = Propagation.NEVER)
    public String updateWithSyncDouble(Long execContextId, Long taskId) {
        return ExecContextSyncService.getWithSync(execContextId, () -> txTestingService.updateDouble(execContextId, taskId));
    }

    public Function getOrCreateFunction(String functionCode, String funcType, String file) throws IOException {
        long mills;
        mills = System.currentTimeMillis();
        log.info("Start findByCode.save()");
        Function f = functionRepository.findByCode(functionCode);
        log.info("findByCode() was finished for {} milliseconds", System.currentTimeMillis() - mills);
        if (f == null) {
            FunctionConfigYaml sc = new FunctionConfigYaml();
            sc.function.code = functionCode;
            sc.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
            sc.function.type = funcType;
            sc.function.env = "python-3";
            sc.function.file = file;
            sc.function.params = "AAA";
            sc.function.metas.add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

            mills = System.currentTimeMillis();
            log.info("Start functionRepository.save() #1");
            byte[] bytes = IOUtils.resourceToByteArray("/bin/test-zip.zip");
            f = functionTxService.persistFunction(sc, new ByteArrayInputStream(bytes), bytes.length);

            log.info("functionRepository.save() #1 was finished for {} milliseconds", System.currentTimeMillis() - mills);
        }
        return f;
    }
}
