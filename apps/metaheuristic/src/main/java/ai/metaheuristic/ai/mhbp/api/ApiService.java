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
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 4/11/2023
 * Time: 12:39 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ApiService {

    private final ApiRepository apiRepository;

    public ApiData.Apis getApis(Pageable pageable, DispatcherContext context) {
        pageable = PageUtils.fixPageSize(20, pageable);

        Page<Api> apis = apiRepository.findAllByCompanyUniqueId(pageable, context.getCompanyId());
        List<ApiData.SimpleApi> list = apis.stream().map(ApiData.SimpleApi::new).toList();
        var sorted = list.stream().sorted((o1, o2)->Long.compare(o2.id, o1.id)).collect(Collectors.toList());
        return new ApiData.Apis(new PageImpl<>(sorted, pageable, list.size()));
    }

    public List<Api> getApisAllowedForCompany(DispatcherContext context) {
        List<Api> apis = apiRepository.findAllByCompanyUniqueId(context.getCompanyId());
        return apis;
    }

    @Transactional
    public OperationStatusRest deleteApiById(Long apiId, DispatcherContext context) {
        if (apiId==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        Api api = apiRepository.findById(apiId).orElse(null);
        if (api == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "217.050 API wasn't found, apiId: " + apiId, null);
        }
        if (api.companyId!=context.getCompanyId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "217.100  apiId: " + apiId);
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

    public ApiData.Api getApiAsData(@Nullable Long apiId, DispatcherContext context) {
        Api api = getApi(apiId, context);
        if (api==null) {
            return new ApiData.Api("217.150 Not found");
        }
        return new ApiData.Api(new ApiData.SimpleApi(api));
    }

    @Nullable
    public Api getApi(@Nullable Long apiId, DispatcherContext context) {
        if (apiId==null) {
            return null;
        }
        Api api = apiRepository.findById(apiId).orElse(null);
        if (api == null) {
            return null;
        }
        if (api.getCompanyId()!=context.getCompanyId()) {
            throw new AccessDeniedException("Access denied for apiId " + apiId);
        }
        return api;
    }
}
