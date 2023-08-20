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

package ai.metaheuristic.ai.dispatcher.internal_functions.aggregate;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.commons.ArtifactCleanerAtDispatcher;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextUtilsService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.mhbp.scenario.ScenarioUtils;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYaml;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * @author Serge
 * Date: 3/13/2020
 * Time: 11:19 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AggregateFunction implements InternalFunction {

    // this type only for aggregating result.
    // if internal function doesn't support or won't support such aggregation, don't create a new type
    public enum AggregateType {
        zip(true, EnumsApi.VariableType.zip), text(true, EnumsApi.VariableType.text),
        ww2003(true, EnumsApi.VariableType.xml), html(false, EnumsApi.VariableType.html),
        pdf(false, EnumsApi.VariableType.pdf);

        public final boolean supported;
        public final EnumsApi.VariableType type;

        AggregateType(boolean supported, EnumsApi.VariableType type) {
            this.supported = supported;
            this.type = type;
        }
    }

    public static final String VARIABLES = "variables";
    public static final String TYPE = "type";
    public static final String PRODUCE_METADATA = "produce-metadata";

    private final VariableRepository variableRepository;
    private final VariableTxService variableTxService;
    private final ExecContextUtilsService execContextUtilsService;
    private final AggregateToWW2003Service aggregateToWW2003Service;

    private static final String META_ERROR_CONTROL = "error-control-policy";
    public enum ErrorControlPolicy { fail, ignore }

    @Override
    public String getCode() {
        return Consts.MH_AGGREGATE_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_AGGREGATE_FUNCTION;
    }

    public boolean isScenarioCompatible() {
        return true;
    }

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();
        ArtifactCleanerAtDispatcher.setBusy();
        try {
            processInternal(simpleExecContext, taskId, taskParamsYaml);
        }
        finally {
            ArtifactCleanerAtDispatcher.notBusy();
        }
    }

    @SneakyThrows
    private void processInternal(ExecContextData.SimpleExecContext simpleExecContext, Long taskId, TaskParamsYaml taskParamsYaml) {

        if (taskParamsYaml.task.outputs.size()!=1) {
            throw new InternalFunctionException(
                    number_of_outputs_is_incorrect, "#979.020 There must be only one output variable, current: "+ taskParamsYaml.task.outputs);
        }

        final boolean produceMetadata = MetaUtils.isTrue(taskParamsYaml.task.metas, true, PRODUCE_METADATA);
        String resultTypeStr = MetaUtils.getValue(taskParamsYaml.task.metas, TYPE);
        AggregateType resultType = S.b(resultTypeStr) ? AggregateType.zip : AggregateType.valueOf(resultTypeStr);

        List<String> names = getNamesOfVariables(taskParamsYaml.task.metas);
        if (CollectionUtils.isEmpty(names)) {
            throw new InternalFunctionException(
                    meta_not_found, "#979.080 Meta 'variables' wasn't found or empty, process: " + taskParamsYaml.task.processCode);
        }

        List<Variable> list = variableRepository.getIdAndStorageUrlInVarsForExecContext(simpleExecContext.execContextId, names.toArray(String[]::new));

        String policyMeta = MetaUtils.getValue(taskParamsYaml.task.metas, META_ERROR_CONTROL);
        ErrorControlPolicy policy = S.b(policyMeta) ? ErrorControlPolicy.ignore : ErrorControlPolicy.valueOf(policyMeta);

        Path tempDir = null;
        try {
            tempDir = DirUtils.createMhTempPath("mh-aggregate-internal-context-");
            if (tempDir==null) {
                throw new InternalFunctionException(
                        system_error, "#979.100 Can't create temporary directory in dir "+ SystemUtils.JAVA_IO_TMPDIR);
            }

            TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
            Path outputDir = tempDir.resolve(outputVariable.name);
            Files.createDirectory(outputDir);

            List<Variable> variables = new ArrayList<>();
            LinkedHashSet<String> taskContextIds = new LinkedHashSet<>();

            // TODO p3 2023-06-06 replace with sorting which is being used for 'State of Tasks'
            list.stream().map(o->o.taskContextId).collect(Collectors.toCollection(()->taskContextIds));

            for (String contextId : taskContextIds) {
                Path taskContextDir = outputDir.resolve(contextId);
                MetadataAggregateFunctionParamsYaml mafpy = new MetadataAggregateFunctionParamsYaml();
                try {
                    Files.createDirectory(taskContextDir);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                list.stream().filter(t-> contextId.equals(t.taskContextId))
                        .forEach( v->{
                            if (v.nullified) {
                                return;
                            }
                            try {
                                switch(resultType) {
                                    case zip -> {
                                        String ext = execContextUtilsService.getExtensionForVariable(simpleExecContext.execContextVariableStateId, v.id, "");
                                        Path varFile = taskContextDir.resolve(v.name + ext);
                                        if (produceMetadata) {
                                            mafpy.mapping.add(Map.of(varFile.getFileName().toString(), v.name));
                                        }
                                        variableTxService.storeToFileWithTx(v.id, varFile);
                                    }
                                    case text, ww2003 -> {
                                        variables.add(v);
                                    }
                                    default -> throw new InternalFunctionException(
                                            system_error, "#979.100 Can't create temporary directory in dir "+ SystemUtils.JAVA_IO_TMPDIR);
                                }
                            } catch (VariableDataNotFoundException e) {
                                log.error("#979.140 Variable #{}, name {},  wasn't found", v.id, v.name);
                                if (policy==ErrorControlPolicy.fail) {
                                    throw e;
                                }
                            }
                        });
                if (produceMetadata) {
                    Path metadataFile = taskContextDir.resolve(Consts.MH_METADATA_YAML_FILE_NAME);
                    try {
                        Files.writeString(metadataFile, MetadataAggregateFunctionParamsYamlUtils.BASE_YAML_UTILS.toString(mafpy));
                    } catch (IOException e) {
                        final String es = "#979.200 error";
                        log.error(es, e);
                        if (policy==ErrorControlPolicy.fail) {
                            throw new RuntimeException(es, e);
                        }
                    }
                }
            };

            switch(resultType) {
                case zip -> {
                    Path zipFile = tempDir.resolve("result-for-"+outputVariable.id+'-'+outputVariable.name+".zip");
                    ZipUtils.createZip(outputDir, zipFile);

                    VariableSyncService.getWithSyncVoidForCreation(outputVariable.id,
                            ()-> variableTxService.storeDataInVariable(outputVariable, zipFile));
                }
                case text -> {
                    String text = variables.stream().map(v-> variableTxService.getVariableDataAsString(v.id)).collect(Collectors.joining("\n\n"));
                    VariableSyncService.getWithSyncVoidForCreation(outputVariable.id,
                            ()-> variableTxService.storeStringInVariable(outputVariable, text));
                }
                case ww2003 -> {
                    Path ww2003File = tempDir.resolve("result-for-"+outputVariable.id+'-'+outputVariable.name+".xml");
                    aggregateToWW2003Service.aggregate(ww2003File, variables);

                    VariableSyncService.getWithSyncVoidForCreation(outputVariable.id,
                            ()-> variableTxService.storeDataInVariable(outputVariable, ww2003File));
                }
            }
        }
        finally {
            if (tempDir!=null) {
                try {
                    PathUtils.deleteDirectory(tempDir);
                } catch (Throwable th) {
                    log.error("Error", th);
                }
            }
        }
    }

    @Nullable
    public static List<String> getNamesOfVariables(List<Map<String, String>> metas) {
        String[] namesBefore = StringUtils.split(MetaUtils.getValue(metas, VARIABLES), ",");
        if (namesBefore==null || namesBefore.length==0) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String s : namesBefore) {
            List<String> vars = ScenarioUtils.getVariables(s.strip(), true);
            if (vars.isEmpty()) {
                continue;
            }
            final String str = vars.get(0);
            if (!S.b(str)) {
                names.add(StrUtils.getVariableName(str, ()-> {
                    throw new InternalFunctionException(
                            name_of_variable_in_meta_is_broken, "#979.480 Meta 'variables' wasn't found or empty, s: " + s);
                }));
            }
        }
        return names.isEmpty() ? null : names;
    }
}
