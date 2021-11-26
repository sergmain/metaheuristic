/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.complex;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorUtils;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:47 AM
 */
@SuppressWarnings("FieldCanBeLocal")
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class})
@ActiveProfiles("dispatcher")
@Slf4j
@DirtiesContext
@AutoConfigureCache
public class TestRegisterProcessor {

    private MockMvc mockMvc;

    private String processorIdAsStr = null;
    private Long processorId = null;
    private final Set<Long> processorIds =new HashSet<>();
    private String sessionId = null;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ProcessorRepository processorRepository;

    @BeforeEach
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    public void afterTest() {
        for (Long id : processorIds) {
            try {
                processorRepository.deleteById(id);
            } catch (Throwable th) {
                log.error("Error", th);
            }
        }
    }

    @Test
    @WithUserDetails("data_rest")
    public void testRestPayload_asRest() throws Exception {

        ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm.requests.add(req);

        DispatcherCommParamsYaml ed = requestServer(processorComm);

        assertNotNull(ed);
        assertEquals(1, ed.responses.size());
        DispatcherCommParamsYaml.AssignedProcessorId assignedProcessorId = ed.responses.get(0).getAssignedProcessorId();
        assertNotNull(assignedProcessorId);
        assertNotNull(assignedProcessorId.getAssignedProcessorId());
        assertFalse(assignedProcessorId.getAssignedProcessorId().isBlank());
        assertNotNull(assignedProcessorId.getAssignedSessionId());
        assertFalse(assignedProcessorId.getAssignedSessionId().isBlank());

        processorIdAsStr = assignedProcessorId.getAssignedProcessorId();
        processorId = Long.valueOf(processorIdAsStr);
        processorIds.add(processorId);
        sessionId = assignedProcessorId.getAssignedSessionId();

        processorComm = new ProcessorCommParamsYaml();
        req = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm.requests.add(req);

        // init processorId and sessionId must be first operation. Otherwise, commands won't be inited correctly.
        req.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        req.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false, null);
        req.checkForMissingOutputResources = new ProcessorCommParamsYaml.CheckForMissingOutputResources();

        ed = requestServer(processorComm);

        assignedProcessorId = ed.responses.get(0).getAssignedProcessorId();

        if (assignedProcessorId!=null) {
            // collect for deletion
            processorIds.add(Long.valueOf(assignedProcessorId.getAssignedProcessorId()));
        }
        assertNull(assignedProcessorId);

        Processor s = processorRepository.findById(processorId).orElse(null);
        assertNotNull(s);

        ProcessorStatusYaml ss1 = s.getProcessorStatusYaml();

        final KeepAliveRequestParamYaml.ReportProcessor ss = new KeepAliveRequestParamYaml.ReportProcessor (
                new KeepAliveRequestParamYaml.Env(),
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown, null, null),
                "0:00 - 23:59",
                sessionId,
                System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, false,
                1, EnumsApi.OS.unknown, null);

        ss.currDir = ss1.currDir;
        ss1.schedule = ss.schedule;
        ss1.ip = ss.ip;
        ss1.host = ss.host;

        assertFalse(ProcessorUtils.isProcessorStatusDifferent(ss1, ss), S.f("ss1:\n%s\n\nss:\n%s", ss1, ss));

        //noinspection unused
        int i=0;
    }

    private DispatcherCommParamsYaml requestServer(ProcessorCommParamsYaml data) throws Exception {
        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(data);

        final String url = "/rest/v1/srv-v2/"+ UUID.randomUUID();
        MvcResult result = mockMvc
                .perform(buildPostRequest(processorYaml, url))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        System.out.println(content);

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(content);
        return d;
    }

    private static MockHttpServletRequestBuilder buildPostRequest(String data, String url) {
        return MockMvcRequestBuilders
                .post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(data);
    }

    @Test
    @WithUserDetails("data")
    public void testRestPayload_asUser() throws Exception {
        final ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        processorComm.requests.add(new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE));

        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm);

        final String url = "/rest/v1/srv-v2/"+ UUID.randomUUID();
        mockMvc.perform(buildPostRequest(processorYaml, url))
                .andExpect(status().isForbidden());

    }
}
