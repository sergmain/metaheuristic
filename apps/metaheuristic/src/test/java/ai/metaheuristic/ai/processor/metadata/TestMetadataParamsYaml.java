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

package ai.metaheuristic.ai.processor.metadata;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.yaml.metadata.*;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 12/29/2020
 * Time: 12:59 AM
 */
public class TestMetadataParamsYaml {

    @Test
    public void testVersion1() throws IOException {
        String s = IOUtils.resourceToString("/metadata/metadata-v1.yaml", StandardCharsets.UTF_8);
        assertFalse(S.b(s));

        MetadataParamsYamlUtilsV1 utils = (MetadataParamsYamlUtilsV1)MetadataParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1);
        assertNotNull(utils);
        MetadataParamsYamlV1 metadata = utils.to(s);
        assertNotNull(metadata);
        assertNotNull(metadata.dispatcher);
        assertFalse(metadata.dispatcher.isEmpty());
        assertTrue(metadata.dispatcher.containsKey("http://localhost:8080"));
        assertTrue(metadata.dispatcher.containsKey("https://localhost:8888"));
        MetadataParamsYamlV1.DispatcherInfoV1 dispatcher8080 = metadata.dispatcher.get("http://localhost:8080");
        assertNotNull(dispatcher8080);
        assertEquals("localhost-8080", dispatcher8080.code);
        assertEquals("209", dispatcher8080.processorId);
        assertEquals("sessionId-11", dispatcher8080.sessionId);

        MetadataParamsYamlV1.DispatcherInfoV1 dispatcher8888 = metadata.dispatcher.get("https://localhost:8888");
        assertNotNull(dispatcher8888);
        assertEquals("localhost-8888", dispatcher8888.code);
        assertEquals("42", dispatcher8888.processorId);
        assertEquals("sessionId-12", dispatcher8888.sessionId);

        assertNotNull(metadata.metadata);
        assertFalse(metadata.metadata.isEmpty());
        assertTrue(metadata.metadata.containsKey(Consts.META_FUNCTION_DOWNLOAD_STATUS));
        String statusYaml = metadata.metadata.get(Consts.META_FUNCTION_DOWNLOAD_STATUS);
        assertFalse(S.b(statusYaml));

        FunctionDownloadStatusYaml fdsy = FunctionDownloadStatusYamlUtils.BASE_YAML_UTILS.to(statusYaml);

        assertNotNull(fdsy.statuses);
        assertEquals(5, fdsy.statuses.size());

