/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.event.LaunchpadInternalEvent;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeStoredParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.YamlVersion;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.SourceCode;
import ai.metaheuristic.api.launchpad.ExecContext;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.OK;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class SourceCodeService {

    private final ExecContextRepository execContextRepository;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeRepository sourceCodeRepository;

    private final ExecContextService execContextService;
    private final CommonProcessValidatorService commonProcessValidatorService;
    private final FunctionRepository functionRepository;
    private final ExecContextFSM execContextFSM;
    private final CompanyCache companyCache;

    @Async
    @EventListener
    public void handleAsync(LaunchpadInternalEvent.SourceCodeLockingEvent event) {
        setLockedTo(event.sourceCodeId, event.companyUniqueId, event.lock);
    }

    public SourceCodeData.SourceCodesForCompany getAvailableSourceCodesForCompany(DispatcherContext context) {
        return getAvailableSourceCodesForCompany(context.getCompanyId());
    }

    public SourceCodeData.SourceCodesForCompany getSourceCode(Long companyId, Long sourceCodeId) {
        SourceCodeData.SourceCodesForCompany availableSourceCodesForCompany = getAvailableSourceCodesForCompany(companyId, (o) -> o.getId().equals(sourceCodeId));
        if (availableSourceCodesForCompany.items.size()>1) {
            log.error("!!!!!!!!!!!!!!!! error in code -  (sourceCodeForBatchResult.items.size()>1) !!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        return availableSourceCodesForCompany;
    }

    public SourceCodeData.SourceCodesForCompany getAvailableSourceCodesForCompany(Long companyId) {
        return getAvailableSourceCodesForCompany(companyId, (f) -> true);
    }

    private SourceCodeData.SourceCodesForCompany getAvailableSourceCodesForCompany(Long companyUniqueId, final Function<SourceCode, Boolean> sourceCodeFilter) {
        final SourceCodeData.SourceCodesForCompany sourceCodesForCompany = new SourceCodeData.SourceCodesForCompany();
        sourceCodesForCompany.items = sourceCodeRepository.findAllAsSourceCode(companyUniqueId).stream().filter(sourceCodeFilter::apply).filter(o->{
            if (!o.isValid()) {
                return false;
            }
            try {
                SourceCodeStoredParamsYaml scspy = SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams());
                return !scspy.internalParams.archived;
            } catch (YAMLException e) {
                final String es = "#995.010 Can't parse SourceCode params. It's broken or unknown version. SourceCode id: #" + o.getId();
                sourceCodesForCompany.addErrorMessage(es);
                log.error(es);
                log.error("#995.015 Params:\n{}", o.getParams());
                log.error("#995.020 Error: {}", e.toString());
                return false;
            }
        }).collect(Collectors.toList());

        Company company = companyCache.findByUniqueId(companyUniqueId);
        if (!S.b(company.getParams())) {
            final Set<String> groups = new HashSet<>();
            try {
                CompanyParamsYaml cpy = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(company.getParams());
                if (cpy.ac!=null && !S.b(cpy.ac.groups)) {
                    String[] arr = StringUtils.split(cpy.ac.groups, ',');
                    Stream.of(arr).forEach(s-> groups.add(s.strip()));
                }
            } catch (YAMLException e) {
                final String es = "#995.025 Can't parse Company params. It's broken or version is unknown. Company companyUniqueId: #" + companyUniqueId;
                sourceCodesForCompany.addErrorMessage(es);
                log.error(es);
                log.error("#995.027 Params:\n{}", company.getParams());
                log.error("#995.030 Error: {}", e.toString());
                return sourceCodesForCompany;
            }

            if (!groups.isEmpty()) {
                List<SourceCode> commonSourceCodes = sourceCodeRepository.findAllAsSourceCode(Consts.ID_1).stream().filter(sourceCodeFilter::apply).filter(o -> {
                    if (!o.isValid()) {
                        return false;
                    }
                    try {
                        SourceCodeParamsYaml ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams());
                        if (ppy.source.ac!=null) {
                            String[] arr = StringUtils.split(ppy.source.ac.groups, ',');
                            return Stream.of(arr).map(String::strip).anyMatch(groups::contains);
                        }
                        return false;
                    } catch (YAMLException e) {
                        final String es = "#995.033 Can't parse SourceCode params. It's broken or unknown version. SourceCode id: #" + o.getId();
                        sourceCodesForCompany.addErrorMessage(es);
                        log.error(es);
                        log.error("#995.035 Params:\n{}", o.getParams());
                        log.error("#995.037 Error: {}", e.toString());
                        return false;
                    }
                }).collect(Collectors.toList());
                sourceCodesForCompany.items.addAll(commonSourceCodes);
            }
        }
        sourceCodesForCompany.items.sort((o1, o2) -> Long.compare(o2.getId(), o1.getId()));

        return sourceCodesForCompany;
    }

    // TODO 2019.05.19 add reporting of producing of tasks
    // TODO 2020.01.17 reporting to where? do we need to implement it?
    public synchronized void createAllTasks() {

        Monitoring.log("##019", Enums.Monitor.MEMORY);
        List<ExecContextImpl> execContexts = execContextRepository.findByExecState(EnumsApi.ExecContextState.PRODUCING.code);
        Monitoring.log("##020", Enums.Monitor.MEMORY);
        if (!execContexts.isEmpty()) {
            log.info("#701.020 Start producing tasks");
        }
        for (ExecContextImpl execContext : execContexts) {
            SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
            if (sourceCode==null) {
                execContextFSM.toStopped(execContext.id);
                continue;
            }
            Monitoring.log("##021", Enums.Monitor.MEMORY);
            log.info("#701.030 Producing tasks for sourceCode.code: {}, input resource pool: \n{}",sourceCode.uid, execContext.getParams());
            produceAllTasks(true, sourceCode, execContext);
            Monitoring.log("##022", Enums.Monitor.MEMORY);
        }
        if (!execContexts.isEmpty()) {
            log.info("#701.040 Producing of tasks was finished");
        }
    }

    public SourceCodeApiData.SourceCodeValidation validateInternal(SourceCodeImpl sourceCode) {
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = getSourceCodesValidation(sourceCode);
        setValidTo(sourceCode, sourceCodeValidation.status == EnumsApi.SourceCodeValidateStatus.OK );
        if (sourceCode.isValid() || sourceCodeValidation.status==OK) {
            if (sourceCode.isValid() && sourceCodeValidation.status!=OK) {
                log.error("#701.097 Need to investigate: (sourceCode.isValid() && sourceCodeValidation.status!=OK)");
            }
            sourceCodeValidation.infoMessages = Collections.singletonList("Validation result: OK");
        }
        else {
            final String es = "#701.100 Validation error: " + sourceCodeValidation.status;
            log.error(es);
            sourceCodeValidation.addErrorMessage(es);
        }
        return sourceCodeValidation;
    }

    private SourceCodeApiData.SourceCodeValidation getSourceCodesValidation(SourceCodeImpl sourceCode) {
        final SourceCodeApiData.SourceCodeValidation sourceCodeValidation = new SourceCodeApiData.SourceCodeValidation();
        try {
            sourceCodeValidation.status = validate(sourceCode);
        } catch (YAMLException e) {
            sourceCodeValidation.addErrorMessage("#701.090 Error while parsing yaml config, " + e.toString());
            sourceCodeValidation.status = EnumsApi.SourceCodeValidateStatus.YAML_PARSING_ERROR;
        }
        return sourceCodeValidation;
    }

    private final static Object syncObj = new Object();

    private void setValidTo(SourceCode sourceCode, boolean valid) {
        synchronized (syncObj) {
            SourceCodeImpl p = sourceCodeRepository.findByIdForUpdate(sourceCode.getId(), sourceCode.getCompanyId());
            if (p!=null && p.isValid()!=valid) {
                p.setValid(valid);
                saveInternal(p);
            }
            sourceCode.setValid(valid);
        }
    }

    private void setLockedTo(Long sourceCodeId, Long companyUniqueId, boolean locked) {
        synchronized (syncObj) {
            SourceCodeImpl p = sourceCodeRepository.findByIdForUpdate(sourceCodeId, companyUniqueId);
            if (p!=null && p.isLocked()!=locked) {
                p.setLocked(locked);
                saveInternal(p);
            }
        }
    }

    private void saveInternal(SourceCodeImpl sourceCode) {
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        scspy.internalParams.updatedOn = System.currentTimeMillis();
        sourceCode.updateParams(scspy);

        sourceCodeCache.save(sourceCode);
    }

    public SourceCodeApiData.TaskProducingResultComplex produceAllTasks(boolean isPersist, SourceCodeImpl sourceCode, ExecContext execContext) {
        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();
        if (isPersist && execContext.getState()!= EnumsApi.ExecContextState.PRODUCING.code) {
            result.sourceCodeValidateStatus = EnumsApi.SourceCodeValidateStatus.ALREADY_PRODUCED_ERROR;
            return result;
        }
        long mills = System.currentTimeMillis();
        result.sourceCodeValidateStatus = validate(sourceCode);
        log.info("#701.150 SourceCode was validated for "+(System.currentTimeMillis() - mills) + " ms.");
        if (result.sourceCodeValidateStatus != EnumsApi.SourceCodeValidateStatus.OK &&
                result.sourceCodeValidateStatus != EnumsApi.SourceCodeValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR ) {
            log.error("#701.160 Can't produce tasks, error: {}", result.sourceCodeValidateStatus);
            if(isPersist) {
                execContextFSM.toStopped(execContext.getId());
            }
            return result;
        }
        Monitoring.log("##022", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        result = execContextService.produceTasks(isPersist, sourceCode, execContext.getId());
        log.info("#701.170 SourceCodeService.produceTasks() was processed for "+(System.currentTimeMillis() - mills) + " ms.");
        Monitoring.log("##033", Enums.Monitor.MEMORY);

        return result;
    }

    public EnumsApi.SourceCodeValidateStatus validate(SourceCodeImpl sourceCode) {
        if (sourceCode==null) {
            return EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(sourceCode.uid)) {
            return EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_UID_EMPTY_ERROR;
        }
        if (StringUtils.isBlank(sourceCode.getParams())) {
            return EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_PARAMS_EMPTY_ERROR;
        }
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
        SourceCodeParamsYaml.SourceCodeYaml sourceCodeYaml = sourceCodeParamsYaml.source;
        if (sourceCodeYaml.getProcesses().isEmpty()) {
            return EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR;
        }

        SourceCodeParamsYaml.Process lastProcess = null;
        List<SourceCodeParamsYaml.Process> processes = sourceCodeYaml.getProcesses();
        for (int i = 0; i < processes.size(); i++) {
            SourceCodeParamsYaml.Process process = processes.get(i);
            if (i + 1 < processes.size()) {
                if (process.output.isEmpty()) {
                    return EnumsApi.SourceCodeValidateStatus.PROCESS_PARAMS_EMPTY_ERROR;
                }
                for (SourceCodeParamsYaml.Variable params : process.output) {
                    if (S.b(params.name)) {
                        return EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLE_NOT_DEFINED_ERROR;
                    }
                    if (params.sourcing==null) {
                        return EnumsApi.SourceCodeValidateStatus.SOURCING_OF_VARIABLE_NOT_DEFINED_ERROR;
                    }
                }
            }
            lastProcess = process;
            if (S.b(process.code) || !StrUtils.isCodeOk(process.code)){
                log.error("Error while validating sourceCode {}", sourceCodeYaml);
                return EnumsApi.SourceCodeValidateStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            EnumsApi.SourceCodeValidateStatus status;
            status = checkFunctions(sourceCode, process);
            if (status!=OK) {
                return status;
            }
        }
        return EnumsApi.SourceCodeValidateStatus.OK;
    }

    private EnumsApi.SourceCodeValidateStatus checkFunctions(SourceCode sourceCode, SourceCodeParamsYaml.Process process) {
        YamlVersion v = YamlForVersioning.getYamlForVersion().load(sourceCode.getParams());

        if (process.function !=null) {
            SourceCodeParamsYaml.FunctionDefForSourceCode snDef = process.function;
            if (snDef.context== EnumsApi.FunctionExecContext.internal) {
                if (!Consts.MH_INTERNAL_FUNCTIONS.contains(snDef.code)) {
                    return EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR;
                }
            }
            else {
                EnumsApi.SourceCodeValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Function wasn't found for code: {}, process: {}", snDef.code, process);
                    return x;
                }
            }
        }
        if (process.preFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.preFunctions) {
                EnumsApi.SourceCodeValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Pre-function {} wasn't found", snDef.code);
                    return x;
                }
            }
        }
        if (process.postFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.postFunctions) {
                EnumsApi.SourceCodeValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Post-function {} wasn't found", snDef.code);
                    return x;
                }
            }
        }

        return OK;
    }

    private EnumsApi.SourceCodeValidateStatus checkRequiredVersionOfTaskParams(int sourceCodeYamlAsStr, SourceCodeParamsYaml.Process process, SourceCodeParamsYaml.FunctionDefForSourceCode snDef) {
        if (StringUtils.isNotBlank(snDef.code)) {
            Long functionId = functionRepository.findIdByCode(snDef.code);
            if (functionId == null) {
                log.error("#177.030 function wasn't found for code: {}, process: {}", snDef.code, process);
                return EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR;
            }
        }
        else {
            log.error("#177.060 function wasn't found for code: {}, process: {}", snDef.code, process);
            return EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR;
        }
        if (!commonProcessValidatorService.checkRequiredVersion(sourceCodeYamlAsStr, snDef)) {
            return EnumsApi.SourceCodeValidateStatus.VERSION_OF_FUNCTION_IS_TOO_LOW_ERROR;
        }
        return OK;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProduceTaskResult {
        public EnumsApi.SourceCodeProducingStatus status;
        public List<String> outputResourceCodes;
        public int numberOfTasks=0;
        public List<Long> taskIds = new ArrayList<>();

        public ProduceTaskResult(EnumsApi.SourceCodeProducingStatus status) {
            this.status = status;
        }
    }

    @Data
    @NoArgsConstructor
    public static class ResourcePools {
        public final Map<String, List<String>> collectedInputs = new HashMap<>();
        public Map<String, SourceCodeParamsYaml.Variable> inputStorageUrls=null;
        public final Map<String, String> mappingCodeToOriginalFilename = new HashMap<>();
        public EnumsApi.SourceCodeProducingStatus status = EnumsApi.SourceCodeProducingStatus.OK;

        public ResourcePools(List<SimpleVariableAndStorageUrl> initialInputResourceCodes) {

            if (initialInputResourceCodes==null || initialInputResourceCodes.isEmpty()) {
                status = EnumsApi.SourceCodeProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
                return;
            }

            initialInputResourceCodes.forEach(o->
                collectedInputs.computeIfAbsent(o.variable, p -> new ArrayList<>()).add(o.id)
            );

            initialInputResourceCodes.forEach(o-> mappingCodeToOriginalFilename.put(o.id, o.originalFilename));

            inputStorageUrls = initialInputResourceCodes.stream()
                    .collect(Collectors.toMap(o -> o.id, o -> {
                        DataStorageParams p = o.getParams();
                        return new SourceCodeParamsYaml.Variable(p.sourcing, p.git, p.disk, p.storageType);
                    }));

        }

        public void clean() {
            collectedInputs.values().forEach(o-> o.forEach(inputStorageUrls::remove));
            collectedInputs.clear();
            mappingCodeToOriginalFilename.clear();
        }

        public void add(String outputType, List<String> outputResourceCodes) {
            if (outputResourceCodes!=null) {
                collectedInputs.computeIfAbsent(outputType, k -> new ArrayList<>()).addAll(outputResourceCodes);
            }
        }

        public void merge(ResourcePools metaPools) {
            metaPools.collectedInputs.forEach((key, value) -> collectedInputs.merge(
                    key, value, (o, o1) -> {o.addAll(o1); return o;} )
            );
            inputStorageUrls.putAll(metaPools.inputStorageUrls);
            mappingCodeToOriginalFilename.putAll((metaPools.mappingCodeToOriginalFilename));
        }
    }

}
