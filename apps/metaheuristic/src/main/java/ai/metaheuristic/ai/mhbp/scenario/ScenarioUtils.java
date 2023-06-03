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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.internal_functions.aggregate.AggregateFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.api_call.ApiCallService;
import ai.metaheuristic.ai.dispatcher.internal_functions.batch_line_splitter.BatchLineSplitterFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.enhance_text.EnhanceTextFunction;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.mhbp.yaml.scenario.ScenarioParams;
import ai.metaheuristic.ai.mhbp.yaml.scheme.ApiScheme;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.StrUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergio Lissner
 * Date: 5/14/2023
 * Time: 1:08 AM
 */
@Slf4j
public class ScenarioUtils {

    public static final Pattern VAR_PATTERN = Pattern.compile("[\\[\\{]{2}([\\w\\s-_.]+)[\\]\\}]{2}");
    public static final String MH_STUB_VARIABLE = "mh.stub-variable";
    public static final int MAX_LENGTH_OF_UID = 250;

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
        return to(getUid(s), s.getScenarioParams(), null);
    }

    public static String getUid(Scenario s) {
        final String suffix = getString(s);
        final String uid = StrUtils.getCode(StringUtils.substring(s.name, 0, MAX_LENGTH_OF_UID - suffix.length()) + suffix, () -> "scenario" + suffix).toLowerCase();
        return uid;
    }

    public static String getString(Scenario s) {
        final String suffix = "-" + s.scenarioGroupId + '-' + s.id + '-' + s.version;
        return suffix;
    }

    public static SourceCodeParamsYaml to(String uid, ScenarioParams sp, Function<String, ApiScheme> apiSchemeResolverFunc) {
        List<ItemWithUuid> list = sp.steps.stream().map(o->new ItemWithUuid(o.uuid, o.parentUuid)).toList();
        CollectionUtils.TreeUtils<String> treeUtils = new CollectionUtils.TreeUtils<>();
        List<ItemWithUuid> tree = treeUtils.rebuildTree((List)list);

        AtomicInteger processNumber = new AtomicInteger(1);
        SourceCodeParamsYaml sc = new SourceCodeParamsYaml();
        sc.source.processes = new ArrayList<>();
        sc.source.instances = 1;
        sc.source.uid = uid;
        sc.source.strictNaming = true;
        sc.source.variables = null;
        sc.source.metas = null;

        processTree(sc.source.processes, sp, tree, processNumber, apiSchemeResolverFunc);
        return sc;
    }

    private static void processTree(List<SourceCodeParamsYaml.Process> processes, ScenarioParams sp, List<ItemWithUuid> tree,
                                    AtomicInteger processNumber, Function<String, ApiScheme> apiSchemeResolverFunc) {
        for (ItemWithUuid itemWithUuid : tree) {
            ScenarioParams.Step step = findStepByUuid(sp, itemWithUuid.uuid);
            if (step==null) {
                throw new IllegalStateException("(step==null), uuid: 4" + itemWithUuid.uuid);
            }
            boolean isApi = step.function==null;
            if (isApi && step.api==null) {
                throw new IllegalStateException("(isApi && step.api==null)");
            }

            SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
            p.name = step.name;
            p.code = StrUtils.getCode(step.name, () -> "process-" + processNumber.getAndIncrement());

            final SourceCodeParamsYaml.FunctionDefForSourceCode f = new SourceCodeParamsYaml.FunctionDefForSourceCode();
            f.code = isApi ? Consts.MH_API_CALL_FUNCTION : step.function.code;
            f.context = EnumsApi.FunctionExecContext.internal;

            p.function = f;
            p.preFunctions = null;
            p.postFunctions = null;

            // 60 second for exec
            p.timeoutBeforeTerminate = 60L;

            if (!S.b(step.expected)) {
                p.metas.add(Map.of(Consts.EXPECTED, step.expected));
            }

            if (step.function==null || (!Consts.MH_BATCH_LINE_SPLITTER_FUNCTION.equals(step.function.code) && !Consts.MH_AGGREGATE_FUNCTION.equals(step.function.code))) {
                extractInputVariables(p.inputs, step);
                EnumsApi.VariableType type = null;
                if (step.api!=null) {
                    final ApiScheme apiScheme = apiSchemeResolverFunc.apply(step.api.code);
                    if (apiScheme==null) {
                        throw new IllegalStateException("(apiScheme==null)");
                    }
                    type = switch(apiScheme.scheme.response.type) {
                        case json, text -> EnumsApi.VariableType.text;
                        case image -> EnumsApi.VariableType.image;
                    };
                }

                if (type==null) {
                    type = EnumsApi.VariableType.text;
                }
                extractOutputVariables(p.outputs, step, type);
            }

            p.cache = null;
            if (isApi) {
                p.metas.add(Map.of(ApiCallService.PROMPT, step.p));
                p.metas.add(Map.of(ApiCallService.API_CODE, step.api.code));
                p.triesAfterError = 0;
//                p.triesAfterError = 2;
                p.cache = new SourceCodeParamsYaml.Cache(true, true, true);
            }
            else {
                if (Consts.MH_BATCH_LINE_SPLITTER_FUNCTION.equals(step.function.code)) {
                    p.metas.add(Map.of(BatchLineSplitterFunction.NUMBER_OF_LINES_PER_TASK, "1"));
                    p.metas.add(Map.of(BatchLineSplitterFunction.VARIABLE_FOR_SPLITTING, getNameForVariable(getVariables(step.p, true).get(0))));
                    p.metas.add(Map.of(BatchLineSplitterFunction.OUTPUT_VARIABLE, getNameForVariable(getVariables(step.resultCode, true).get(0))));
                    p.metas.add(Map.of(BatchLineSplitterFunction.IS_ARRAY, "false"));
                }
                else if (Consts.MH_ENHANCE_TEXT_FUNCTION.equals(step.function.code)) {
                    p.metas.add(Map.of(EnhanceTextFunction.TEXT, step.p));
                }
                else if (Consts.MH_ACCEPTANCE_TEST_FUNCTION.equals(step.function.code)) {
                    if (step.api==null || S.b(step.api.code)) {
                        throw new IllegalStateException("(step.api==null || S.b(step.api.code))");
                    }
                    p.metas.add(Map.of(ApiCallService.PROMPT, step.p));
                    p.metas.add(Map.of(ApiCallService.API_CODE, step.api.code));
                    p.cache = new SourceCodeParamsYaml.Cache(true, true, true);
                }
                else if (Consts.MH_AGGREGATE_FUNCTION.equals(step.function.code)) {
                    if (step.aggregateType==null) {
                        throw new IllegalStateException("(step.aggregateType==null)");
                    }
                    p.metas.add(Map.of(AggregateFunction.VARIABLES, step.p));
                    p.metas.add(Map.of(AggregateFunction.TYPE, step.aggregateType.toString()));
                    p.metas.add(Map.of(AggregateFunction.PRODUCE_METADATA, "false"));
                    extractOutputVariables(p.outputs, step, step.aggregateType.type);
                }
            }


            if (CollectionUtils.isNotEmpty(itemWithUuid.items)) {
                p.subProcesses = new SourceCodeParamsYaml.SubProcesses(EnumsApi.SourceCodeSubProcessLogic.sequential, new ArrayList<>());
                processTree(p.subProcesses.processes, sp, itemWithUuid.items, processNumber, apiSchemeResolverFunc);
            }

            processes.add(p);
        }
    }

    private static void extractOutputVariables(List<SourceCodeParamsYaml.Variable> outputs, ScenarioParams.Step step, EnumsApi.VariableType type) {
        if (S.b(step.resultCode)) {
            throw new IllegalStateException("(S.b(step.resultCode))");
        }
        String outputName = getVariables(step.resultCode, true).get(0);
        final SourceCodeParamsYaml.Variable v = new SourceCodeParamsYaml.Variable();
        v.name = getNameForVariable(outputName);
        v.ext = type.ext;
        outputs.add(v);
    }

    private static void extractInputVariables(List<SourceCodeParamsYaml.Variable> inputs, ScenarioParams.Step step) {
        if (S.b(step.p)) {
            throw new IllegalStateException("(S.b(step.p))");
        }
        String text = step.p.replace("[[]]", "[[mh.stub-variable]]");
        text = text.replace("{{}}", "[[mh.stub-variable]]");
        final List<String> variables = getVariables(text, step.function!=null);
        for (String name : variables) {
            if (MH_STUB_VARIABLE.equals(name)) {
                continue;
            }
            final SourceCodeParamsYaml.Variable v = new SourceCodeParamsYaml.Variable();
            v.name = getNameForVariable(name);
            v.ext = ".txt";
            inputs.add(v);
        }
    }

    public static String getNameForVariable(String name) {
        return StrUtils.getVariableName(name, () -> {
            log.error("Wrong name for variable: " + name);
            throw new IllegalStateException("Wrong name of variable: " + name);
        }).toLowerCase();
    }

    @Nullable
    public static String getVariable(String text) {
        List<String> l = getVariables(text, false);
        return l.isEmpty() ? null : l.get(0);
    }

    public static List<String> getVariables(String text, boolean useTextAsDefault) {
        Matcher matcher = VAR_PATTERN.matcher(text);

        List<String> list = new ArrayList<>();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            list.add(variableName);
        }

        if (useTextAsDefault && list.isEmpty()) {
            list.add(text);
        }
        return list;
    }

    @Nullable
    public static ScenarioParams.Step findStepByUuid(ScenarioParams sp, String uuid) {
        return sp.steps.stream().filter(o->o.uuid.equals(uuid)).findFirst().orElse(null);
    }
}
