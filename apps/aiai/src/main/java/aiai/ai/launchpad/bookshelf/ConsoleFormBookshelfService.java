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

package aiai.ai.launchpad.bookshelf;

import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.repositories.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

@Service
public class ConsoleFormBookshelfService {

    private final TaskRepository taskRepository;

    public ConsoleFormBookshelfService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @SuppressWarnings("Duplicates")
    @Transactional
    public ConsoleOutputStoredToBookshelf collectConsoleOutputs(long flowInstanceId) {

        try (Stream<Task> stream = taskRepository.findAllByFlowInstanceIdAsStream(flowInstanceId) ) {
            stream.forEach(o -> {
                        ConsoleOutputStoredToBookshelf.TaskOutput taskOutput = new ConsoleOutputStoredToBookshelf.TaskOutput();
                        taskOutput.taskId = o.getId();
                        taskOutput.console = o.snippetExecResults;
                    });
        }
        return null;
    }
}
