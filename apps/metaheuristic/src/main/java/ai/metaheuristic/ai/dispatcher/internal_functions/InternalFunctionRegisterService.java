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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 4:44 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class InternalFunctionRegisterService {

    public final List<InternalFunction> internalFunctions;

    public static Map<String, InternalFunction> internalFunctionMap = null;

    @PostConstruct
    public void postConstruct() {
        internalFunctionMap = internalFunctions.stream()
                .collect(Collectors.toUnmodifiableMap(InternalFunction::getCode, o -> o, (a, b) -> b));
    }

    public static InternalFunction getInternalFunction(String functionCode) {
        if (internalFunctionMap==null) {
            throw new IllegalStateException("(internalFunctionMap==null)");
        }
        return internalFunctionMap.get(functionCode);
    }

    public static InternalFunction get(String functionCode) {
        if (internalFunctionMap==null) {
            throw new IllegalStateException("(internalFunctionMap==null)");
        }
        return internalFunctionMap.get(functionCode);
    }
}

