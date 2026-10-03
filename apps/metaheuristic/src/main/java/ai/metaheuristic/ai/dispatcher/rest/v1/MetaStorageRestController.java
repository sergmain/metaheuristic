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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.MetaStorageRegistry;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.MetaStorageViewData;
import ai.metaheuristic.ai.dispatcher.data.SimpleCompany;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageCloneService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageCloneUtils;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageData;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageDescriptionService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageDownloadService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageDropService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageIndexUtils;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageSyntheticService;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRegistryRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageSyntheticRepository;
import ai.metaheuristic.ai.sec.SecConsts;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final MetaStorageDropService metaStorageDropService;
    private final MetaStorageDownloadService metaStorageDownloadService;
    private final MetaStorageCloneService metaStorageCloneService;
    private final MetaStorageDescriptionService metaStorageDescriptionService;

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

        // One GROUP BY per tab answers both which tables exist and when each was last written to.
        final List<MetaStorageData.TypeStat> stats = acrossCompanies
                ? allTypeStats(production)
                : ownTypeStats(userContextService.getContext(authentication).getCompanyId(), production);
        final List<MetaStorageData.TypeRef> refs = stats.stream().map(MetaStorageData.TypeStat::ref).toList();
        final Map<MetaStorageData.TypeRef, Long> lastUpdatedAt = lastUpdatedAtByTable(stats);

        // One read for every description on the tab, rather than one lookup per table.
        // The same single read of the registry also gives every table's CREATED_ON.
        final List<MetaStorageRegistry> descriptors = metaStorageRegistryRepository.findAllByProd(production);
        final Map<MetaStorageData.TypeRef, String> descriptions = descriptionsByTable(descriptors, production);
        final Map<MetaStorageData.TypeRef, Long> createdOn = createdOnByTable(descriptors);

        // ❗ The entitlement difference is carried entirely by this lambda - id -> null leaves the
        // owner-company column empty, and nothing inside the join re-decides who may see what.
        final Function<Long, @Nullable String> companyNames = acrossCompanies
                ? companyNameResolver(refs)
                : id -> null;

        return new MetaStorageViewData.MetaTablesResult(production, acrossCompanies,
                MetaStorageIndexUtils.index(refs, descriptions::get, companyNames, createdOn::get, lastUpdatedAt::get));
    }

    private List<MetaStorageData.TypeStat> allTypeStats(boolean production) {
        return production
                ? metaStorageRepository.findAllTypeStats()
                : metaStorageSyntheticRepository.findAllTypeStats();
    }

    private List<MetaStorageData.TypeStat> ownTypeStats(Long companyId, boolean production) {
        return production
                ? metaStorageRepository.findTypeStatsByCompanyId(companyId)
                : metaStorageSyntheticRepository.findTypeStatsByCompanyId(companyId);
    }

    private static Map<MetaStorageData.TypeRef, Long> lastUpdatedAtByTable(List<MetaStorageData.TypeStat> stats) {
        final Map<MetaStorageData.TypeRef, Long> result = new HashMap<>();
        for (MetaStorageData.TypeStat s : stats) {
            result.put(s.ref(), s.lastUpdatedAt());
        }
        return result;
    }

    /**
     * The record keys of one meta table.
     *
     * <p>{@code companyId} is honoured ONLY for a caller entitled to the whole installation, and
     * ignored for everyone else in favour of the principal's own company. ❗ That asymmetry is the
     * whole of the tenant isolation on this endpoint: an ADMIN who adds another company's id to the
     * query string gets their OWN company's data - not an error, and not the other company's.
     *
     * <p>❗ The page SIZE is not taken from the request. {@code Pageable} carries whatever {@code
     * ?size=} said, and the services clamp it to their own constant before it reaches a query - a
     * meta table can hold any number of records, so an unclamped size is a whole-store read on
     * demand. Only the page NUMBER is the caller's to choose.
     */
    @GetMapping("/meta-tables/{metaTable}/rec-keys")
    public MetaStorageViewData.MetaTableRecordsResult recKeys(
            Authentication authentication,
            Pageable pageable,
            @PathVariable("metaTable") String metaTable,
            @RequestParam(name = "companyId", required = false) @Nullable Long companyId,
            @RequestParam(name = "production", required = false, defaultValue = "true") boolean production) {

        final Long scopedCompanyId = scopeCompanyId(authentication, companyId);
        final Page<String> recKeys = production
                ? metaStorageService.listKeys(scopedCompanyId, metaTable, pageable)
                : metaStorageSyntheticService.listKeys(scopedCompanyId, metaTable, pageable);

        // getTotalElements(), never getContent().size() - the count query already ran, and passing
        // the page's own size here would make totalPages 1 and disable Next on every full page.
        return new MetaStorageViewData.MetaTableRecordsResult(scopedCompanyId, metaTable, production,
                recKeys.getContent(),
                new MetaStorageViewData.PageInfo(recKeys.getSize(), recKeys.getNumber(),
                        recKeys.getTotalElements(), recKeys.getTotalPages()));
    }

    /**
     * One record's body, fetched when the reader opens it rather than with the list.
     *
     * <p>❗ The body is returned verbatim. MH never parses or validates a body, so this endpoint does
     * not either - it is supposed to be JSON and may not be, and turning a malformed body into an
     * error here would make the one screen that could show you the problem the one screen that
     * refuses to.
     */
    @GetMapping("/meta-tables/{metaTable}/record")
    public MetaStorageViewData.MetaTableRecordResult record(
            Authentication authentication,
            @PathVariable("metaTable") String metaTable,
            @RequestParam(name = "recKey") String recKey,
            @RequestParam(name = "companyId", required = false) @Nullable Long companyId,
            @RequestParam(name = "production", required = false, defaultValue = "true") boolean production) {

        final Long scopedCompanyId = scopeCompanyId(authentication, companyId);
        final List<MetaStorageData.Record> records = production
                ? metaStorageService.select(scopedCompanyId, metaTable, List.of(recKey))
                : metaStorageSyntheticService.select(scopedCompanyId, metaTable, List.of(recKey));

        return records.isEmpty()
                ? new MetaStorageViewData.MetaTableRecordResult(scopedCompanyId, metaTable, production, recKey, false, null)
                : new MetaStorageViewData.MetaTableRecordResult(scopedCompanyId, metaTable, production, recKey, true,
                        records.get(0).body());
    }

    /**
     * Drop one whole meta table: every record under it in the chosen store, and its descriptor.
     *
     * <p>❗ {@code production} is REQUIRED here, unlike on the reads. A read defaulting to Production
     * lands the first load where the screen lands; a delete defaulting to it would make the
     * irreversible outcome the one a client gets by forgetting a parameter. A record in
     * MH_META_STORAGE cannot be recovered, so the store has to be stated.
     *
     * <p>❗ {@code companyId} goes through {@link #scopeCompanyId} exactly as on the reads: for an
     * ADMIN a foreign id resolves to their own company, so this endpoint cannot drop another tenant's
     * table. The result echoes the company the drop actually ran in.
     */
    @DeleteMapping("/meta-tables/{metaTable}")
    public MetaStorageViewData.MetaTableDropResult dropMetaTable(
            Authentication authentication,
            @PathVariable("metaTable") String metaTable,
            @RequestParam(name = "companyId", required = false) @Nullable Long companyId,
            @RequestParam(name = "production") boolean production) {

        final Long scopedCompanyId = scopeCompanyId(authentication, companyId);
        log.info("01.945.040 drop of meta table '{}' of company #{} (production={}) requested by '{}'",
                metaTable, scopedCompanyId, production, authentication.getName());
        final MetaStorageDropService.DropResult r = metaStorageDropService.drop(scopedCompanyId, metaTable, production);
        return new MetaStorageViewData.MetaTableDropResult(scopedCompanyId, metaTable, production, r.deleted(), r.hadDescriptor());
    }

    /**
     * Clone one whole meta table into a new one: inside its domain when {@code targetProduction}
     * equals {@code production}, between domains when it does not.
     *
     * <p>❗ Both store flags are REQUIRED, for the reason the drop gives: this writes, and a write into
     * MH_META_STORAGE cannot be undone, so which store receives the records has to be stated rather than
     * be the outcome of an omitted parameter.
     *
     * <p>❗ {@code companyId} goes through {@link #scopeCompanyId} exactly as on the drop. Source and
     * target are in that one company: a clone never moves data between tenants.
     *
     * <p>A refused or incomplete clone is an ordinary response with {@code status=ERROR} and the reason
     * in {@code errorMessages}, not an HTTP error - the request was well-formed and the server knows
     * exactly why it did not, or did not fully, do it.
     */
    @PostMapping("/meta-tables/{metaTable}/clone")
    public MetaStorageViewData.MetaTableCloneResult cloneMetaTable(
            Authentication authentication,
            @PathVariable("metaTable") String metaTable,
            @RequestParam(name = "companyId", required = false) @Nullable Long companyId,
            @RequestParam(name = "production") boolean production,
            @RequestParam(name = "targetMetaTable") String targetMetaTable,
            @RequestParam(name = "targetProduction") boolean targetProduction) {

        final Long scopedCompanyId = scopeCompanyId(authentication, companyId);
        log.info("01.945.060 clone of meta table '{}' of company #{} (production={}) into '{}' (production={}) requested by '{}'",
                metaTable, scopedCompanyId, production, targetMetaTable, targetProduction, authentication.getName());
        final MetaStorageCloneService.CloneResult r =
                metaStorageCloneService.clone(scopedCompanyId, metaTable, production, targetMetaTable, targetProduction);

        return r.ok()
                ? new MetaStorageViewData.MetaTableCloneResult(scopedCompanyId, metaTable, production, targetMetaTable, targetProduction,
                        r.copied(), r.descriptorCopied(), EnumsApi.OperationStatus.OK, List.of(),
                        List.of("Meta table '" + metaTable + "' was cloned into '" + targetMetaTable + "' in "
                                + MetaStorageCloneUtils.storeName(targetProduction) + ": " + r.copied() + " record(s)"
                                + (r.descriptorCopied() ? ", with its descriptor" : ", it had no descriptor")))
                : new MetaStorageViewData.MetaTableCloneResult(scopedCompanyId, metaTable, production, targetMetaTable, targetProduction,
                        r.copied(), false, EnumsApi.OperationStatus.ERROR, List.of(r.error()), List.of());
    }

    /**
     * Set the description of one meta table - the {@code desc} of its registry descriptor in the
     * chosen store. A table with a descriptor keeps every other field of it and its CREATED_ON; a
     * table without one gets a descriptor carrying only the description.
     *
     * <p>{@code description} arrives as a form field rather than in the query string: it is free text
     * of any length up to the limit, and a URL is the wrong place for it.
     *
     * <p>❗ {@code production} is REQUIRED, as on every write here - which store's descriptor changes
     * has to be stated. {@code companyId} is scoped exactly as on the drop.
     */
    @PostMapping("/meta-tables/{metaTable}/description")
    public MetaStorageViewData.MetaTableDescriptionResult updateDescription(
            Authentication authentication,
            @PathVariable("metaTable") String metaTable,
            @RequestParam(name = "companyId", required = false) @Nullable Long companyId,
            @RequestParam(name = "production") boolean production,
            @RequestParam(name = "description") String description) {

        final Long scopedCompanyId = scopeCompanyId(authentication, companyId);
        log.info("01.945.080 description of meta table '{}' of company #{} (production={}) edited by '{}'",
                metaTable, scopedCompanyId, production, authentication.getName());
        final MetaStorageDescriptionService.DescriptionResult r =
                metaStorageDescriptionService.updateDescription(scopedCompanyId, metaTable, production, description);

        return r.ok()
                ? new MetaStorageViewData.MetaTableDescriptionResult(scopedCompanyId, metaTable, production, r.description(), r.created(),
                        EnumsApi.OperationStatus.OK, List.of(),
                        List.of("Description of meta table '" + metaTable + "' in " + MetaStorageCloneUtils.storeName(production)
                                + (r.created() ? " was registered" : " was updated")))
                : new MetaStorageViewData.MetaTableDescriptionResult(scopedCompanyId, metaTable, production, null, false,
                        EnumsApi.OperationStatus.ERROR, List.of(r.error()), List.of());
    }

    /**
     * One whole meta table as a zip: a directory named after the table, one file per record holding
     * the body verbatim. Built synchronously in a temp dir, streamed, then removed by
     * {@code CleanerInterceptor}.
     *
     * <p>{@code production} defaults to true like the other reads - this one changes nothing.
     * {@code companyId} is scoped exactly as on the reads.
     */
    @GetMapping(value = "/meta-tables/{metaTable}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadMetaTable(
            HttpServletRequest request,
            Authentication authentication,
            @PathVariable("metaTable") String metaTable,
            @RequestParam(name = "companyId", required = false) @Nullable Long companyId,
            @RequestParam(name = "production", required = false, defaultValue = "true") boolean production) {

        final Long scopedCompanyId = scopeCompanyId(authentication, companyId);
        final CleanerInfo resource = metaStorageDownloadService.download(scopedCompanyId, metaTable, production);
        // Handed over BEFORE the null check: a failed export may already have created its temp dir.
        request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        return resource.entity==null
                ? new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE)
                : resource.entity;
    }

    /**
     * Which company's data the caller is actually allowed to read.
     *
     * <p>A requested companyId is a REQUEST, honoured only for a caller entitled across companies.
     * For everyone else the principal's own company wins silently rather than raising - an ADMIN
     * following a stale link carrying someone else's id should see their own table, not a refusal
     * that confirms the other company's id meant something.
     */
    private Long scopeCompanyId(Authentication authentication, @Nullable Long requestedCompanyId) {
        if (requestedCompanyId!=null && isMainAdmin(authentication)) {
            return requestedCompanyId;
        }
        return userContextService.getContext(authentication).getCompanyId();
    }

    /**
     * Descriptions keyed by (companyId, metaTable) - the same pair the listing is keyed by, and the
     * one the registry's unique index declares.
     *
     * ❗ A descriptor whose PARAMS will not parse costs its own description and nothing else. The
     * alternative - letting it propagate - loses the whole listing to one malformed row, and the
     * listing is how an operator would find that row in the first place.
     */
    private Map<MetaStorageData.TypeRef, String> descriptionsByTable(List<MetaStorageRegistry> descriptors, boolean production) {
        final Map<MetaStorageData.TypeRef, String> result = new HashMap<>();
        for (MetaStorageRegistry r : descriptors) {
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
     * CREATED_ON keyed by (companyId, metaTable), the same pair as the descriptions.
     *
     * <p>Read from the column, so - unlike the description - it needs no PARAMS parse and cannot be
     * lost to a malformed one.
     */
    private static Map<MetaStorageData.TypeRef, Long> createdOnByTable(List<MetaStorageRegistry> descriptors) {
        final Map<MetaStorageData.TypeRef, Long> result = new HashMap<>();
        for (MetaStorageRegistry r : descriptors) {
            if (r.metaTable==null || r.companyId==null) {
                continue;
            }
            result.put(new MetaStorageData.TypeRef(r.companyId, r.metaTable), r.createdOn);
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
