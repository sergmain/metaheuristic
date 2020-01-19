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
package ai.metaheuristic.apps.fsm;

import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineModelConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.config.model.StateMachineModelFactory;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.uml.UmlStateMachineModelFactory;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class SimpleFsm implements CommandLineRunner {

    // https://docs.spring.io/spring-statemachine/docs/3.0.0.M2/reference/#statemachine-examples-eventservice

    public static void main(String[] args) {
        SpringApplication.run(SimpleFsm.class, args);
    }

    public enum States { SI, S1, S2 }

    public enum Events { E1, E2 }

    @Configuration
    @EnableStateMachine
    public static class StateMachineConfig
            extends EnumStateMachineConfigurerAdapter<States, Events> {

        @Override
        public void configure(StateMachineConfigurationConfigurer<States, Events> config)
                throws Exception {
            config
                    .withConfiguration()
                    .autoStartup(true)
                    .listener(listener());
        }

        @Override
        public void configure(StateMachineStateConfigurer<States, Events> states)
                throws Exception {
            states
                    .withStates()
                    .initial(States.SI)
                    .states(EnumSet.allOf(States.class));
        }

        @Override
        public void configure(StateMachineTransitionConfigurer<States, Events> transitions)
                throws Exception {
            transitions
                    .withExternal()
                    .source(States.SI).target(States.S1).event(Events.E1)
                    .and()
                    .withExternal()
                    .source(States.S1).target(States.S2).event(Events.E2);
        }

        @Bean
        public StateMachineListener<States, Events> listener() {
            return new StateMachineListenerAdapter<States, Events>() {
                @Override
                public void stateChanged(State<States, Events> from, State<States, Events> to) {
                    System.out.println("State change from "+(from!=null ? from.getId() : "null" )+" to " + to.getId());
                }
            };
        }
    }
/*
    @Configuration
    @EnableStateMachine
    public static class StateMachineConfig_gp extends StateMachineConfigurerAdapter<String, String> {

        @Override
        public void configure(StateMachineModelConfigurer<String, String> config) throws Exception {
            config
                    .withModel()
                    .factory(modelFactory_gp());
        }

        @Bean
        public StateMachineModelFactory<String, String> modelFactory_gp() {
            Resource resource = new ClassPathResource("/fsm/gp.uml");
            UmlStateMachineModelFactory factory = new UmlStateMachineModelFactory(resource);
            return factory;
        }
    }
*/

    @Autowired
    private StateMachine<States, Events> stateMachine;

    @Override
    public void run(String... args) {
        stateMachine
                .sendEvent(Mono.just(MessageBuilder.withPayload(Events.E1).build()))
                .subscribe();
        stateMachine
                .sendEvent(Mono.just(MessageBuilder.withPayload(Events.E2).build()))
                .subscribe();
    }}