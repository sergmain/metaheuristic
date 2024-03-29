/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.auth;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.mhbp.beans.Auth;
import ai.metaheuristic.ai.mhbp.repositories.AuthRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.yaml.auth.ApiAuth;
import ai.metaheuristic.commons.yaml.auth.ApiAuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static ai.metaheuristic.api.EnumsApi.OperationStatus.ERROR;
import static ai.metaheuristic.api.EnumsApi.OperationStatus.INFO;

/**
 * @author Sergio Lissner
 * Date: 10/21/2023
 * Time: 1:07 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AuthTxService {

    private final AuthRepository authRepository;

    @Transactional
    public OperationStatusRest deleteAuthById(Long authId, DispatcherContext context) {
        if (authId==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        Auth auth = authRepository.findById(authId).orElse(null);
        if (auth == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                "246.040 API wasn't found, authId: " + authId, null);
        }
        if (auth.companyId!=context.getCompanyId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "247.080 authId: " + authId);
        }

        authRepository.deleteById(authId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest createAuth(String code, String params, DispatcherContext context) {
        Auth auth = new Auth();
        auth.code = code;
        auth.setParams(params);
        auth.companyId = context.getCompanyId();
        auth.accountId = context.getAccountId();
        auth.createdOn = System.currentTimeMillis();

        authRepository.save(auth);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional(rollbackFor = CommonRollbackException.class)
    public OperationStatusRest createAuth(String yaml, DispatcherContext context) {

        ApiAuth apiAuth = ApiAuthUtils.UTILS.to(yaml);
        Auth auth = authRepository.findByCode(apiAuth.auth.code);
        if (auth!=null) {
            throw new CommonRollbackException("246.120 Auth with code'"+apiAuth.auth.code+"' already exist", INFO);
        }

        auth = new Auth();
        auth.code = apiAuth.auth.code;
        auth.setParams(yaml);
        auth.companyId = context.getCompanyId();
        auth.accountId = context.getAccountId();
        auth.createdOn = System.currentTimeMillis();

        authRepository.save(auth);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest updateAuth(Long authId, String params, DispatcherContext context) {
        Auth auth = authRepository.findById(authId).orElse(null);
        if (auth == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                "246.160 API wasn't found, authId: " + authId, null);
        }
        if (auth.companyId!=context.getCompanyId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "246.200 authId: " + authId);
        }

        ApiAuth apiAuth = ApiAuthUtils.UTILS.to(params);
        auth.updateParams(apiAuth);

        authRepository.save(auth);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

}
