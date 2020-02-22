/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.mh.dispatcher..source_code;

import ai.metaheuristic.ai.mh.dispatcher..function.FunctionService;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("mh.dispatcher.")
@Service
@RequiredArgsConstructor
public class CommonProcessValidatorService {

    private final FunctionService functionService;

    public boolean checkRequiredVersion(int sourceCodeParamsVersion, SourceCodeParamsYaml.FunctionDefForSourceCode snDef) {
        int taskParamsYamlVersion = SourceCodeParamsYamlUtils.getRequiredVertionOfTaskParamsYaml(sourceCodeParamsVersion);
        boolean ok = functionService.isFunctionVersionOk(taskParamsYamlVersion, snDef);
        if (!ok) {
            log.error("#175.030 Version of function {} is too low, required version: {}", snDef.code, taskParamsYamlVersion);
            return false;
        }
        return true;
    }
}
