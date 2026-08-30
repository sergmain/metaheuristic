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

package ai.metaheuristic.meta_storage_rdbms;

import ai.metaheuristic.meta_storage_rdbms.data.MetaRecordParams;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * MCP tool definitions for the meta storage - the two SPI methods, exposed over MCP.
 *
 * <p>Tools are built explicitly via the low-level MCP Java SDK, matching
 * {@code MhMcpToolDefinitions} in the dispatcher. This class supplies specifications only; the host
 * application registers them with its own transport.
 *
 * <p>2 tools:
 * <pre>
 *   mh_meta_storage_fetch  - read records of one type, all of them or a named subset
 *   mh_meta_storage_upsert - insert-or-update records on their natural key
 * </pre>
 *
 * <p>Error code prefix: {@code 01.941.} (unique to this class).
 *
 * @author Serge
 */
@Slf4j
@RequiredArgsConstructor
public class MetaStorageMcpToolDefinitions {

    private final MetaStorageSpi metaStorageSpi;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== DTOs ====================

    public record FetchResultDto(
            String bucket,
            String type,
            int count,
            List<MetaRecordParams> records
    ) {}

    public record UpsertResultDto(
            String bucket,
            int requested,
            int rows
    ) {}

    /**
     * Transport-boundary guard - applied to EVERY tool spec in {@link #getAllToolSpecifications()}.
     *
     * <p>The MCP SDK builds the JSON-RPC error frame straight from a thrown exception's
     * {@code getMessage()} and asserts it non-null. An exception whose message is null - any NPE,
     * {@code IllegalStateException()} - therefore aborts the whole response stream and the caller
     * learns neither which tool failed nor why. Per MCP semantics a tool failure is a
     * {@code CallToolResult} with {@code isError=true}, not a protocol error.
     */
    static McpServerFeatures.SyncToolSpecification transportGuarded(McpServerFeatures.SyncToolSpecification spec) {
        final var delegate = spec.callHandler();
        final String toolName = spec.tool().name();
        return new McpServerFeatures.SyncToolSpecification(spec.tool(), (exchange, request) -> {
            try {
                return delegate.apply(exchange, request);
            }
            catch (Throwable th) {
                log.error("01.941.270 tool '{}' failed, returning it as an isError CallToolResult", toolName, th);
                final String msg = th.getMessage() == null ? th.toString() : th.getMessage();
                return CallToolResult.builder()
                        .addTextContent("01.941.280 ERROR in '" + toolName + "': " + msg)
                        .isError(true)
                        .build();
            }
        });
    }

    // ==================== Build all tool specifications ====================

    public List<McpServerFeatures.SyncToolSpecification> getAllToolSpecifications() {
        return Stream.of(
                new McpServerFeatures.SyncToolSpecification(FETCH_TOOL, this::handleFetch),
                new McpServerFeatures.SyncToolSpecification(UPSERT_TOOL, this::handleUpsert)
        ).map(MetaStorageMcpToolDefinitions::transportGuarded).toList();
    }

    // ==================== Tool 1: fetch ====================

    private static final Tool FETCH_TOOL = Tool.builder("mh_meta_storage_fetch",
                    objectSchema(
                            Map.of("bucket", Map.of("type", "string", "description", "Tenant/namespace. Opaque to the store."),
                                    "type", Map.of("type", "string", "description",
                                            "Entity kind, e.g. 'contact'. A free string - a new kind needs no DDL."),
                                    "recKeys", Map.of("type", "array", "items", Map.of("type", "string"),
                                            "description", "Natural keys to fetch. Omit or leave empty for every record of that type.")),
                            List.of("bucket", "type")))
            .title("Meta Storage Fetch")
            .description("Read records of one type out of one bucket. With recKeys omitted it returns every record "
                    + "of that type, which is the selection step feeding a batch splitter. With recKeys supplied it "
                    + "returns exactly those records, which is the per-batch payload fetch. Returns the version-less "
                    + "record shape - rows stored under an older version are upgraded on the way out.")
            .build();

    private CallToolResult handleFetch(McpSyncServerExchange exchange, CallToolRequest request) {
        final Map<String, Object> arguments = request.arguments();
        final String bucket = getRequiredString(arguments, "bucket");
        final String type = getRequiredString(arguments, "type");
        final List<String> recKeys = getOptionalStringList(arguments, "recKeys");
        log.info("01.941.020 MCP fetch(bucket={}, type={}, recKeys={})", bucket, type,
                recKeys == null ? "<all>" : recKeys.size());

        final List<MetaRecordParams> records = metaStorageSpi.fetch(bucket, type, recKeys);
        return toCallToolResult(new FetchResultDto(bucket, type, records.size(), records));
    }

    // ==================== Tool 2: upsert ====================

    private static final Tool UPSERT_TOOL = Tool.builder("mh_meta_storage_upsert",
                    objectSchema(
                            Map.of("bucket", Map.of("type", "string", "description", "Tenant/namespace. Opaque to the store."),
                                    "records", Map.of("type", "array",
                                            "items", Map.of("type", "object",
                                                    "properties", Map.of(
                                                            "type", Map.of("type", "string", "description", "Entity kind."),
                                                            "recKey", Map.of("type", "string", "description", "Natural key, unique within (bucket, type)."),
                                                            "name", Map.of("type", "string"),
                                                            "secondName", Map.of("type", "string"),
                                                            "email", Map.of("type", "string")),
                                                    "required", List.of("type", "recKey")),
                                            "description", "Records to write.")),
                            List.of("bucket", "records")))
            .title("Meta Storage Upsert")
            .description("Insert-or-update records on the natural key (bucket, type, recKey). Replaying the same "
                    + "records overwrites the rows written the first time rather than appending duplicates, which is "
                    + "what makes a retried task safe. Returns the number of rows written.")
            .build();

    private CallToolResult handleUpsert(McpSyncServerExchange exchange, CallToolRequest request) {
        final Map<String, Object> arguments = request.arguments();
        final String bucket = getRequiredString(arguments, "bucket");
        final Object rawRecords = arguments.get("records");
        if (!(rawRecords instanceof List<?> list)) {
            return errorResult("01.941.040 Required parameter 'records' is missing or is not an array");
        }
        final List<MetaRecordParams> records = new ArrayList<>(list.size());
        for (Object o : list) {
            records.add(objectMapper.convertValue(o, MetaRecordParams.class));
        }
        log.info("01.941.060 MCP upsert(bucket={}, records={})", bucket, records.size());

        final int rows = metaStorageSpi.upsert(bucket, records);
        return toCallToolResult(new UpsertResultDto(bucket, records.size(), rows));
    }

    // ==================== Utility methods ====================

    private static String getRequiredString(Map<String, Object> arguments, String key) {
        final Object value = arguments.get(key);
        if (value == null) {
            throw new IllegalArgumentException("01.941.080 Required parameter '" + key + "' is missing");
        }
        return value.toString();
    }

    @Nullable
    private static List<String> getOptionalStringList(Map<String, Object> arguments, String key) {
        final Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        throw new IllegalArgumentException("01.941.100 Parameter '" + key + "' must be an array of strings");
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        final Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private CallToolResult toCallToolResult(Object result) {
        try {
            final String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            return CallToolResult.builder()
                    .addTextContent(json)
                    .isError(false)
                    .build();
        }
        catch (JacksonException e) {
            log.error("01.941.240 Error serializing tool result", e);
            return CallToolResult.builder()
                    .addTextContent("01.941.260 Error: " + e.getMessage())
                    .isError(true)
                    .build();
        }
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .build();
    }
}
