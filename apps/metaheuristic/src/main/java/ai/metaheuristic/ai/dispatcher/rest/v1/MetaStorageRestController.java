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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.beans.MetaStorageRegistry;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.MetaStorageViewData;
import ai.metaheuristic.ai.dispatcher.data.SimpleCompany;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageData;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageIndexUtils;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageSyntheticService;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRegistryRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageSyntheticRepository;
import ai.metaheuristic.ai.sec.SecConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The meta storage browser - what tables exist, in which store, and what each is for.
 *
 * <p>Two entitlements, one endpoint. A MAIN_ADMIN belongs to the management company and sees every
 * table in the installation, with the owning company named; an ADMIN sees the tables of their own
 * company and no others. ❗ The company an ADMIN is scoped to resolves from the authentication
 * principal and NEVER from a request parameter, the same rule
 * {@code CompanyRestController#currentCompany} follows - an endpoint that accepted a companyId would
 * be a tenant-isolation hole wearing a filter.
 *
 * <p>The two tabs are one parameter rather than two endpoints because they differ only in which
 * physical table is read: MH_META_STORAGE for production, MH_META_STORAGE_SYNTHETIC otherwise.
 * Everything downstream of the read - the registry join, the placeholder, the company resolution -
 * is identical, and duplicating it per tab would be two copies of one rule.
 *
 * <p>Error code prefix: {@code 01.945.} (unique to this class).
 *
 * @author Serge
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/meta-storage")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@PreAuthorize("hasAnyRole('MAIN_ADMIN', 'ADMIN')")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MetaStorageRestController {

    private final MetaStorageService metaStorageService;
    private final MetaStorageSyntheticService metaStorageSyntheticService;
    private final MetaStorageRepository metaStorageRepository;
    private final MetaStorageSyntheticRepository metaStorageSyntheticRepository;
    private final MetaStorageRegistryRepository metaStorageRegistryRepository;
    private final CompanyRepository companyRepository;
    private final UserContextService userContextService;

    /**
     * One tab of the index: every meta table the caller is entitled to, with its description.
     *
     * <p>{@code production} defaults to true because Production is the default tab - a request that
     * arrives without the parameter is the first load of the screen, and it should land where the
     * screen lands.
     */
    @GetMapping("/meta-tables")
    public MetaStorageViewData.MetaTablesResult metaTables(
            Authentication authentication,
            @RequestParam(name = "production", required = false, defaultValue = "true") boolean production) {

        final boolean acrossCompanies = isMainAdmin(authentication);

        final List<MetaStorageData.TypeRef> refs = acrossCompanies
                ? allTypeRefs(production)
                : ownTypeRefs(userContextService.getContext(authentication).getCompanyId(), production);

        // One read for every description on the tab, rather than one lookup per table.
        final Map<MetaStorageData.TypeRef, String> descriptions = descriptionsByTable(production);

        // ❗ The entitlement difference is carried entirely by this lambda - id -> null leaves the
        // owner-company column empty, and nothing inside the join re-decides who may see what.
        final Function<Long, @Nullable String> companyNames = acrossCompanies
                ? companyNameResolver(refs)
                : id -> null;

        return new MetaStorageViewData.MetaTablesResult(production, acrossCompanies,
                MetaStorageIndexUtils.index(refs, descriptions::get, companyNames));
    }

    private List<MetaStorageData.TypeRef> allTypeRefs(boolean production) {
        return production
                ? metaStorageRepository.findAllTypeRefs()
                : metaStorageSyntheticRepository.findAllTypeRefs();
    }

    private List<MetaStorageData.TypeRef> ownTypeRefs(Long companyId, boolean production) {
        return MetaStorageIndexUtils.toTypeRefs(companyId, production
                ? metaStorageService.listTypes(companyId)
                : metaStorageSyntheticService.listTypes(companyId));
    }

    /**
     * Descriptions keyed by (companyId, metaTable) - the same pair the listing is keyed by, and the
     * one the registry's unique index declares.
     *
     * ❗ A descriptor whose PARAMS will not parse costs its own description and nothing else. The
     * alternative - letting it propagate - loses the whole listing to one malformed row, and the
     * listing is how an operator would find that row in the first place.
     */
    private Map<MetaStorageData.TypeRef, String> descriptionsByTable(boolean production) {
        final Map<MetaStorageData.TypeRef, String> result = new HashMap<>();
        for (MetaStorageRegistry r : metaStorageRegistryRepository.findAllByProd(production)) {
            if (r.metaTable==null || r.companyId==null) {
                continue;
            }
            try {
                result.put(new MetaStorageData.TypeRef(r.companyId, r.metaTable), r.getMetaStorageRegistryParams().desc);
            }
            catch (Throwable th) {
                log.warn("01.945.020 PARAMS of the descriptor of meta table '{}' (prod={}) can't be parsed, "
                        + "the table will be listed without a description, error: {}", r.metaTable, production, th.getMessage());
            }
        }
        return result;
    }

    /**
     * Names for exactly the companies this listing mentions.
     *
     * <p>⚠️ A company id with no company behind it renders as {@code #<id>} rather than as an empty
     * cell. Meta storage carries no foreign key to MH_COMPANY - records outlive the company row -
     * so this is a state the screen can genuinely reach, and an empty cell would read as a loading
     * failure instead of as a deleted company.
     */
    private Function<Long, @Nullable String> companyNameResolver(List<MetaStorageData.TypeRef> refs) {
        final List<Long> companyIds = refs.stream().map(MetaStorageData.TypeRef::companyId).distinct().toList();
        if (companyIds.isEmpty()) {
            return id -> null;
        }
        final Map<Long, String> names = new HashMap<>();
        for (SimpleCompany c : companyRepository.findAllAsSimpleByUniqueIds(companyIds)) {
            names.put(c.uniqueId, c.name);
        }
        return id -> names.getOrDefault(id, "#" + id);
    }

    /**
     * ❗ The role, not the company id. A ROLE_MAIN_* role is only grantable inside the management
     * company - see {@code SecConsts.MANAGEMENT_COMPANY_POSSIBLE_ROLES} and the note on
     * {@code AccountRoleEditUtils#validateToggle} - so holding it already implies the company, and
     * checking both would state the same fact twice with two chances to drift.
     */
    private static boolean isMainAdmin(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (SecConsts.ROLE_MAIN_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
