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

package ai.metaheuristic.ai.launchpad.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

import java.util.function.Consumer;

/**
 * @author Serge
 * Date: 1/24/2020
 * Time: 1:25 AM
 */
public class LaunchpadInternalEvent {

    @Data
    @AllArgsConstructor
    public static class ExperimentResetEvent {
        public Long workbookId;
    }

    @Data
    @AllArgsConstructor
    public static class SourceCodeLockingEvent {
        public Long sourceCodeId;
        public Long companyUniqueId;
        public boolean lock;
    }

    public static class WorkbookDeletionEvent extends ApplicationEvent {
        public Long workbookId;

        /**
         * Create a new ApplicationEvent.
         *
         * @param source the object on which the event initially occurred (never {@code null})
         */
        public WorkbookDeletionEvent(Object source, Long workbookId) {
            super(source);
            this.workbookId = workbookId;
        }
    }

    @EqualsAndHashCode(of = "workbookId")
    public static class WorkbookDeletionListener implements ApplicationListener<WorkbookDeletionEvent> {
        private long workbookId;

        private Consumer<Long> consumer;

        public WorkbookDeletionListener(long workbookId, Consumer<Long> consumer) {
            this.workbookId = workbookId;
            this.consumer = consumer;
        }

        @Override
        public void onApplicationEvent( WorkbookDeletionEvent event) {
            consumer.accept(event.workbookId);
        }
    }
}
