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
package ai.metaheuristic.ai.dispatcher.internal_functions.meta_storage_registry;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageRegistryTxService;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import ai.metaheuristic.api.dispatcher.InternalFunction;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * {@code mh.meta-storage-registry} - register, from inside a pipeline, what a meta storage table is for.
 *
 * <p>Before this existed the registry was reachable only from the MCP surface, so a workflow could
 * create a table and had no way to say what it was: registration fell to whoever launched the run, and
 * an unregistered table was the normal outcome rather than an oversight. A run that produces a table
 * knows everything worth recording about it at the moment it produces it, which is the moment to write
 * it down.
 *
 * <p><b>Every value arrives in a VARIABLE; the metas only say which variable.</b> The same indirection
 * {@code mh.meta-storage} uses for {@code type} and {@code synthetic}, and for the same reason: a
 * descriptor describes ONE run - its table name is minted per run, and whether that run was production
 * is decided at launch - so none of it can be fixed when the .mhsc is authored. A meta holding the
 * description itself would make the .mhsc single-use, which is what {@code DAHF} 10 prohibits.
 *
 * <p><b>metas</b> - each names an input variable:
 * <pre>
 *   meta-table       variable holding the described table's name          (required)
 *   production       variable holding the production flag                 (required)
 *   desc             variable holding the one-sentence description        (required)
 *   rec-key-format   variable holding the recKey shape                    (required)
 *   body-format      variable holding the body encoding                   (required)
 *   consumer         variable holding the consumer protocol               (optional)
 *   function         variable holding the Function code that wrote it     (optional)
 * </pre>
 *
 * <p>{@code producer} and {@code execContextId} are NOT inputs. They are facts about the run, and the
 * run already knows them - asking the .mhsc to pass its own SourceCode uid would invite a descriptor
 * that names the wrong graph, which is precisely the drift a registry exists to prevent.
 *
 * <p>{@code production} follows the same rule everywhere else: the literal {@code true} means the
 * production store, and every other value - including the {@code mh.null-value} sentinel - means
 * development. An absent or misspelled flag therefore describes a synthetic table, which is the
 * failure a re-run repairs.
 *
 * <p>Error code prefix: {@code 01.943.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MetaStorageRegistryFunction implements InternalFunction {

    private static final String META_TABLE = "meta-table";
    private static final String PRODUCTION = "production";
    private static final String DESC = "desc";
    private static final String REC_KEY_FORMAT = "rec-key-format";
    private static final String BODY_FORMAT = "body-format";
    private static final String CONSUMER = "consumer";
    private static final String FUNCTION = "function";

    private final MetaStorageRegistryTxService metaStorageRegistryTxService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final SourceCodeRepository sourceCodeRepository;

    @Override
    public String getCode() {
        return Consts.MH_META_STORAGE_REGISTRY_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_META_STORAGE_REGISTRY_FUNCTION;
    }

    @Override
    public void process(
        ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
        TaskParamsYaml taskParamsYaml) {

        TxUtils.checkTxNotExists();

        final String metaTable = required(simpleExecContext, taskContextId, taskParamsYaml, META_TABLE);
        final String production = required(simpleExecContext, taskContextId, taskParamsYaml, PRODUCTION);
        final boolean prod = "true".equals(production.strip());

        final MetaStorageRegistryParams params = new MetaStorageRegistryParams();
        params.desc = required(simpleExecContext, taskContextId, taskParamsYaml, DESC);
        params.recKeyFormat = required(simpleExecContext, taskContextId, taskParamsYaml, REC_KEY_FORMAT);
        params.bodyFormat = required(simpleExecContext, taskContextId, taskParamsYaml, BODY_FORMAT);
        params.consumer = optional(simpleExecContext, taskContextId, taskParamsYaml, CONSUMER);
        params.function = optional(simpleExecContext, taskContextId, taskParamsYaml, FUNCTION);
        params.execContextId = simpleExecContext.execContextId;
        params.producer = producerUid(simpleExecContext);

        metaStorageRegistryTxService.upsert(simpleExecContext.companyId, metaTable.strip(), prod, params);

        log.info("01.943.220 registered meta table '{}' (prod: {}) by '{}', execContextId: {}",
            metaTable.strip(), prod, params.producer, params.execContextId);
    }

    /**
     * The SourceCode uid of the running graph. Falls back to the numeric id when the row cannot be
     * read: a descriptor that names the producer imprecisely is worth more than a failed registration,
     * because the alternative leaves the table undescribed entirely.
     */
    private String producerUid(ExecContextApiData.SimpleExecContext simpleExecContext) {
        final SourceCodeImpl sc = sourceCodeRepository.findById(simpleExecContext.sourceCodeId).orElse(null);
        return sc==null ? ("sourceCodeId#" + simpleExecContext.sourceCodeId) : sc.uid;
    }

    private String required(
        ExecContextApiData.SimpleExecContext simpleExecContext, String taskContextId,
        TaskParamsYaml taskParamsYaml, String metaName) {

        final String varName = MetaUtils.getValue(taskParamsYaml.task.metas, metaName);
        if (S.b(varName)) {
            throw new InternalFunctionException(meta_not_found,
                "01.943.020 meta '" + metaName + "' wasn't found or it's blank");
        }
        final String value = internalFunctionVariableService.getValueOfVariable(
            simpleExecContext.execContextId, taskContextId, varName);
        if (S.b(value)) {
            throw new InternalFunctionException(data_not_found,
                "01.943.040 variable '" + varName + "', named by meta '" + metaName + "', is empty");
        }
        return value;
    }

    @Nullable
    private String optional(
        ExecContextApiData.SimpleExecContext simpleExecContext, String taskContextId,
        TaskParamsYaml taskParamsYaml, String metaName) {

        final String varName = MetaUtils.getValue(taskParamsYaml.task.metas, metaName);
        if (S.b(varName)) {
            return null;
        }
        final String value = internalFunctionVariableService.getValueOfVariable(
            simpleExecContext.execContextId, taskContextId, varName);
        return S.b(value) ? null : value;
    }
}