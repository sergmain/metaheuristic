/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.atlas;

import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.commons.utils.DirUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

@Service
@Slf4j
@Profile("dispatcher")
public class ConsoleFormAtlasService {

    // TODO 2019-10-02 if at 2019-11-02 there isn't any case for using this code, it has to be deleted

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
    public ConsoleOutputStoredToAtlas collectConsoleOutputs(Long execContextId) {

        File tempDir = DirUtils.createTempDir("store-console-");
        if (tempDir == null) {
            return new ConsoleOutputStoredToAtlas("#605.10 Can't create temporary directory");
        }
        try {
            File output = File.createTempFile("output-", ".txt", tempDir);

            try (Stream<Task> stream = taskRepository.findAllByExecContextIdAsStream(execContextId);
                 final OutputStream os = new FileOutputStream(output);
                 final PrintWriter pw = new PrintWriter(os, false, StandardCharsets.UTF_8)
            ) {
                stream.forEach(o -> {
                    ConsoleOutputStoredToAtlas.TaskOutput taskOutput = new ConsoleOutputStoredToAtlas.TaskOutput();
                    taskOutput.taskId = o.getId();
                    taskOutput.console = o.getFunctionExecResults();
                    try {
                        String json = mapper.writeValueAsString(taskOutput);

                        pw.print(o.getId());
                        pw.print(',');
                        pw.println(json);
                    } catch (IOException e) {
                        throw new BreakFromLambdaException(e);
                    }
                });
            }
            return new ConsoleOutputStoredToAtlas(output);
        }
        catch(BreakFromLambdaException e) {
            String es = "#605.18 Error while dumping of console outputs " + e.getCause().toString();
            log.error(es);
            return new ConsoleOutputStoredToAtlas(es);
        } catch (IOException e) {
            String es = "#605.14 Error while creating dump of console outputs " + e.toString();
            log.error(es);
            return new ConsoleOutputStoredToAtlas(es);
        }
    }

    ConsoleOutputStoredToAtlas.TaskOutput fromJson(String json) throws IOException {
        return mapper.readValue(json, ConsoleOutputStoredToAtlas.TaskOutput.class);
    }
}
