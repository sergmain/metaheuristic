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

package aiai.ai.launchpad.plan;

import aiai.ai.launchpad.repositories.WorkbookRepository;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Profile("launchpad")
public class WorkbookService implements ApplicationEventPublisherAware {


    private ApplicationEventPublisher publisher;

    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public static class WorkbookDeletionEvent extends ApplicationEvent {
        public long workbookId;

        /**
         * Create a new ApplicationEvent.
         *
         * @param source the object on which the event initially occurred (never {@code null})
         */
        public WorkbookDeletionEvent(Object source, long workbookId) {
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

    private final WorkbookRepository workbookRepository;

    public WorkbookService(WorkbookRepository workbookRepository) {
        this.workbookRepository = workbookRepository;
    }

    public void deleteById(long workbookId) {
        publisher.publishEvent( new WorkbookDeletionEvent(this, workbookId) );
        workbookRepository.deleteById(workbookId);
    }
}
