/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;

import static ai.metaheuristic.commons.CommonConsts.GIT_REPO;

/**
 * @author Sergio Lissner
 * Date: 8/23/2022
 * Time: 12:03 AM
 */
public class ArtifactCommonUtils {
    private static EnumsApi.DataType toType(EnumsApi.VariableContext context) {
        switch (context) {
            case global:
                return EnumsApi.DataType.global_variable;
            case local:
            case array:
                return EnumsApi.DataType.variable;
            default:
                throw new IllegalStateException("#103.040 wrong context: " + context);
        }
    }

    public static TaskFileParamsYaml.InputVariable upInputVariable(TaskParamsYaml.InputVariable v1) {
        TaskFileParamsYaml.InputVariable  v = new TaskFileParamsYaml.InputVariable();
        v.id = v1.id.toString();
        v.dataType = toType(v1.context);
        v.array = v1.context== EnumsApi.VariableContext.array;
        v.name = v1.name;
        v.disk = v1.disk;
        v.git = v1.git;
        v.sourcing = v1.sourcing;
        v.filename = v1.filename;
        v.type = v1.type;
        v.empty = v1.empty;
        v.setNullable(v1.getNullable());
        return v;
    }

    public static TaskFileParamsYaml.OutputVariable upOutputVariable(TaskParamsYaml.OutputVariable v1) {
        TaskFileParamsYaml.OutputVariable v = new TaskFileParamsYaml.OutputVariable();
        v.id = v1.id.toString();
        v.name = v1.name;
        v.dataType = toType(v1.context);
        v.disk = v1.disk;
        v.git = v1.git;
        v.sourcing = v1.sourcing;
        v.filename = v1.filename;
        v.type = v1.type;
        v.empty = v1.empty;
        v.setNullable(v1.getNullable());
        return v;
    }

    public static String normalizeCode(String code) {
        if (StringUtils.containsWhitespace(code)) {
            throw new IllegalStateException("Code can't contain any whitespace char");
        }
        final String replaced = code.replace(':', '_');
        int count=0;
        for (int i = replaced.length() - 1; i >= 0; i--) {
            final char c = replaced.charAt(i);
            if (c=='.') {
                count++;
            }
            else {
                break;
            }
        }
        if (count==replaced.length()) {
            throw new IllegalStateException("function code contains only '.' chars");
        }
        return count==0 ? replaced : replaced.substring(0, replaced.length()-count);
    }

    public static Path prepareFunctionPath(Path basePath) {
        return basePath.resolve(EnumsApi.DataType.function.toString());
    }

    public static Path prepareGitRepoPath(Path basePath) {
        return basePath.resolve(GIT_REPO);
    }
}