        FunctionDownloadStatusYaml.Status status;
        {
            status = fdsy.statuses.get(0);
            assertEquals("test.function:1.0", status.code);
            assertEquals("http://localhost:8080", status.dispatcherUrl);
            assertEquals(EnumsApi.FunctionState.signature_wrong, status.functionState);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, status.sourcing);
            assertTrue(status.verified);
        }
        {
            status = fdsy.statuses.get(1);
            assertEquals("test.function:1.0", status.code);
            assertEquals("https://localhost:8888", status.dispatcherUrl);
            assertEquals(EnumsApi.FunctionState.signature_wrong, status.functionState);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, status.sourcing);
            assertFalse(status.verified);
        }
        {
            status = fdsy.statuses.get(2);
            assertEquals("function-01:1.1", status.code);
            assertEquals("http://localhost:8080", status.dispatcherUrl);
            assertEquals(EnumsApi.FunctionState.not_found, status.functionState);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, status.sourcing);
            assertFalse(status.verified);
        }
        {
            status = fdsy.statuses.get(3);
            assertEquals("function-02:1.1", status.code);
            assertEquals("http://localhost:8080", status.dispatcherUrl);
            assertEquals(EnumsApi.FunctionState.not_found, status.functionState);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, status.sourcing);
            assertFalse(status.verified);
        }
        {
            status = fdsy.statuses.get(4);
            assertEquals("fileless-function:1.0", status.code);
            assertEquals("https://localhost:8888", status.dispatcherUrl);
            assertEquals(EnumsApi.FunctionState.not_found, status.functionState);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, status.sourcing);
            assertFalse(status.verified);
        }
    }

    @Test
    public void testVersion3() throws IOException {
        String s = IOUtils.resourceToString("/metadata/metadata-v3.yaml", StandardCharsets.UTF_8);
        assertFalse(S.b(s));

        final AbstractParamsYamlUtils forVersion = MetadataParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
        assertNotNull(forVersion);
        MetadataParamsYamlV3 metadata = (MetadataParamsYamlV3) forVersion.to(s);
        assertNotNull(metadata);
        assertNotNull(metadata.processorSessions);
        assertFalse(metadata.processorSessions.isEmpty());
        assertEquals(2, metadata.processorSessions.size());
        assertTrue(metadata.processorSessions.containsKey("http://localhost:8080"));
        MetadataParamsYamlV3.ProcessorSessionV3 dispatcher8080 = metadata.processorSessions.get("http://localhost:8080");
        assertNotNull(dispatcher8080);
        assertEquals("localhost-8080", dispatcher8080.dispatcherCode);
        assertEquals(209, dispatcher8080.processorId);
        assertEquals("sessionId-11", dispatcher8080.sessionId);

        MetadataParamsYamlV3.ProcessorSessionV3 dispatcher8888 = metadata.processorSessions.get("https://localhost:8888");
        assertNotNull(dispatcher8888);
        assertEquals("localhost-8888", dispatcher8888.dispatcherCode);
        assertEquals(42, dispatcher8888.processorId);
        assertEquals("sessionId-12", dispatcher8888.sessionId);
        assertEquals(2, dispatcher8888.cores.size());
        assertTrue(dispatcher8888.cores.containsKey("core-1"));
        assertTrue(dispatcher8888.cores.containsKey("core-2"));
        assertEquals(117, dispatcher8888.cores.get("core-1"));
        assertEquals(119, dispatcher8888.cores.get("core-2"));

        assertNull(metadata.metadata);

        List<MetadataParamsYamlV3.FunctionV3> statuses = metadata.functions;
        assertNotNull(statuses);
        assertEquals(5, statuses.size());

        MetadataParamsYamlV3.FunctionV3 status;
        {
            status = statuses.get(0);
            assertEquals("test.function:1.0", status.code);
            assertEquals("http://localhost:8080", status.assetManagerUrl);
            assertEquals(EnumsApi.FunctionState.signature_wrong, status.state);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, status.sourcing);
        }
        {
            status = statuses.get(1);
            assertEquals("test.function:1.0", status.code);
            assertEquals("https://localhost:8888", status.assetManagerUrl);
            assertEquals(EnumsApi.FunctionState.signature_wrong, status.state);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, status.sourcing);
        }
        {
            status = statuses.get(2);
            assertEquals("function-01:1.1", status.code);
            assertEquals("http://localhost:8080", status.assetManagerUrl);
            assertEquals(EnumsApi.FunctionState.not_found, status.state);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, status.sourcing);
        }
        {
            status = statuses.get(3);
            assertEquals("function-02:1.1", status.code);
            assertEquals("http://localhost:8080", status.assetManagerUrl);
            assertEquals(EnumsApi.FunctionState.not_found, status.state);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, status.sourcing);
        }
        {
            status = statuses.get(4);
            assertEquals("fileless-function:1.0", status.code);
            assertEquals("https://localhost:8888", status.assetManagerUrl);
            assertEquals(EnumsApi.FunctionState.not_found, status.state);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, status.sourcing);
        }
    }

    @Test
    public void test_upgrade_to_Version4() throws IOException {
        String s = IOUtils.resourceToString("/metadata/metadata-v3.yaml", StandardCharsets.UTF_8);
        assertFalse(S.b(s));

        final AbstractParamsYamlUtils forVersion3 = MetadataParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
        assertNotNull(forVersion3);
        MetadataParamsYamlUtilsV3 v3 = (MetadataParamsYamlUtilsV3)forVersion3;
        MetadataParamsYamlV3 pV3 = v3.to(s);

        var pV4 = v3.upgradeTo(pV3);

        assertNotNull(pV4);
        assertNotNull(pV4.processorSessions);
        assertFalse(pV4.processorSessions.isEmpty());
        assertEquals(2, pV4.processorSessions.size());
        assertTrue(pV4.processorSessions.containsKey("http://localhost:8080"));
        MetadataParamsYamlV4.ProcessorSessionV4 dispatcher8080 = pV4.processorSessions.get("http://localhost:8080");
        assertNotNull(dispatcher8080);
        assertEquals("localhost-8080", dispatcher8080.dispatcherCode);
        assertEquals(209, dispatcher8080.processorId);
        assertEquals("sessionId-11", dispatcher8080.sessionId);

        MetadataParamsYamlV4.ProcessorSessionV4 dispatcher8888 = pV4.processorSessions.get("https://localhost:8888");
        assertNotNull(dispatcher8888);
        assertEquals("localhost-8888", dispatcher8888.dispatcherCode);
        assertEquals(42, dispatcher8888.processorId);
        assertEquals("sessionId-12", dispatcher8888.sessionId);
        assertEquals(2, dispatcher8888.cores.size());
        assertTrue(dispatcher8888.cores.containsKey("core-1"));
        assertTrue(dispatcher8888.cores.containsKey("core-2"));
        assertEquals(117, dispatcher8888.cores.get("core-1"));
        assertEquals(119, dispatcher8888.cores.get("core-2"));
    }
}
