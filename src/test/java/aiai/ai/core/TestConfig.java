package aiai.ai.core;

import lombok.Getter;
import lombok.Value;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import java.util.Collection;

/**
 * User: Serg
 * Date: 15.06.2017
 * Time: 15:59
 */
public class TestConfig {


    @DomainEvents
    Collection<Object> domainEvents() {
        return null;
    }

    @AfterDomainEventPublication
    void callbackMethod() {
        // … potentially clean up domain events list
    }

    @Value(staticConstructor = "of")
    static class OneEvent {
        @Getter(onMethod = @__(@DomainEvents)) Object event;
    }
}
