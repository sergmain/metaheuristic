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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.mhbp.data.ScenarioData;
import ai.metaheuristic.api.dispatcher.InternalFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 4:44 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class InternalFunctionRegisterService {

    private final ApplicationContext appCtx;

    private final StampedLock lock = new StampedLock();

    private Map<String, InternalFunction> internalFunctions = null;

    @Nullable
    public InternalFunction get(String functionCode) {
        initInternalFunctions();
        for (InternalFunction internalFunction : internalFunctions.values()) {
            if (internalFunction.getCode().equals(functionCode)) {
                return internalFunction;
            }
        }
        return null;
    }

    public Set<String> getCachableFunctions() {
        initInternalFunctions();
        return internalFunctions.entrySet().stream().filter(e->e.getValue().isCachable()).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    public List<ScenarioData.InternalFunction> getScenarioCompatibleFunctions() {
        initInternalFunctions();
        return this.internalFunctions.entrySet().stream()
                .filter(e -> e.getValue().isScenarioCompatible())
                .map(e -> new ScenarioData.InternalFunction(e.getKey(), e.getValue().getClass().getSimpleName())).collect(Collectors.toList());
    }

    private void initInternalFunctions() {
        long stamp = lock.tryOptimisticRead();
        var tempHolder = this.internalFunctions;

        if (tempHolder != null && lock.validate(stamp)) {
            return;
        } else {
            stamp = lock.readLock();
            try {
                tempHolder = this.internalFunctions;
                if (tempHolder != null) {
                    return;
                }
            } finally {
                lock.unlock(stamp);
            }
        }
        stamp = lock.writeLock();
        try {
            if (this.internalFunctions!=null){
                return;
            }
            Map<String, InternalFunction> map = appCtx.getBeansOfType(InternalFunction.class);
            this.internalFunctions = map.values().stream()
                .collect(Collectors.toUnmodifiableMap(InternalFunction::getCode, o -> o, (a, b) -> b));
        } finally {
            lock.unlock(stamp);
        }
    }

}

