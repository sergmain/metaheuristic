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

package aiai.ai.launchpad.flow;

import aiai.ai.launchpad.repositories.FlowInstanceRepository;
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
public class FlowInstanceService implements ApplicationEventPublisherAware {


    private ApplicationEventPublisher publisher;

    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public static class FlowInstanceDeletionEvent extends ApplicationEvent {
        public long flowInstanceId;

        /**
         * Create a new ApplicationEvent.
         *
         * @param source the object on which the event initially occurred (never {@code null})
         */
        public FlowInstanceDeletionEvent(Object source, long flowInstanceId) {
            super(source);
            this.flowInstanceId = flowInstanceId;
        }
    }

    @EqualsAndHashCode(of = "flowInstanceId")
    public static class FlowInstanceDeletionListener implements ApplicationListener<FlowInstanceDeletionEvent> {
        private long flowInstanceId;

        private Consumer<Long> consumer;

        public FlowInstanceDeletionListener(long flowInstanceId, Consumer<Long> consumer) {
            this.flowInstanceId = flowInstanceId;
            this.consumer = consumer;
        }

        @Override
        public void onApplicationEvent( FlowInstanceDeletionEvent event) {
            consumer.accept(event.flowInstanceId);
        }
    }

    private final FlowInstanceRepository flowInstanceRepository;

    public FlowInstanceService(FlowInstanceRepository flowInstanceRepository) {
        this.flowInstanceRepository = flowInstanceRepository;
    }

    public void deleteById(long flowInstanceId) {
        publisher.publishEvent( new FlowInstanceDeletionEvent(this, flowInstanceId) );
        flowInstanceRepository.deleteById(flowInstanceId);
    }
}
