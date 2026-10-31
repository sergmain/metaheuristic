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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import org.jspecify.annotations.Nullable;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;

import java.util.EnumSet;

/**
 * @author Serge
 * Date: 12/11/2019
 * Time: 3:21 PM
 */
public class TaskParamsUtils {

    /**
     * A git-sourced Function has its payload extracted into the Task's own asset dir, so that dir MUST be
     * cleaned when the Task finishes: the next Task of the next ExecContext is pinned to a different sha,
     * and content left behind is both stale and unbounded on disk. ASSETS is therefore the default rather
     * than something each descriptor has to remember to declare.
     *
     * <p>An explicit policy always wins - a descriptor asking for ALL means the whole task dir goes, which
     * covers the asset dir too.
     *
     * <p>cleaningPolicy is a comma-separated list now. For a git-sourced Function the declared list is kept and ASSETS
     * is added only when the list has neither ALL nor ASSETS - FULL_AFTER_ONE_WEEK alone would leave the asset dir
     * for a week, which is exactly the stale and unbounded content the default exists to prevent. The result is the
     * canonical form of the list, see CleaningPolicyUtils.asString(); an unknown name is refused.
     */
    public static @Nullable String defaultCleaningPolicy(
            EnumsApi.@Nullable FunctionSourcing sourcing, @Nullable String declared) {
        final EnumSet<EnumsApi.CleaningPolicy> policies = CleaningPolicyUtils.parseStrict(declared);
        if (sourcing==EnumsApi.FunctionSourcing.git
                && !policies.contains(EnumsApi.CleaningPolicy.ALL) && !policies.contains(EnumsApi.CleaningPolicy.ASSETS)) {
            policies.add(EnumsApi.CleaningPolicy.ASSETS);
        }
        return CleaningPolicyUtils.asString(policies);
    }

    public static TaskParamsYaml.FunctionConfig toFunctionConfig(FunctionConfigYaml src) {
        TaskParamsYaml.FunctionConfig trg = new TaskParamsYaml.FunctionConfig();
        trg.checksumMap = src.system!=null ? src.system.checksumMap : null;
        trg.code = src.function.code;
        trg.env = src.function.env;
        // copy per-OS targets across (FunctionConfigYaml.Target -> TaskParamsYaml.Target)
        for (java.util.Map.Entry<String, FunctionConfigYaml.Target> e : src.function.targets.entrySet()) {
            trg.targets.put(e.getKey(), new TaskParamsYaml.Target(e.getValue().src, e.getValue().file));
        }
        trg.git = src.function.git;
        if (src.function.metas!=null) {
            trg.metas.addAll(src.function.metas);
        }
        trg.params = src.function.params;
        trg.sourcing = src.function.sourcing;
        trg.type = src.function.type;
        trg.assetDir = src.function.assetDir;
        trg.cleaningPolicy = defaultCleaningPolicy(src.function.sourcing, src.function.cleaningPolicy);
        // Stage 5 (vault secret handoff): propagate FunctionConfigYaml.api → TaskParamsYaml.api.
        if (src.function.api != null) {
            trg.api = new TaskParamsYaml.Api(src.function.api.keyCode);
        }
        return trg;
    }
}
