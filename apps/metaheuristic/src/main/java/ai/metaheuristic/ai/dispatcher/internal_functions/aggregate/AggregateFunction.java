/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.event.ResourceCloseTxEvent;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
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
    private final ApplicationEventPublisher eventPublisher;

    public static final String META_ERROR_CONTROL = "error-control-policy";
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
    public InternalFunctionProcessingResult process(
            ExecContextImpl execContext, TaskImpl task, String taskContextId,
            ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxExists();

        if (taskParamsYaml.task.outputs.size()!=1) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.number_of_outputs_is_incorrect,
                    "#992.020 There must be only one output variable, current: "+ taskParamsYaml.task.outputs);
        }

        Variable variable;
        TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
        if (outputVariable.context==EnumsApi.VariableContext.local) {
            variable = variableRepository.findById(outputVariable.id).orElse(null);
            if (variable == null) {
                throw new IllegalStateException("#992.040 Variable not found for code " + outputVariable);
            }
        }
        else {
            throw new IllegalStateException("#992.060 GlobalVariable not found for code " + outputVariable);
        }

        String[] names = StringUtils.split(MetaUtils.getValue(taskParamsYaml.task.metas, "variables"), ", ");
        if (names==null || names.length==0) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#992.080 Meta 'variables' wasn't found or empty, process: "+ taskParamsYaml.task.processCode);
        }
        String policyMeta = MetaUtils.getValue(taskParamsYaml.task.metas, META_ERROR_CONTROL);
        ErrorControlPolicy policy = S.b(policyMeta) ? ErrorControlPolicy.ignore : ErrorControlPolicy.valueOf(policyMeta);

        List<SimpleVariable> list = variableRepository.getIdAndStorageUrlInVarsForExecContext(execContext.id, names);

        File tempDir = DirUtils.createTempDir("mh-aggregate-internal-context-");
        if (tempDir==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "#992.100 Can't create temporary directory in dir "+ SystemUtils.JAVA_IO_TMPDIR);
        }
        File outputDir = new File(tempDir, outputVariable.name);
        if (!outputDir.mkdirs()) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "#992.120 Can't create output directory  "+ outputDir.getAbsolutePath());
        }

        list.stream().map(o->o.taskContextId).collect(Collectors.toSet())
                .forEach(contextId->{
                    File taskContextDir = new File(outputDir, contextId);
                    //noinspection ResultOfMethodCallIgnored
                    taskContextDir.mkdirs();
                    list.stream().filter(t-> contextId.equals(t.taskContextId))
                            .forEach( v->{
                                try {
                                    File varFile = new File(taskContextDir, v.variable);
                                    variableService.storeToFile(v.id, varFile);
                                } catch (VariableDataNotFoundException e) {
                                    if (v.nullified) {
                                        return;
                                    }
                                    log.error("#992.140 Variable #{}, name {},  wasn't found", v.id, v.variable);
                                    if (policy==ErrorControlPolicy.fail) {
                                        throw e;
                                    }
                                }
                            });
                });

        File zipFile = new File(tempDir, "result-for-"+outputVariable.name+".zip");

        ZipUtils.createZip(outputDir, zipFile);
        try {
            InputStream is = new FileInputStream(zipFile);
            eventPublisher.publishEvent(new ResourceCloseTxEvent(is));
            variableService.update(is, zipFile.length(), variable);
        } catch (FileNotFoundException e) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "Can't open zipFile   "+ zipFile.getAbsolutePath());
        }

        return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.ok);
    }
}
