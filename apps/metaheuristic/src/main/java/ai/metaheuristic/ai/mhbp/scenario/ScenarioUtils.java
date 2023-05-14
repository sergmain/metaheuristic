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

package ai.metaheuristic.ai.mhbp.scenario;

import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.mhbp.yaml.scenario.ScenarioParams;
import ai.metaheuristic.ai.utils.CollectionUtils;

import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.utils.StrUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Sergio Lissner
 * Date: 5/14/2023
 * Time: 1:08 AM
 */
public class ScenarioUtils {

    @SuppressWarnings({"unchecked", "rawtypes", "RedundantIfStatement"})
    public static class ItemWithUuid implements CollectionUtils.TreeUtils.TreeItem<String> {
        public String uuid;
        @Nullable
        public String parentUuid;
        @Nullable
        public List<ItemWithUuid> items = null;

        public ItemWithUuid(String uuid, @Nullable String parentUuid) {
            this.uuid = uuid;
            this.parentUuid = parentUuid;
        }

        @JsonIgnore
        @Override
        public String getTopId() {
            return parentUuid;
        }

        @JsonIgnore
        @Override
        public String getId() {
            return uuid;
        }

        @JsonIgnore
        @Nullable
        @Override
        public List<CollectionUtils.TreeUtils.TreeItem<String>> getSubTree() {
            return (List)items;
        }

        @JsonIgnore
        @Override
        public void setSubTree(@Nullable List<CollectionUtils.TreeUtils.TreeItem<String>> list) {
            this.items = (List)list;
        }

        @SuppressWarnings("ConstantValue")
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }

            ItemWithUuid that = (ItemWithUuid) o;

            if (!this.uuid.equals(that.uuid)) {
                return false;
            }
            if (this.parentUuid != null ? !this.parentUuid.equals(that.parentUuid) : that.parentUuid != null) {
                return false;
            }
            return true;
        }

    }

    public static SourceCodeParamsYaml to(Scenario s) {

        ScenarioParams sp = s.getScenarioParams();
        List<ItemWithUuid> list = sp.steps.stream().map(o->new ItemWithUuid(o.uuid, o.parentUuid)).toList();
        CollectionUtils.TreeUtils<String> treeUtils = new CollectionUtils.TreeUtils<>();
        List<ItemWithUuid> tree = treeUtils.rebuildTree((List)list);

        AtomicInteger processNumber = new AtomicInteger(1);
        SourceCodeParamsYaml sc = new SourceCodeParamsYaml();
        sc.source.processes = new ArrayList<>();
        sc.source.instances = 1;
        sc.source.uid = "scenario-"+s.scenarioGroupId+'-'+s.id+'-'+s.version;
        sc.source.strictNaming = true;

        processTree(sc.source.processes, sc, sp, tree, processNumber);

        return sc;

    }

    private static void processTree(List<SourceCodeParamsYaml.Process> processes, SourceCodeParamsYaml sc, ScenarioParams sp, List<ItemWithUuid> tree, AtomicInteger processNumber) {
        for (ItemWithUuid itemWithUuid : tree) {
            ScenarioParams.Step step = findStepByUuid(sp, itemWithUuid.uuid);
            if (step==null) {
                throw new IllegalStateException("(step==null), uuid: " + itemWithUuid.uuid);
            }

            boolean isApi = step.function==null;

            SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
            p.name = step.name;
            String code;
            if (StrUtils.isCodeOk(step.name)) {
                code = step.name;
            }
            else {
                String n = StrUtils.normalizeCode(step.name);
                code = StrUtils.isCodeOk(n) ? n : "process-"+processNumber.getAndIncrement();
            }

            p.code = code;
            p.function = new SourceCodeParamsYaml.FunctionDefForSourceCode();


/*

            public String name;
            public String code;
            public SourceCodeParamsYaml.FunctionDefForSourceCode function;
            @Nullable
            public List<SourceCodeParamsYaml.FunctionDefForSourceCode> preFunctions = new ArrayList<>();
            @Nullable
            public List<SourceCodeParamsYaml.FunctionDefForSourceCode> postFunctions = new ArrayList<>();

            @Nullable
            public Long timeoutBeforeTerminate;
            public final List<SourceCodeParamsYaml.Variable> inputs = new ArrayList<>();
            public final List<SourceCodeParamsYaml.Variable> outputs = new ArrayList<>();
            public List<Map<String, String>> metas = new ArrayList<>();
            @Nullable
            public SourceCodeParamsYaml.SubProcesses subProcesses;

            @Nullable
            public SourceCodeParamsYaml.Cache cache;

            @Nullable
            public String tag;
            public int priority;
            @Nullable
            public String condition;
            @Nullable
            public Integer triesAfterError;
*/

        }
    }

    @Nullable
    public static ScenarioParams.Step findStepByUuid(ScenarioParams sp, String uuid) {
        return sp.steps.stream().filter(o->o.uuid.equals(uuid)).findFirst().orElse(null);
    }
}
