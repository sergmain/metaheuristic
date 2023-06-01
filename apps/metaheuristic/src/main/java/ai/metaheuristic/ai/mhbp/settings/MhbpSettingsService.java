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

package ai.metaheuristic.ai.mhbp.settings;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Auth;
import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.mhbp.beans.ScenarioGroup;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.mhbp.repositories.AuthRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioGroupRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioRepository;
import ai.metaheuristic.ai.mhbp.yaml.backup.BackupParams;
import ai.metaheuristic.ai.mhbp.yaml.backup.BackupParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.exceptions.WrongVersionOfParamsException;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import static ai.metaheuristic.ai.Consts.YAML_EXT;
import static ai.metaheuristic.ai.Consts.YML_EXT;

/**
 * @author Sergio Lissner
 * Date: 5/20/2023
 * Time: 10:40 PM
 */
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
@Slf4j
public class MhbpSettingsService {

    private final ApiRepository apiRepository;
    private final AuthRepository authRepository;
    private final ScenarioGroupRepository scenarioGroupRepository;
    private final ScenarioRepository scenarioRepository;

    public OperationStatusRest importBackup(MultipartFile file, DispatcherContext context) {

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "561.040 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "561.080 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "561.120 only '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        try {
            BackupParams backupParams;
            String backupYamlAsStr;
            try (InputStream is = file.getInputStream()) {
                backupYamlAsStr = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                backupParams = BackupParamsUtils.UTILS.to(backupYamlAsStr);
            } catch (WrongVersionOfParamsException e) {
                String es = "561.160 An error parsing yaml: " + e.getMessage();
                log.error(es, e);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
            }
            OperationStatusRest result = importBackupParams(backupParams, context);
            return result;
        }
        catch (Throwable e) {
            log.error("561.200 Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "561.240 can't load source codes, Error: " + e.getMessage());
        }
    }

    public OperationStatusRest importBackupParams(BackupParams backupParams, DispatcherContext context) {
        OperationStatusRest result = new OperationStatusRest();
        backupParams.backup.apis.forEach(api -> storeApi(context, result, api));
        backupParams.backup.auths.forEach(auth -> storeAuth(context, result, auth));
        backupParams.backup.scenarioGroups.forEach(scenarioGroup -> storeScenarioGroup(context, result, scenarioGroup));

        if (result.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.getErrorMessagesAsList(), result.getInfoMessagesAsList());
        }

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private void storeScenarioGroup(DispatcherContext context, OperationStatusRest result, BackupParams.ScenarioGroup scenarioGroup) {
        ScenarioGroup bean = scenarioGroupRepository.findByName(scenarioGroup.name);
        if (bean!=null) {
            result.addInfoMessage("Auth with code '" + scenarioGroup.name + "' already exists in database, make a copy.");
        }
        String name = bean==null ? scenarioGroup.name : StrUtils.incCopyNumber(bean.name);

        final ScenarioGroup scenarioGroupBean = new ScenarioGroup();
        scenarioGroupBean.createdOn = scenarioGroup.createdOn;
        scenarioGroupBean.name = name;
        scenarioGroupBean.description = scenarioGroup.description;
        scenarioGroupBean.companyId = context.getCompanyId();
        scenarioGroupBean.accountId = context.getAccountId();
        scenarioGroupRepository.save(scenarioGroupBean);

        scenarioGroup.scenarios.forEach(scenario->storeScenario(context, result, scenarioGroupBean.id, scenario));
    }

    private void storeScenario(DispatcherContext context, OperationStatusRest result, Long scenarioGroupId, BackupParams.Scenario scenario) {
        Scenario scenarioBean = new Scenario();
        scenarioBean.scenarioGroupId = scenarioGroupId;
        scenarioBean.createdOn = scenario.createdOn;
        scenarioBean.name = scenario.name;
        scenarioBean.description = scenario.description;
        scenarioBean.accountId = context.getAccountId();
        scenarioBean.setParams(scenario.params);
        scenarioRepository.save(scenarioBean);
    }

    private void storeAuth(DispatcherContext context, OperationStatusRest result, BackupParams.Auth auth) {
        Auth authBean = authRepository.findByCode(auth.code);
        if (authBean!=null) {
            result.addInfoMessage("Auth with code '" + auth.code + "' already exists in database, skipped.");
            return;
        }
        authBean = new Auth();
        authBean.createdOn = auth.createdOn;
        authBean.code = auth.code;
        authBean.companyId = context.getCompanyId();
        authBean.accountId = context.getAccountId();
        authBean.setParams(auth.params);
        authRepository.save(authBean);
    }

    private void storeApi(DispatcherContext context, OperationStatusRest result, BackupParams.Api api) {
        Api apiBean = apiRepository.findByApiCode(api.code);
        if (apiBean!=null) {
            result.addInfoMessage("API with code '" + api.code + "' already exists in database, skipped.");
            return;
        }
        apiBean = new Api();
        apiBean.name = api.name;
        apiBean.code = api.code;
        apiBean.createdOn = api.createdOn;
        apiBean.companyId = context.getCompanyId();
        apiBean.accountId = context.getAccountId();
        apiBean.setScheme(api.scheme);
        apiRepository.save(apiBean);
    }

    public String exportBackup() {
        BackupParams b = new BackupParams();
        b.backup.apis = StreamSupport.stream(apiRepository.findAll().spliterator(), false).map(MhbpSettingsService::toApi).toList();
        b.backup.auths = StreamSupport.stream(authRepository.findAll().spliterator(), false).map(MhbpSettingsService::toAuth).toList();
        b.backup.scenarioGroups = scenarioGroupRepository.findAllAsList().stream().map(this::toScenarioGroup).toList();

        return BackupParamsUtils.UTILS.toString(b);
    }

    private BackupParams.ScenarioGroup toScenarioGroup(ScenarioGroup v1) {
        BackupParams.ScenarioGroup sg = new BackupParams.ScenarioGroup();
        sg.createdOn = v1.createdOn;
        sg.name = v1.name;
        sg.description = v1.description;
        sg.scenarios = scenarioRepository.findAllByScenarioGroupId(v1.id).stream().map(MhbpSettingsService::toScenario).toList();;

        return sg;
    }

    private static BackupParams.Scenario toScenario(Scenario v1) {
        BackupParams.Scenario s = new BackupParams.Scenario();
        s.createdOn = v1.createdOn;
        s.name = v1.name;
        s.description  =v1.description;
        s.params = v1.getParams();
        return s;
    }

    private static BackupParams.Auth toAuth(Auth v1) {
        BackupParams.Auth a = new BackupParams.Auth();
        a.createdOn = v1.createdOn;
        a.code = v1.code;
        a.params = v1.getParams();
        return a;
    }

    private static BackupParams.Api toApi(Api v1) {
        BackupParams.Api a = new BackupParams.Api();
        a.createdOn = v1.createdOn;
        a.code = v1.code;
        a.name = v1.name;
        a.scheme = v1.getScheme();

        return a;
    }

}
