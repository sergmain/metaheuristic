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

package ai.metaheuristic.ai.mhbp.api;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.yaml.scheme.ApiScheme;
import ai.metaheuristic.commons.yaml.scheme.ApiSchemeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static ai.metaheuristic.api.EnumsApi.OperationStatus.ERROR;

/**
 * @author Sergio Lissner
 * Date: 10/21/2023
 * Time: 1:29 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ApiTxService {

    private final ApiRepository apiRepository;

    @Transactional
    public OperationStatusRest deleteApiById(Long apiId, DispatcherContext context) {
        if (apiId==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        Api api = apiRepository.findById(apiId).orElse(null);
        if (api == null) {
            return new OperationStatusRest(ERROR,
                "216.040 API wasn't found, apiId: " + apiId, null);
        }
        if (api.companyId!=context.getCompanyId()) {
            return new OperationStatusRest(ERROR, "217.100  apiId: " + apiId);
        }

        apiRepository.deleteById(apiId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest createApi(String name, String code, String scheme, DispatcherContext context) {
        Api api = new Api();
        api.name = name;
        api.code = code;
        api.setScheme(scheme);
        api.companyId = context.getCompanyId();
        api.accountId = context.getAccountId();

        apiRepository.save(api);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest createApi(String yaml, DispatcherContext context) {
        ApiScheme apiScheme = ApiSchemeUtils.UTILS.to(yaml);

        Api api = apiRepository.findByApiCode(apiScheme.code);
        if (api!=null) {
            throw new CommonRollbackException("216.080 Api scheme with code'"+apiScheme.code+"' already exist", ERROR);
        }

        api = new Api();
        api.name = apiScheme.code;
        api.code = apiScheme.code;
        api.setScheme(yaml);
        api.companyId = context.getCompanyId();
        api.accountId = context.getAccountId();

        apiRepository.save(api);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }



}
