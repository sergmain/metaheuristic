/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.internal_functions.meta_storage;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageData;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.dispatcher.InternalFunction;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.JsonUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * {@code mh.meta-storage} - the whole interaction between a {@code .mhsc} and the meta storage.
 *
 * <p>Generic by construction: every domain meaning arrives as a meta VALUE, so MH wires names and
 * never learns what any of them mean. A {@code .mhsc} that stores contacts and one that stores
 * triples use this same function with different {@code type} strings.
 *
 * <p><b>metas</b>
 * <pre>
 *   action      select | upsert            (required)
 *   type        entity kind, a free string (required)
 *   keys        input variable holding one recKey per line   (optional, action=select)
 *   output      name of the output variable                  (required, action=select)
 *   records     input variable holding the records to write  (required, action=upsert)
 * </pre>
 *
 * <p><b>Wire format.</b> {@code select} writes a JSON array of {@code {type, recKey, body}} and
 * {@code upsert} reads the same shape. ❗ {@code body} stays a STRING throughout - MH transports it
 * and never parses it. What is inside a body, and in what encoding, is the caller's business.
 *
 * <p><b>select</b> with no {@code keys} returns every record of that type, which is the selection
 * step that feeds {@code mh.batch-line-splitter}. With {@code keys} it returns exactly those
 * records, which is the per-batch payload fetch. Payloads are only ever materialised for a batch;
 * a key list is what travels whole.
 *
 * <p>Error code prefix: {@code 01.942.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MetaStorageFunction implements InternalFunction {

    private static final String ACTION = "action";
    private static final String TYPE = "type";
    private static final String KEYS = "keys";
    private static final String OUTPUT = "output";
    private static final String RECORDS = "records";

    private static final String ACTION_SELECT = "select";
    private static final String ACTION_UPSERT = "upsert";

    private final MetaStorageService metaStorageService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final VariableTxService variableTxService;

    @Override
    public String getCode() {
        return Consts.MH_META_STORAGE_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_META_STORAGE_FUNCTION;
    }

    @SneakyThrows
    @Override
    public void process(
            ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {

        TxUtils.checkTxNotExists();

        final String action = MetaUtils.getValue(taskParamsYaml.task.metas, ACTION);
        if (S.b(action)) {
            throw new InternalFunctionException(meta_not_found, "01.942.020 meta '" + ACTION + "' wasn't found or it's blank");
        }
        final String type = MetaUtils.getValue(taskParamsYaml.task.metas, TYPE);
        if (S.b(type)) {
            throw new InternalFunctionException(meta_not_found, "01.942.040 meta '" + TYPE + "' wasn't found or it's blank");
        }

        switch (action) {
            case ACTION_SELECT -> processSelect(simpleExecContext, taskId, taskContextId, taskParamsYaml, type);
            case ACTION_UPSERT -> processUpsert(simpleExecContext, taskContextId, taskParamsYaml, type);
            default -> throw new InternalFunctionException(source_code_is_broken,
                    "01.942.060 unknown action '" + action + "', supported: " + ACTION_SELECT + ", " + ACTION_UPSERT);
        }
    }

    private void processSelect(
            ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml, String type) throws Exception {

        final String outputName = MetaUtils.getValue(taskParamsYaml.task.metas, OUTPUT);
        if (S.b(outputName)) {
            throw new InternalFunctionException(meta_not_found, "01.942.080 meta '" + OUTPUT + "' wasn't found or it's blank");
        }

        final List<String> recKeys = readKeys(simpleExecContext, taskContextId, taskParamsYaml);
        final List<MetaStorageData.Record> records = metaStorageService.select(simpleExecContext.companyId, type, recKeys);

        final TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.stream()
                .filter(o -> o.name.equals(outputName))
                .findFirst()
                .orElseThrow(() -> new InternalFunctionException(output_variable_not_found,
                        "01.942.100 output variable not found '" + outputName + "'"));

        final String json = JsonUtils.getMapper().writeValueAsString(records);
        VariableSyncService.getWithSyncVoid(outputVariable.id,
                () -> variableTxService.storeStringInVariable(simpleExecContext.execContextId, taskId, outputVariable, json));

        log.info("01.942.120 select type: {}, keys: {}, records: {}", type, recKeys==null ? "<all>" : recKeys.size(), records.size());
    }

    private void processUpsert(
            ExecContextApiData.SimpleExecContext simpleExecContext, String taskContextId,
            TaskParamsYaml taskParamsYaml, String type) throws Exception {

        final String recordsName = MetaUtils.getValue(taskParamsYaml.task.metas, RECORDS);
        if (S.b(recordsName)) {
            throw new InternalFunctionException(meta_not_found, "01.942.140 meta '" + RECORDS + "' wasn't found or it's blank");
        }
        final String json = internalFunctionVariableService.getValueOfVariable(
                simpleExecContext.execContextId, taskContextId, recordsName);
        if (S.b(json)) {
            throw new InternalFunctionException(data_not_found,
                    "01.942.160 variable '" + recordsName + "' is empty");
        }

        final MetaStorageData.Record[] parsed = JsonUtils.getMapper().readValue(json, MetaStorageData.Record[].class);
        final List<MetaStorageData.Record> records = new ArrayList<>(parsed.length);
        for (MetaStorageData.Record r : parsed) {
            if (S.b(r.recKey())) {
                throw new InternalFunctionException(source_code_is_broken, "01.942.180 recKey is blank");
            }
            if (r.body()==null) {
                throw new InternalFunctionException(source_code_is_broken, "01.942.200 body is null, recKey: " + r.recKey());
            }
            // the 'type' meta wins over whatever a record carries - the .mhsc declares the kind
            records.add(new MetaStorageData.Record(type, r.recKey(), r.body()));
        }

        final int rows = metaStorageService.upsert(simpleExecContext.companyId, records);
        log.info("01.942.220 upsert type: {}, records: {}, rows: {}", type, records.size(), rows);
    }

    /**
     * Reads the optional key list. One recKey per line, which is the shape
     * {@code mh.batch-line-splitter} produces.
     */
    @Nullable
    private List<String> readKeys(
            ExecContextApiData.SimpleExecContext simpleExecContext, String taskContextId, TaskParamsYaml taskParamsYaml) {

        final String keysName = MetaUtils.getValue(taskParamsYaml.task.metas, KEYS);
        if (S.b(keysName)) {
            return null;
        }
        final String content = internalFunctionVariableService.getValueOfVariable(
                simpleExecContext.execContextId, taskContextId, keysName);
        if (S.b(content)) {
            return List.of();
        }
        return content.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
