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

package ai.metaheuristic.ai.mhbp.auth;

import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.mhbp.beans.Auth;
import ai.metaheuristic.ai.mhbp.data.AuthData;
import ai.metaheuristic.ai.mhbp.repositories.AuthRepository;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 4/13/2023
 * Time: 12:03 AM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AuthService {

    private final AuthRepository authRepository;
    private final AuthTxService authTxService;

    public AuthData.Auths getAuths(Pageable pageable, UserContext context) {
        pageable = PageUtils.fixPageSize(20, pageable);

        Page<Auth> auths = authRepository.findAllByCompanyUniqueId(pageable, context.getCompanyId());
        List<AuthData.SimpleAuth> list = auths.stream().map(AuthData.SimpleAuth::new).toList();
        var sorted = list.stream().sorted((o1, o2)->Long.compare(o2.id, o1.id)).collect(Collectors.toList());
        return new AuthData.Auths(new PageImpl<>(sorted, pageable, list.size()));
    }

    public AuthData.Auth getAuth(@Nullable Long authId, UserContext context) {
        if (authId==null) {
            return new AuthData.Auth("247.120 Not found");
        }
        Auth auth = authRepository.findById(authId).orElse(null);
        if (auth == null) {
            return new AuthData.Auth("247.160 Not found");
        }
        if (auth.companyId!=context.getCompanyId()) {
            return new AuthData.Auth("247.200 Illegal access");
        }
        return new AuthData.Auth(new AuthData.SimpleAuth(auth));
    }

    public OperationStatusRest createAuth(String yaml, UserContext context) {
        try {
            return authTxService.createAuth(yaml, context);
        } catch (CommonRollbackException e) {
            return switch (e.status) {
                case OK -> OperationStatusRest.OPERATION_STATUS_OK;
                case ERROR -> new OperationStatusRest(e.status, e.messages);
                case INFO -> new OperationStatusRest(e.status, e.messages, null);
            };
        }
    }
}
