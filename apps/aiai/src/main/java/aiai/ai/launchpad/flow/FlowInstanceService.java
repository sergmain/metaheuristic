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
