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

package ai.metaheuristic.ai.dispatcher.internal_functions.aggregate;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextUtilsService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYaml;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYamlUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 3/13/2020
 * Time: 11:19 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class AggregateFunction implements InternalFunction {

    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final ExecContextVariableService execContextVariableService;
    private final ExecContextUtilsService execContextUtilsService;

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

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        if (taskParamsYaml.task.outputs.size()!=1) {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.number_of_outputs_is_incorrect,
                            "#979.020 There must be only one output variable, current: "+ taskParamsYaml.task.outputs));
        }

        final boolean produceMetadata = MetaUtils.isTrue(taskParamsYaml.task.metas, true, "produce-metadata");

        String[] names = StringUtils.split(MetaUtils.getValue(taskParamsYaml.task.metas, "variables"), ", ");
        if (names==null || names.length==0) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#979.080 Meta 'variables' wasn't found or empty, process: "+ taskParamsYaml.task.processCode));
        }

        String policyMeta = MetaUtils.getValue(taskParamsYaml.task.metas, META_ERROR_CONTROL);
        ErrorControlPolicy policy = S.b(policyMeta) ? ErrorControlPolicy.ignore : ErrorControlPolicy.valueOf(policyMeta);

        List<SimpleVariable> list = variableRepository.getIdAndStorageUrlInVarsForExecContext(simpleExecContext.execContextId, names);

        File tempDir = null;
        try {
            tempDir = DirUtils.createMhTempDir("mh-aggregate-internal-context-");
            if (tempDir==null) {
                throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                        "#979.100 Can't create temporary directory in dir "+ SystemUtils.JAVA_IO_TMPDIR));
            }

            TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
            File outputDir = new File(tempDir, outputVariable.name);
            if (!outputDir.mkdirs()) {
                throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                        "#979.120 Can't create output directory  "+ outputDir.getAbsolutePath()));
            }

            list.stream().map(o->o.taskContextId).collect(Collectors.toSet())
                    .forEach(contextId->{
                        File taskContextDir = new File(outputDir, contextId);
                        MetadataAggregateFunctionParamsYaml mafpy = new MetadataAggregateFunctionParamsYaml();
                        //noinspection ResultOfMethodCallIgnored
                        taskContextDir.mkdirs();
                        list.stream().filter(t-> contextId.equals(t.taskContextId))
                                .forEach( v->{
                                    if (v.nullified) {
                                        return;
                                    }
                                    try {
                                        String ext = execContextUtilsService.getExtensionForVariable(simpleExecContext.execContextVariableStateId, v.id, "");
                                        File varFile = new File(taskContextDir, v.variable+ext);
                                        if (produceMetadata) {
                                            mafpy.mapping.add(Map.of(varFile.getName(), v.variable));
                                        }
                                        variableService.storeToFileWithTx(v.id, varFile);
                                    } catch (VariableDataNotFoundException e) {
                                        log.error("#979.140 Variable #{}, name {},  wasn't found", v.id, v.variable);
                                        if (policy==ErrorControlPolicy.fail) {
                                            throw e;
                                        }
                                    }
                                });
                        if (produceMetadata) {
                            File metadataFile = new File(taskContextDir, Consts.MH_METADATA_YAML_FILE_NAME);
                            try {
                                FileUtils.writeStringToFile(metadataFile, MetadataAggregateFunctionParamsYamlUtils.BASE_YAML_UTILS.toString(mafpy), StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                final String es = "#979.200 error";
                                log.error(es, e);
                                if (policy==ErrorControlPolicy.fail) {
                                    throw new RuntimeException(es, e);
                                }
                            }
                        }

                    });

            File zipFile = new File(tempDir, "result-for-"+outputVariable.name+".zip");
            ZipUtils.createZip(outputDir, zipFile);

            execContextVariableService.storeDataInVariable(outputVariable, zipFile);
        }
        finally {
            if (tempDir!=null) {
                try {
                    FileUtils.deleteDirectory(tempDir);
                } catch (Throwable th) {
                    log.error("Error", th);
                }
            }
        }
    }
}
