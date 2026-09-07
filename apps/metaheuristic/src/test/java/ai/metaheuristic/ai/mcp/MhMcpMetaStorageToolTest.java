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

package ai.metaheuristic.ai.mcp;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.MetaStorage;
import ai.metaheuristic.ai.dispatcher.beans.MetaStorageSynthetic;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageData;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageSyntheticService;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageSyntheticRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * mh_get_meta_storage_record on the V3 harness - real Spring context, real H2, real rows in both
 * MH_META_STORAGE and MH_META_STORAGE_SYNTHETIC.
 *
 * <p>The whole content of this tool is a routing decision - which of two identically-shaped tables a
 * row id is looked up in. A double could not express it: a stubbed repository returns whatever the
 * test taught it, so an inverted flag would still 'pass'. Only two real tables holding two real rows
 * with different bodies can fail when the branch is wrong. Hence RULE-NO-MOCKITO.md, and hence the
 * harness.
 *
 * <p>The subject is constructed by hand with nulls for the fifteen collaborators this tool never
 * reaches, and the two real repositories in the positions it does. Those nulls are not doubles -
 * nothing is programmed and nothing is asserted on them; reaching one would be an NPE reported as a
 * tool error, which is itself the wiring failure {@link MhMcpToolWiringTest} pins.
 *
 * <p>❗ {@code companyId} comes from {@link SharedItEnv#uniqueLong()} - never {@code 1L}, which is
 * reserved for the MH management company. Isolation on the shared DB is by unique identifiers.
 *
 * @author Serge
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
public class MhMcpMetaStorageToolTest extends MhSharedItTest {

    private static final String TOOL_NAME = "mh_get_meta_storage_record";
    private static final String SELECT_TOOL = "mh_select_meta_storage_record";
    private static final String DELETE_TOOL = "mh_delete_meta_storage_record";

    /** Map.of takes no varargs past a point and the flag is the only part that varies per call. */
    private static Map<String, Object> withSynthetic(Map<String, Object> key, boolean synthetic) {
        final Map<String, Object> arguments = new java.util.HashMap<>(key);
        arguments.put("synthetic", synthetic);
        return arguments;
    }

    @Autowired private MetaStorageService metaStorageService;
    @Autowired private MetaStorageSyntheticService metaStorageSyntheticService;
    @Autowired private MetaStorageRepository metaStorageRepository;
    @Autowired private MetaStorageSyntheticRepository metaStorageSyntheticRepository;

    private CallToolResult call(Map<String, Object> arguments) {
        return call(TOOL_NAME, arguments);
    }

    private CallToolResult call(String toolName, Map<String, Object> arguments) {
        final MhMcpToolDefinitions definitions = new MhMcpToolDefinitions(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                metaStorageRepository, metaStorageSyntheticRepository, metaStorageService, metaStorageSyntheticService);
        final McpServerFeatures.SyncToolSpecification spec = definitions.getAllToolSpecifications().stream()
                .filter(s -> toolName.equals(s.tool().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(toolName + " is not registered"));
        return spec.callHandler().apply(null, new CallToolRequest(toolName, arguments));
    }

    private static String textOf(CallToolResult result) {
        return result.content().stream()
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining());
    }

    /**
     * The routing itself. Both tables get a row under the same natural key and different bodies, so
     * reading the wrong table cannot accidentally produce the right answer.
     */
    @Test
    public void test_syntheticFlagSelectsTheTable() {

        final Long companyId = SharedItEnv.uniqueLong();
        final String type = "mcp-tool";
        final String recKey = SharedItEnv.uniqueCode("routing") + "@example.com";

        // PHASE #1: one row in each table, same natural key, deliberately different bodies
        metaStorageService.upsert(companyId, List.of(new MetaStorageData.Record(type, recKey, "body-from-plain")));
        metaStorageSyntheticService.upsert(companyId, List.of(new MetaStorageData.Record(type, recKey, "body-from-synthetic")));

        final MetaStorage plain = metaStorageRepository.findByNaturalKey(companyId, type, recKey);
        final MetaStorageSynthetic synth = metaStorageSyntheticRepository.findByNaturalKey(companyId, type, recKey);
        assertNotNull(plain, "PHASE #1: the MH_META_STORAGE row must exist");
        assertNotNull(synth, "PHASE #1: the MH_META_STORAGE_SYNTHETIC row must exist");

        // PHASE #2: synthetic=false reads MH_META_STORAGE
        final CallToolResult fromPlain = call(Map.of("id", plain.id, "synthetic", false));
        assertEquals(Boolean.FALSE, fromPlain.isError(), "PHASE #2: " + textOf(fromPlain));
        final String plainJson = textOf(fromPlain);
        assertTrue(plainJson.contains("\"body\" : \"body-from-plain\""),
                "PHASE #2: synthetic=false must read MH_META_STORAGE, got: " + plainJson);
        assertTrue(plainJson.contains("\"synthetic\" : false"),
                "PHASE #2: the result must report the table it came from: " + plainJson);
        assertTrue(plainJson.contains("\"recKey\" : \"" + recKey + "\""),
                "PHASE #2: recKey of the addressed row: " + plainJson);
        assertTrue(plainJson.contains("\"companyId\" : " + companyId),
                "PHASE #2: companyId of the addressed row: " + plainJson);

        // PHASE #3: synthetic=true reads MH_META_STORAGE_SYNTHETIC. The two tables allocate ids from
        // independent sequences, so this is a different id addressing an equally real row.
        final CallToolResult fromSynthetic = call(Map.of("id", synth.id, "synthetic", true));
        assertEquals(Boolean.FALSE, fromSynthetic.isError(), "PHASE #3: " + textOf(fromSynthetic));
        final String syntheticJson = textOf(fromSynthetic);
        assertTrue(syntheticJson.contains("\"body\" : \"body-from-synthetic\""),
                "PHASE #3: synthetic=true must read MH_META_STORAGE_SYNTHETIC, got: " + syntheticJson);
        assertTrue(syntheticJson.contains("\"synthetic\" : true"),
                "PHASE #3: the result must report the table it came from: " + syntheticJson);

        // PHASE #4: the whole row travels, not just the body - gen and version are what a caller
        // watching for staleness actually reads
        assertTrue(syntheticJson.contains("\"gen\" : " + synth.gen), "PHASE #4: gen: " + syntheticJson);
        assertTrue(syntheticJson.contains("\"version\" : " + synth.version), "PHASE #4: version: " + syntheticJson);
        assertTrue(syntheticJson.contains("\"updatedAt\" : " + synth.updatedAt), "PHASE #4: updatedAt: " + syntheticJson);
    }

    /** An absent id reports which table was searched, so the caller can tell a wrong flag from a wrong id. */
    @Test
    public void test_unknownIdReportsTheTableItSearched() {

        // no @TableGenerator allocation will ever reach this, in either table
        final long absentId = Long.MAX_VALUE;

        final CallToolResult plain = call(Map.of("id", absentId, "synthetic", false));
        assertEquals(Boolean.TRUE, plain.isError(), "PHASE #1: an absent id is an error result");
        assertTrue(textOf(plain).contains("MH_META_STORAGE"), "PHASE #1: names the table: " + textOf(plain));
        assertFalse(textOf(plain).contains("MH_META_STORAGE_SYNTHETIC"),
                "PHASE #1: synthetic=false must not name the synthetic table: " + textOf(plain));

        final CallToolResult synthetic = call(Map.of("id", absentId, "synthetic", true));
        assertEquals(Boolean.TRUE, synthetic.isError(), "PHASE #2: an absent id is an error result");
        assertTrue(textOf(synthetic).contains("MH_META_STORAGE_SYNTHETIC"),
                "PHASE #2: names the table: " + textOf(synthetic));
    }

    /**
     * The natural key is what a caller normally holds, so the select tool must reach the same row the
     * id tool does - and must respect the table flag while doing it.
     */
    @Test
    public void test_selectByNaturalKeyReachesTheSameRowAsTheIdForm() {

        final Long companyId = SharedItEnv.uniqueLong();
        final String type = "mcp-tool";
        final String recKey = SharedItEnv.uniqueCode("natural") + "@example.com";

        // PHASE #1: one row in each table under the SAME natural key, different bodies
        metaStorageService.upsert(companyId, List.of(new MetaStorageData.Record(type, recKey, "plain-by-key")));
        metaStorageSyntheticService.upsert(companyId, List.of(new MetaStorageData.Record(type, recKey, "synthetic-by-key")));

        // PHASE #2: the key selects the table, exactly as the id form does
        final Map<String, Object> key = Map.of("companyId", companyId, "type", type, "recKey", recKey);
        final CallToolResult plain = call(SELECT_TOOL, withSynthetic(key, false));
        assertEquals(Boolean.FALSE, plain.isError(), "PHASE #2: " + textOf(plain));
        assertTrue(textOf(plain).contains("\"body\" : \"plain-by-key\""),
                "PHASE #2: synthetic=false must read MH_META_STORAGE: " + textOf(plain));

        final CallToolResult synth = call(SELECT_TOOL, withSynthetic(key, true));
        assertEquals(Boolean.FALSE, synth.isError(), "PHASE #3: " + textOf(synth));
        assertTrue(textOf(synth).contains("\"body\" : \"synthetic-by-key\""),
                "PHASE #3: synthetic=true must read MH_META_STORAGE_SYNTHETIC: " + textOf(synth));

        // PHASE #4: the row id it reports must be the id the by-id tool answers to - that round trip is
        // the whole reason this tool returns an id at all
        final MetaStorage row = metaStorageRepository.findByNaturalKey(companyId, type, recKey);
        assertNotNull(row, "PHASE #4: the row must exist");
        assertTrue(textOf(plain).contains("\"id\" : " + row.id), "PHASE #4: reported id: " + textOf(plain));
        final CallToolResult byId = call(Map.of("id", row.id, "synthetic", false));
        assertEquals(Boolean.FALSE, byId.isError(), "PHASE #4: " + textOf(byId));
        assertTrue(textOf(byId).contains("\"body\" : \"plain-by-key\""),
                "PHASE #4: the id from the key form addresses the same row: " + textOf(byId));

        // PHASE #5: an absent key is an error naming the table searched, not an empty success
        final CallToolResult absent = call(SELECT_TOOL,
                Map.of("companyId", companyId, "type", type, "recKey", "no-such-key", "synthetic", false));
        assertEquals(Boolean.TRUE, absent.isError(), "PHASE #5: an unmatched key is an error result");
        assertTrue(textOf(absent).contains("MH_META_STORAGE"), "PHASE #5: names the table: " + textOf(absent));
    }

    /**
     * Delete removes the addressed row and nothing else - in particular not its twin in the other
     * table, which carries an identical natural key.
     */
    @Test
    public void test_deleteByNaturalKeyRemovesOnlyTheAddressedRow() {

        final Long companyId = SharedItEnv.uniqueLong();
        final String type = "mcp-tool";
        final String recKey = SharedItEnv.uniqueCode("delete") + "@example.com";
        final Map<String, Object> key = Map.of("companyId", companyId, "type", type, "recKey", recKey);

        // PHASE #1: the same natural key in both tables
        metaStorageService.upsert(companyId, List.of(new MetaStorageData.Record(type, recKey, "plain-doomed")));
        metaStorageSyntheticService.upsert(companyId, List.of(new MetaStorageData.Record(type, recKey, "synthetic-survivor")));
        assertNotNull(metaStorageRepository.findByNaturalKey(companyId, type, recKey), "PHASE #1: plain row exists");
        assertNotNull(metaStorageSyntheticRepository.findByNaturalKey(companyId, type, recKey), "PHASE #1: synthetic row exists");

        // PHASE #2: delete the plain one
        final CallToolResult deleted = call(DELETE_TOOL, withSynthetic(key, false));
        assertEquals(Boolean.FALSE, deleted.isError(), "PHASE #2: " + textOf(deleted));
        assertTrue(textOf(deleted).contains("\"ok\" : true"), "PHASE #2: " + textOf(deleted));

        // PHASE #3: it is gone from the table addressed, and ONLY from that table. The twin sharing the
        // key is what would fail here if the flag were ignored on the write path.
        assertNull(metaStorageRepository.findByNaturalKey(companyId, type, recKey),
                "PHASE #3: the addressed row must be gone");
        assertNotNull(metaStorageSyntheticRepository.findByNaturalKey(companyId, type, recKey),
                "PHASE #3: the synthetic twin must survive a delete aimed at the plain table");

        // PHASE #4: a repeated delete matches nothing and reports it, rather than failing - the same
        // idempotency the natural key gives upsert
        final CallToolResult again = call(DELETE_TOOL, withSynthetic(key, false));
        assertEquals(Boolean.FALSE, again.isError(), "PHASE #4: a no-op delete is not a transport error");
        assertTrue(textOf(again).contains("\"ok\" : false"), "PHASE #4: " + textOf(again));
        assertTrue(textOf(again).contains("nothing was deleted"), "PHASE #4: " + textOf(again));

        // PHASE #5: and the survivor can still be deleted through its own flag
        final CallToolResult synth = call(DELETE_TOOL, withSynthetic(key, true));
        assertEquals(Boolean.FALSE, synth.isError(), "PHASE #5: " + textOf(synth));
        assertNull(metaStorageSyntheticRepository.findByNaturalKey(companyId, type, recKey),
                "PHASE #5: the synthetic row is gone once addressed with synthetic=true");
    }

    /**
     * 'synthetic' has no default. Defaulting it would make a missing flag silently mean "the plain
     * table", and the caller would get a real record from a place it never asked for.
     */
    @Test
    public void test_syntheticIsRequiredAndMustBeABoolean() {

        final Long companyId = SharedItEnv.uniqueLong();
        final String recKey = SharedItEnv.uniqueCode("required") + "@example.com";
        metaStorageService.upsert(companyId, List.of(new MetaStorageData.Record("mcp-tool", recKey, "body")));
        final MetaStorage row = metaStorageRepository.findByNaturalKey(companyId, "mcp-tool", recKey);
        assertNotNull(row, "PHASE #1: the row must exist");

        final CallToolResult missing = call(Map.of("id", row.id));
        assertEquals(Boolean.TRUE, missing.isError(), "PHASE #2: a missing 'synthetic' is an error");
        assertTrue(textOf(missing).contains("Required parameter 'synthetic' is missing"),
                "PHASE #2: " + textOf(missing));

        // PHASE #3: an unrecognized value is rejected rather than read as false - Boolean.parseBoolean
        // would have turned this typo into a successful read of the wrong table
        final CallToolResult typo = call(Map.of("id", row.id, "synthetic", "yes"));
        assertEquals(Boolean.TRUE, typo.isError(), "PHASE #3: 'yes' is not a boolean");
        assertTrue(textOf(typo).contains("must be a boolean"), "PHASE #3: " + textOf(typo));

        // PHASE #4: the string forms a JSON-RPC client may send still work
        final CallToolResult asString = call(Map.of("id", row.id, "synthetic", "false"));
        assertEquals(Boolean.FALSE, asString.isError(), "PHASE #4: " + textOf(asString));
        assertTrue(textOf(asString).contains("\"body\" : \"body\""), "PHASE #4: " + textOf(asString));
    }
}
