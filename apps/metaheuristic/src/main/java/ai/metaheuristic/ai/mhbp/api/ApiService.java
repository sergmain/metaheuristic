/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.account.UserContext;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

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
    private final ApiTxService apiTxService;

    public ApiData.Apis getApis(Pageable pageable, UserContext context) {
        pageable = PageUtils.fixPageSize(20, pageable);

        Page<Api> apis = apiRepository.findAllByCompanyUniqueId(pageable, context.getCompanyId());
        List<ApiData.SimpleApi> list = apis.stream().map(ApiData.SimpleApi::new).toList();
        var sorted = list.stream().sorted((o1, o2)->Long.compare(o2.id, o1.id)).collect(Collectors.toList());
        return new ApiData.Apis(new PageImpl<>(sorted, pageable, list.size()));
    }

    public List<Api> getApisAllowedForCompany(UserContext context) {
        List<Api> apis = apiRepository.findAllByCompanyUniqueId(context.getCompanyId());
        return apis;
    }

    public ApiData.Api getApiAsData(@Nullable Long apiId, UserContext context) {
        Api api = getApi(apiId, context);
        if (api==null) {
            return new ApiData.Api("217.150 Not found");
        }
        return new ApiData.Api(new ApiData.SimpleApi(api));
    }

    @Nullable
    public Api getApi(@Nullable Long apiId, UserContext context) {
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

    public OperationStatusRest createApi(String yaml, UserContext context) {
        try {
            return apiTxService.createApi(yaml, context);
        } catch (CommonRollbackException e) {
            return switch (e.status) {
                case OK -> OperationStatusRest.OPERATION_STATUS_OK;
                case ERROR -> new OperationStatusRest(e.status, e.messages);
                case INFO -> new OperationStatusRest(e.status, e.messages, null);
            };
        }
    }

}
