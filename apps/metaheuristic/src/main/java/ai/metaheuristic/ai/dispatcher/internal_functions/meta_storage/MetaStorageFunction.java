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
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageSyntheticService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.dispatcher.InternalFunction;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
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
import java.util.function.Function;
import java.util.function.ToIntFunction;

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
 *   type        INPUT VARIABLE holding the entity kind      (required)
 *   keys        input variable holding one recKey per line   (optional, action=select)
 *   output      name of the output variable                  (required, action=select)
 *   content     input variable holding what to write         (required, action=upsert)
 *   synthetic   INPUT VARIABLE holding true or false         (optional, both actions)
 *   (action=delete: keys and synthetic are both REQUIRED, see "delete" below)
 * </pre>
 *
 * <p>{@code synthetic} names a VARIABLE, the same indirection {@code type} uses and for the same
 * reason: which table a run writes to is a property of the RUN, and a meta is fixed when the .mhsc
 * is authored. A development trial belongs in MH_META_STORAGE_SYNTHETIC and the real thing in
 * MH_META_STORAGE, and nothing about that is decidable at authoring time.
 *
 * <p>It routes BOTH actions to the same table. Omitting it on one of a store/select
 * pair is the way to get a well-formed empty answer out of a store that really does hold the record.
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
 * <p><b>delete</b> removes the records {@code keys} names from the table {@code synthetic} names - the
 * step that takes a record off a queue once the work it stood for is done. Both metas are REQUIRED
 * here, unlike for the other actions, because a delete has no undo: a missing {@code keys} is never
 * read as "every record of the type", and the {@code synthetic} variable must hold exactly true or
 * false, so production records are removed only when a run said false in as many words. Nothing is
 * removed until both are resolved. A key that matches no record is skipped, so a replayed delete
 * succeeds. It writes no output variable.
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
    private static final String CONTENT = "content";
    private static final String SYNTHETIC = "synthetic";

    private static final String ACTION_SELECT = "select";
    private static final String ACTION_UPSERT = "upsert";
    private static final String ACTION_DELETE = "delete";

    private final MetaStorageService metaStorageService;
    private final MetaStorageSyntheticService metaStorageSyntheticService;
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
        final String typeVarName = MetaUtils.getValue(taskParamsYaml.task.metas, TYPE);
        if (S.b(typeVarName)) {
            throw new InternalFunctionException(meta_not_found, "01.942.040 meta '" + TYPE + "' wasn't found or it's blank");
        }
        // The meta names a VARIABLE and the variable holds the type. A type minted at runtime - one
        // per ExecContext, per subject, per batch of work - cannot travel in a meta, because a meta is
        // fixed when the .mhsc is authored and this function used to overwrite every record with it.
        // Naming the variable instead is the indirection the RG functions already use for their
        // inputs: meta says which name, the variable says which value.
        final String typeFromVariable = internalFunctionVariableService.getValueOfVariable(
            simpleExecContext.execContextId, taskContextId, typeVarName);
        if (S.b(typeFromVariable)) {
            throw new InternalFunctionException(data_not_found,
                "01.942.042 variable '" + typeVarName + "' is empty, it must hold the meta storage type");
        }
        final String type = typeFromVariable.strip();
        try {
            switch (action) {
                case ACTION_SELECT -> processSelect(simpleExecContext, taskId, taskContextId, taskParamsYaml, type);
                case ACTION_UPSERT -> processUpsert(simpleExecContext, taskContextId, taskParamsYaml, type);
                case ACTION_DELETE -> processDelete(simpleExecContext, taskContextId, taskParamsYaml, type);
                default -> throw new InternalFunctionException(source_code_is_broken,
                    "01.942.060 unknown action '" + action + "', supported: " + ACTION_SELECT + ", " + ACTION_UPSERT + ", " + ACTION_DELETE);
            }
        }
        catch (InternalFunctionException e) {
            throw e;
        }
        catch (CommonRollbackException e) {
            if (e.status== EnumsApi.OperationStatus.ERROR) {
                throw new InternalFunctionException(general_error,
                    "01.942.064 action '" + action + "', error: " + e.getMessage());
            }
            if (log.isDebugEnabled()) {
                log.info("01.942.066 error: {}", e.infoMessage());
            }
        }
        catch (Throwable th) {
            throw new InternalFunctionException(general_error,
                "01.942.068 action '" + action + "', error: " + th.getMessage());
        }
    }

    /**
     * The synthetic flag, read from the VARIABLE that the {@code synthetic} meta names.
     *
     * <p>Same indirection as {@code type}, for the same reason: which table a run writes to is a
     * property of the RUN rather than of the pipeline, and a meta is fixed when the .mhsc is authored.
     *
     * <p>An absent meta means false, which is the PRODUCTION table. That keeps every .mhsc written
     * before this change working, but note which way that default fails: a pipeline which says nothing
     * writes production rows. The discipline that prevents it lives in the .mhsc - each one names a
     * variable here - and not in this default, which is why the default is worth knowing rather than
     * relying on.
     */
    private boolean resolveSynthetic(
        ExecContextApiData.SimpleExecContext simpleExecContext, String taskContextId, TaskParamsYaml taskParamsYaml) {

        final String varName = MetaUtils.getValue(taskParamsYaml.task.metas, SYNTHETIC);
        if (S.b(varName)) {
            return false;
        }
        final String value = internalFunctionVariableService.getValueOfVariable(
            simpleExecContext.execContextId, taskContextId, varName);
        return value!=null && "true".equalsIgnoreCase(value.strip());
    }
    private void processSelect(
        ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
        TaskParamsYaml taskParamsYaml, String type) {

        final String outputName = MetaUtils.getValue(taskParamsYaml.task.metas, OUTPUT);
        if (S.b(outputName)) {
            throw new InternalFunctionException(meta_not_found, "01.942.080 meta '" + OUTPUT + "' wasn't found or it's blank");
        }

        final boolean synthetic = resolveSynthetic(simpleExecContext, taskContextId, taskParamsYaml);
        final List<String> recKeys = readKeys(simpleExecContext, taskContextId, taskParamsYaml);
        // The same meta that routes an upsert routes the read, and for the same reason: the two
        // tables carry identical columns and allocate ids independently, so nothing in a recKey or a
        // type distinguishes them. Only the process says which store it meant. Reading the other one
        // is not an error at any layer below - it returns a well-formed answer out of the wrong
        // table, or an empty one - so the branch has to be here.
        final List<MetaStorageData.Record> records = synthetic
            ? metaStorageSyntheticService.select(simpleExecContext.companyId, type, recKeys)
            : metaStorageService.select(simpleExecContext.companyId, type, recKeys);

        final TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.stream()
            .filter(o -> o.name.equals(outputName))
            .findFirst()
            .orElseThrow(() -> new InternalFunctionException(output_variable_not_found,
                "01.942.100 output variable not found '" + outputName + "'"));

        final String json = JsonUtils.getMapper().writeValueAsString(records);
        VariableSyncService.getWithSyncVoid(outputVariable.id,
            () -> variableTxService.storeStringInVariable(simpleExecContext.execContextId, taskId, outputVariable, json));

        log.info("01.942.120 select type: {}, synthetic: {}, keys: {}, records: {}", type, synthetic, recKeys==null ? "<all>" : recKeys.size(), records.size());
    }

    private void processUpsert(
        ExecContextApiData.SimpleExecContext simpleExecContext, String taskContextId,
        TaskParamsYaml taskParamsYaml, String type) {

        final boolean synthetic = resolveSynthetic(simpleExecContext, taskContextId, taskParamsYaml);
        final String contentName = MetaUtils.getValue(taskParamsYaml.task.metas, CONTENT);
        if (S.b(contentName)) {
            throw new InternalFunctionException(meta_not_found, "01.942.140 meta '" + CONTENT + "' wasn't found or it's blank");
        }
        final String json = internalFunctionVariableService.getValueOfVariable(
            simpleExecContext.execContextId, taskContextId, contentName);
        if (S.b(json)) {
            throw new InternalFunctionException(data_not_found,
                "01.942.160 variable '" + contentName + "' is empty");
        }
        // ❗ The ENVELOPE only - the JSON array of {type, recKey, body} that this function defines as
        // its wire format. It says nothing about a body: a body's encoding is the caller's decision
        // and may not be JSON at all, so it is transported as a string and never inspected. Only the
        // envelope is MH's own contract and therefore MH's to validate. The check exists for the
        // error code: without it a malformed variable escapes as a raw JacksonException through
        // @SneakyThrows, naming neither the variable nor the cause.
        if (!JsonUtils.isValidJson(json)) {
            throw new InternalFunctionException(source_code_is_broken,
                "01.942.170 variable '" + contentName + "' is not well-formed JSON, length: " + json.length());
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
            // the resolved type wins over whatever a record carries - the process declares the kind
            records.add(new MetaStorageData.Record(type, r.recKey(), r.body()));
        }

        final int rows;
        if (synthetic) {
            rows = metaStorageSyntheticService.upsert(simpleExecContext.companyId, records);
        }
        else {
            rows = metaStorageService.upsert(simpleExecContext.companyId, records);
        }
        log.info("01.942.220 upsert type: {}, records: {}, rows: {}", type, records.size(), rows);
    }

    /**
     * Action {@code delete}: remove the records {@code keys} names from the table {@code synthetic} names.
     *
     * <p>Both arguments are resolved and checked BEFORE the first record is removed. MH keeps no history
     * of a meta storage record, so a Task that is going to fail on its arguments has to fail while the
     * store is still untouched - never half way through a key list.
     *
     * <p>Each key goes through {@code deleteByNaturalKey}, which resolves the row outside any transaction
     * and treats a key that matches nothing as a no-op: a replayed delete succeeds instead of failing on
     * the records its first pass already removed.
     */
    private void processDelete(
        ExecContextApiData.SimpleExecContext simpleExecContext, String taskContextId,
        TaskParamsYaml taskParamsYaml, String type) {

        final Function<String, @Nullable String> valueOf = varName ->
            internalFunctionVariableService.getValueOfVariable(simpleExecContext.execContextId, taskContextId, varName);
        final boolean synthetic = syntheticForDelete(MetaUtils.getValue(taskParamsYaml.task.metas, SYNTHETIC), valueOf);
        final List<String> recKeys = keysForDelete(MetaUtils.getValue(taskParamsYaml.task.metas, KEYS),
            readKeys(simpleExecContext, taskContextId, taskParamsYaml));

        // the same branch processSelect and processUpsert make: the two tables carry identical columns and
        // allocate ids independently, so only the process says which store it meant
        final ToIntFunction<String> deleteOne = synthetic
            ? recKey -> metaStorageSyntheticService.deleteByNaturalKey(simpleExecContext.companyId, type, recKey)
            : recKey -> metaStorageService.deleteByNaturalKey(simpleExecContext.companyId, type, recKey);
        final int deleted = recKeys.stream().mapToInt(deleteOne).sum();

        log.info("01.942.280 delete type: {}, synthetic: {}, keys: {}, deleted: {}", type, synthetic, recKeys.size(), deleted);
    }

    /**
     * The table a {@code delete} addresses - deliberately stricter than {@code resolveSynthetic}.
     *
     * <p>{@code resolveSynthetic} reads an absent meta, and any value other than {@code true}, as the
     * PRODUCTION table. That keeps older pipelines working for select and upsert, and it is the wrong
     * default for an operation with no undo: a pipeline that forgot to say, or said {@code ture}, would
     * remove production records. So a delete requires the meta, requires its variable to hold exactly
     * {@code true} or {@code false} (case and surrounding whitespace aside), and fails on anything else.
     *
     * @param syntheticVarName the value of meta {@code synthetic} - the NAME of the variable holding the flag
     * @param valueOf reads a variable's value by name, null when the variable holds nothing
     */
    static boolean syntheticForDelete(@Nullable String syntheticVarName, Function<String, @Nullable String> valueOf) {
        if (S.b(syntheticVarName)) {
            throw new InternalFunctionException(meta_not_found,
                "01.942.240 meta '" + SYNTHETIC + "' is required for action '" + ACTION_DELETE
                + "', it names the variable that says which table the records are deleted from");
        }
        final String value = valueOf.apply(syntheticVarName);
        final String flag = value==null ? "" : value.strip();
        if ("true".equalsIgnoreCase(flag)) {
            return true;
        }
        if ("false".equalsIgnoreCase(flag)) {
            return false;
        }
        throw new InternalFunctionException(general_business_error,
            "01.942.250 variable '" + syntheticVarName + "' must hold true or false for action '" + ACTION_DELETE
            + "', got '" + flag + "'");
    }

    /**
     * The keys a {@code delete} removes - required, and never empty.
     *
     * <p>{@code select} reads a missing {@code keys} as "every record of the type". A delete that read it
     * the same way would empty the type, so here a missing meta is an error rather than a wildcard, and so
     * is a key list with nothing in it: both mean the process did not say what to delete.
     *
     * @param keysVarName the value of meta {@code keys} - the NAME of the variable holding the recKeys
     * @param recKeys what {@code readKeys} returned for that meta - null when the meta is absent
     */
    static List<String> keysForDelete(@Nullable String keysVarName, @Nullable List<String> recKeys) {
        if (S.b(keysVarName) || recKeys==null) {
            throw new InternalFunctionException(meta_not_found,
                "01.942.260 meta '" + KEYS + "' is required for action '" + ACTION_DELETE
                + "', without it a key list means every record of the type, and a delete never means that");
        }
        if (recKeys.isEmpty()) {
            throw new InternalFunctionException(data_not_found,
                "01.942.270 variable '" + keysVarName + "' holds no recKey, there is nothing to delete");
        }
        return recKeys;
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
