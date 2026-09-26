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

package ai.metaheuristic.ai.dispatcher.meta_storage;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRegistryRepository;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Drop and Download of a whole meta table, through the real controller, the real services and the
 * real H2. MockMvc drives the actual dispatcher servlet with real security and the real
 * CleanerInterceptor, so company scoping, the parameter contract and the temp-dir cleanup are what
 * production does - not what a test said it would do.
 *
 * <p>The caller is SpringSecurityWebAuxTestConfig's "admin": ROLE_ADMIN in company 2, a caller scoped
 * to ONE company. That is the caller the controller's scoping has to hold for, so it is the one tenant
 * isolation is pinned against. Company 2 is seeded by the H2 initial revision.
 *
 * <p>Isolation on the shared DB is by unique table names from {@link SharedItEnv#uniqueCode}; a
 * foreign company comes from {@link SharedItEnv#uniqueLong()} - never 1L, the management company.
 *
 * @author Serge
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@Import({SpringSecurityWebAuxTestConfig.class})
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class MetaStorageRestControllerTest extends MhSharedItTest {

    /** SpringSecurityWebAuxTestConfig's "admin" belongs to company 2. */
    private static final long ADMIN_COMPANY_ID = 2L;

    private static final String META_TABLES = "/rest/v1/dispatcher/meta-storage/meta-tables/";

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired MetaStorageService metaStorageService;
    @Autowired MetaStorageSyntheticService metaStorageSyntheticService;
    @Autowired MetaStorageRegistryTxService metaStorageRegistryTxService;
    @Autowired MetaStorageRegistryRepository metaStorageRegistryRepository;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private static MetaStorageData.Record rec(String type, String recKey, String body) {
        return new MetaStorageData.Record(type, recKey, body);
    }

    private static MetaStorageRegistryParams desc(String text) {
        final MetaStorageRegistryParams p = new MetaStorageRegistryParams();
        p.desc = text;
        return p;
    }

    private boolean hasDescriptor(Long companyId, String type, boolean prod) {
        return metaStorageRegistryRepository.findByCompanyIdAndMetaTableAndProd(companyId, type, prod)!=null;
    }

    private record ZipContent(Set<String> dirs, Map<String, String> files) {}

    private static ZipContent readZip(byte[] bytes) throws IOException {
        final Set<String> dirs = new HashSet<>();
        final Map<String, String> files = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes), UTF_8)) {
            for (ZipEntry entry; (entry = zis.getNextEntry())!=null; ) {
                if (entry.isDirectory()) {
                    dirs.add(entry.getName());
                }
                else {
                    files.put(entry.getName(), new String(zis.readAllBytes(), UTF_8));
                }
            }
        }
        return new ZipContent(dirs, files);
    }

    // ---------- drop ----------

    @Test
    @WithUserDetails("admin")
    public void test_dropRemovesTheTableAndItsDescriptorFromTheProductionStoreOnly() throws Exception {
        final String type = SharedItEnv.uniqueCode("ms-drop");
        final String otherType = SharedItEnv.uniqueCode("ms-keep");

        // PHASE #1: the same table in both stores, a second table beside it, a descriptor in each store
        metaStorageService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "k-1", "p1"), rec(type, "k-2", "p2"), rec(otherType, "k-1", "o1")));
        metaStorageSyntheticService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "k-1", "s1")));
        metaStorageRegistryTxService.upsert(ADMIN_COMPANY_ID, type, true, desc("production " + type));
        metaStorageRegistryTxService.upsert(ADMIN_COMPANY_ID, type, false, desc("synthetic " + type));

        // PHASE #2: drop it from production
        mockMvc.perform(delete(META_TABLES + type).param("production", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value((int) ADMIN_COMPANY_ID))
                .andExpect(jsonPath("$.metaTable").value(type))
                .andExpect(jsonPath("$.production").value(true))
                .andExpect(jsonPath("$.deleted").value(2))
                .andExpect(jsonPath("$.hadDescriptor").value(true));

        // PHASE #3: the production table and its descriptor are gone
        assertEquals(List.of(), metaStorageService.listKeys(ADMIN_COMPANY_ID, type),
                "PHASE #3: every production record of the table must be gone");
        assertFalse(hasDescriptor(ADMIN_COMPANY_ID, type, true),
                "PHASE #3: the production descriptor goes with the production table");

        // PHASE #4: nothing else moved - not the synthetic twin, not its descriptor, not the neighbour
        assertEquals(List.of("k-1"), metaStorageSyntheticService.listKeys(ADMIN_COMPANY_ID, type),
                "PHASE #4: the synthetic table of the same name is a different table");
        assertTrue(hasDescriptor(ADMIN_COMPANY_ID, type, false),
                "PHASE #4: the synthetic descriptor describes the synthetic records, which still exist");
        assertEquals(List.of("k-1"), metaStorageService.listKeys(ADMIN_COMPANY_ID, otherType),
                "PHASE #4: a neighbouring table must be untouched");
    }

    @Test
    @WithUserDetails("admin")
    public void test_dropFromTheSyntheticStoreLeavesProductionAlone() throws Exception {
        final String type = SharedItEnv.uniqueCode("ms-drop-syn");

        // PHASE #1: one production record, two synthetic ones, a descriptor in each store
        metaStorageService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "k-1", "p1")));
        metaStorageSyntheticService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "k-1", "s1"), rec(type, "k-2", "s2")));
        metaStorageRegistryTxService.upsert(ADMIN_COMPANY_ID, type, true, desc("production " + type));
        metaStorageRegistryTxService.upsert(ADMIN_COMPANY_ID, type, false, desc("synthetic " + type));

        // PHASE #2: drop it from synthetic
        mockMvc.perform(delete(META_TABLES + type).param("production", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.production").value(false))
                .andExpect(jsonPath("$.deleted").value(2))
                .andExpect(jsonPath("$.hadDescriptor").value(true));

        // PHASE #3: synthetic gone, production intact
        assertEquals(List.of(), metaStorageSyntheticService.listKeys(ADMIN_COMPANY_ID, type), "PHASE #3: synthetic records");
        assertFalse(hasDescriptor(ADMIN_COMPANY_ID, type, false), "PHASE #3: synthetic descriptor");
        assertEquals(List.of("k-1"), metaStorageService.listKeys(ADMIN_COMPANY_ID, type), "PHASE #3: production records");
        assertTrue(hasDescriptor(ADMIN_COMPANY_ID, type, true), "PHASE #3: production descriptor");
    }

    @Test
    @WithUserDetails("admin")
    public void test_anAdminCannotDropAnotherCompanysTableByNamingItsId() throws Exception {
        final String type = SharedItEnv.uniqueCode("ms-drop-tenant");
        final Long otherCompanyId = SharedItEnv.uniqueLong();

        // PHASE #1: the same table name in the admin's company and in a foreign one
        metaStorageService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "mine", "m")));
        metaStorageService.upsert(otherCompanyId, List.of(rec(type, "theirs", "t")));
        metaStorageRegistryTxService.upsert(otherCompanyId, type, true, desc("theirs"));

        // PHASE #2: an ADMIN names the foreign company - the drop runs in their own company instead
        mockMvc.perform(delete(META_TABLES + type)
                        .param("production", "true")
                        .param("companyId", String.valueOf(otherCompanyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value((int) ADMIN_COMPANY_ID))
                .andExpect(jsonPath("$.deleted").value(1));

        // PHASE #3: the foreign table and its descriptor are exactly as they were
        assertEquals(List.of("theirs"), metaStorageService.listKeys(otherCompanyId, type),
                "PHASE #3: another company's records must be out of an ADMIN's reach");
        assertTrue(hasDescriptor(otherCompanyId, type, true),
                "PHASE #3: another company's descriptor must be out of an ADMIN's reach");
        assertEquals(List.of(), metaStorageService.listKeys(ADMIN_COMPANY_ID, type),
                "PHASE #3: the drop ran in the admin's own company");
    }

    @Test
    @WithUserDetails("admin")
    public void test_dropRefusesARequestThatDoesNotStateTheStore() throws Exception {
        final String type = SharedItEnv.uniqueCode("ms-drop-noflag");
        metaStorageService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "k-1", "p1")));

        // no default for a delete: forgetting the parameter must not make Production the outcome
        mockMvc.perform(delete(META_TABLES + type))
                .andExpect(status().isBadRequest());

        assertEquals(List.of("k-1"), metaStorageService.listKeys(ADMIN_COMPANY_ID, type),
                "a refused drop must remove nothing");
    }

    @Test
    @WithUserDetails("admin")
    public void test_aRepeatedDropReportsNothingRemovedRatherThanFailing() throws Exception {
        final String type = SharedItEnv.uniqueCode("ms-drop-twice");
        metaStorageService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "k-1", "p1")));

        mockMvc.perform(delete(META_TABLES + type).param("production", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(1))
                .andExpect(jsonPath("$.hadDescriptor").value(false));

        mockMvc.perform(delete(META_TABLES + type).param("production", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(0))
                .andExpect(jsonPath("$.hadDescriptor").value(false));
    }

    // ---------- download ----------

    @Test
    @WithUserDetails("admin")
    public void test_downloadIsAZipOfTheTableDirectoryWithOneFilePerRecord() throws Exception {
        final String type = SharedItEnv.uniqueCode("ms-dl");

        // PHASE #1: production records, one of them under a key that is not a legal file name; and a
        // synthetic twin with a different body, so reading the wrong store cannot pass
        metaStorageService.upsert(ADMIN_COMPANY_ID, List.of(
                rec(type, "DR-1", "{\"a\":1}"),
                rec(type, "user/7@example.com", "{\"b\":2}\n")));
        metaStorageSyntheticService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "DR-1", "synthetic body")));

        // PHASE #2: download from production
        final MvcResult result = mockMvc.perform(get(META_TABLES + type + "/download").param("production", "true"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(MediaType.APPLICATION_OCTET_STREAM_VALUE, result.getResponse().getContentType(), "PHASE #2: content type");
        final String disposition = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition, "PHASE #2: the zip must be offered under a name");
        // the client takes whatever follows '' as the encoded file name
        assertTrue(disposition.endsWith("''" + type + ".zip"), "PHASE #2: Content-Disposition was: " + disposition);

        // PHASE #3: one directory named after the table, one file per record, bodies verbatim
        final ZipContent zip = readZip(result.getResponse().getContentAsByteArray());
        assertEquals(Set.of(type + "/"), zip.dirs(), "PHASE #3: directories in the zip");
        assertEquals(Map.of(
                type + "/DR-1.json", "{\"a\":1}",
                type + "/user_7_example.com.json", "{\"b\":2}\n"), zip.files(), "PHASE #3: files in the zip");

        // PHASE #4: the temp dir the zip was built in does not outlive the response. CleanerInterceptor
        // hands it to DirUtils.deletePaths, which deletes on a virtual thread - so the removal is
        // awaited, not asserted at the instant the response returns.
        @SuppressWarnings("unchecked")
        final List<Path> toClean = (List<Path>) result.getRequest().getAttribute(Consts.RESOURCES_TO_CLEAN);
        assertNotNull(toClean, "PHASE #4: the temp dir must be handed to CleanerInterceptor");
        assertEquals(1, toClean.size(), "PHASE #4: " + toClean);
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> Files.notExists(toClean.get(0)));
    }

    @Test
    @WithUserDetails("admin")
    public void test_downloadFromTheSyntheticStoreCarriesTheSyntheticBodies() throws Exception {
        final String type = SharedItEnv.uniqueCode("ms-dl-syn");
        metaStorageService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "DR-1", "production body")));
        metaStorageSyntheticService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "DR-1", "synthetic body")));

        final MvcResult result = mockMvc.perform(get(META_TABLES + type + "/download").param("production", "false"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(Map.of(type + "/DR-1.json", "synthetic body"),
                readZip(result.getResponse().getContentAsByteArray()).files());
    }

    @Test
    @WithUserDetails("admin")
    public void test_anAdminCannotDownloadAnotherCompanysTableByNamingItsId() throws Exception {
        final String type = SharedItEnv.uniqueCode("ms-dl-tenant");
        final Long otherCompanyId = SharedItEnv.uniqueLong();
        metaStorageService.upsert(ADMIN_COMPANY_ID, List.of(rec(type, "mine", "my body")));
        metaStorageService.upsert(otherCompanyId, List.of(rec(type, "theirs", "their body")));

        final MvcResult result = mockMvc.perform(get(META_TABLES + type + "/download")
                        .param("production", "true")
                        .param("companyId", String.valueOf(otherCompanyId)))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(Map.of(type + "/mine.json", "my body"),
                readZip(result.getResponse().getContentAsByteArray()).files(),
                "an ADMIN naming another company must get their own company's table");
    }
}
