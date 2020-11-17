/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
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

import java.util.ArrayList;
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
    private Set<Long> processorIds =new HashSet<>();
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
        DispatcherCommParamsYaml ed = requestServer(processorComm);

        assertNotNull(ed.getAssignedProcessorId());
        assertNotNull(ed.getAssignedProcessorId().getAssignedProcessorId());
        assertFalse(ed.getAssignedProcessorId().getAssignedProcessorId().isBlank());
        assertNotNull(ed.getAssignedProcessorId().getAssignedSessionId());
        assertFalse(ed.getAssignedProcessorId().getAssignedSessionId().isBlank());

        processorIdAsStr = ed.getAssignedProcessorId().getAssignedProcessorId();
        processorId = Long.valueOf(processorIdAsStr);
        processorIds.add(processorId);
        sessionId = ed.getAssignedProcessorId().getAssignedSessionId();

        processorComm = new ProcessorCommParamsYaml();
        // init processorId and sessionId must be first operation. Otherwise, commands won't be inited correctly.
        processorComm.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);

//        processorComm.reportProcessorTaskStatus = new ProcessorCommParamsYaml.ReportProcessorTaskStatus();

        final ProcessorCommParamsYaml.ReportProcessorStatus ss = new ProcessorCommParamsYaml.ReportProcessorStatus(
                new EnvYaml(),
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.installed, "Git 1.0.0", null),
                "0:00 - 23:59",
                sessionId,
                System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, true,
                1, EnumsApi.OS.unknown);

        processorComm.reportProcessorStatus = ss;

        processorComm.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false);
        processorComm.checkForMissingOutputResources = new ProcessorCommParamsYaml.CheckForMissingOutputResources();

        ed = requestServer(processorComm);
        if (ed.getAssignedProcessorId()!=null) {
            // collect for deletion
            processorIds.add(Long.valueOf(ed.getAssignedProcessorId().getAssignedProcessorId()));
        }
        assertNull(ed.getAssignedProcessorId());

        Processor s = processorRepository.findById(processorId).orElse(null);
        assertNotNull(s);

        ProcessorStatusYaml ss1 = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(s.status);
        assertFalse(ProcessorTransactionService.isProcessorStatusDifferent(ss1, ss));

        //noinspection unused
        int i=0;
    }

    public DispatcherCommParamsYaml requestServer(ProcessorCommParamsYaml data) throws Exception {
        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(data);

        final String url = "/rest/v1/srv-v2/"+ UUID.randomUUID().toString();
        MvcResult result = mockMvc
                .perform(buildPostRequest(processorYaml, url))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        System.out.println(content);

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(content);
        return d;
    }

    public MockHttpServletRequestBuilder buildPostRequest(String data, String url) {
        return MockMvcRequestBuilders
                .post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(data);
    }

    @Test
    @WithUserDetails("data")
    public void testRestPayload_asUser() throws Exception {
        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(new ProcessorCommParamsYaml());

        final String url = "/rest/v1/srv-v2/"+ UUID.randomUUID().toString();
        mockMvc.perform(buildPostRequest(processorYaml, url))
                .andExpect(status().isForbidden());

    }
}
