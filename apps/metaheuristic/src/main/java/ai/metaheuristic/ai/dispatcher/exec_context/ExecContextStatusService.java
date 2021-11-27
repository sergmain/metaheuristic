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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml.ExecContextStatus;

/**
 * @author Serge
 * Date: 10/27/2020
 * Time: 3:51 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextStatusService {

    private final ExecContextRepository execContextRepository;

    private ExecContextStatus cachedStatus = null;

    @PostConstruct
    public void post() {
        resetStatus();
    }

    public ExecContextStatus getExecContextStatuses() {
        return cachedStatus;
    }

    public void resetStatus() {
        ExecContextStatus cachedStatusTemp = new ExecContextStatus();

        execContextRepository.findAllExecStates()
                .stream()
                .map(o -> toSimpleStatus((Long) o[0], (Integer) o[1]))
                .collect(Collectors.toCollection(() -> cachedStatusTemp.statuses));

        ExecContextStatus old = cachedStatus;
        cachedStatus = cachedStatusTemp;
//        destroy(old);
    }

    private static void destroy(@Nullable ExecContextStatus cachedStatus) {
        if (cachedStatus!=null) {
            cachedStatus.statuses.clear();
        }
    }

    private static ExecContextStatus.SimpleStatus toSimpleStatus(Long execContextId, Integer execSate) {
        return new ExecContextStatus.SimpleStatus(execContextId, EnumsApi.ExecContextState.toState(execSate));
    }
}
