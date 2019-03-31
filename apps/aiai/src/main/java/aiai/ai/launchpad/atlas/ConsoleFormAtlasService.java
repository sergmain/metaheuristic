/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.atlas;

import aiai.ai.exceptions.BreakFromForEachException;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.apps.commons.utils.DirUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

@Service
@Slf4j
public class ConsoleFormAtlasService {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    private final TaskRepository taskRepository;

    public ConsoleFormAtlasService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public ConsoleOutputStoredToAtlas collectConsoleOutputs(long flowInstanceId) {

        File tempDir = DirUtils.createTempDir("store-console-");
        if (tempDir == null) {
            return new ConsoleOutputStoredToAtlas("#605.10 Can't create temporary directory");
        }
        try {
            File output = File.createTempFile("output-", ".txt", tempDir);

            try (Stream<Task> stream = taskRepository.findAllByFlowInstanceIdAsStream(flowInstanceId);
                 final OutputStream os = new FileOutputStream(output);
                 final PrintWriter pw = new PrintWriter(os, false, StandardCharsets.UTF_8)
            ) {
                stream.forEach(o -> {
                    ConsoleOutputStoredToAtlas.TaskOutput taskOutput = new ConsoleOutputStoredToAtlas.TaskOutput();
                    taskOutput.taskId = o.getId();
                    taskOutput.console = o.snippetExecResults;
                    try {
                        String json = mapper.writeValueAsString(taskOutput);

                        pw.print(o.getId());
                        pw.print(',');
                        pw.println(json);
                    } catch (IOException e) {
                        throw new BreakFromForEachException(e);
                    }
                });
            }
            return new ConsoleOutputStoredToAtlas(output);
        }
        catch(BreakFromForEachException e) {
            String es = "#605.18 Error while dumping of console outputs " + e.getCause().toString();
            log.error(es);
            return new ConsoleOutputStoredToAtlas(es);
        } catch (IOException e) {
            String es = "#605.14 Error while creating dump of console outputs " + e.toString();
            log.error(es);
            return new ConsoleOutputStoredToAtlas(es);
        }
    }

    public ConsoleOutputStoredToAtlas.TaskOutput fromJson(String json) throws IOException {
        return mapper.readValue(json, ConsoleOutputStoredToAtlas.TaskOutput.class);
    }
}
