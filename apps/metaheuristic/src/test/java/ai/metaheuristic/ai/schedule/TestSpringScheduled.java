/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
package ai.metaheuristic.ai.schedule;

import ai.metaheuristic.ai.Globals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.annotation.PostConstruct;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({TestSpringScheduled.ScheduleService1.class, TestSpringScheduled.ScheduleService2.class})
@DirtiesContext
@AutoConfigureCache
@DependsOn({"Globals"})
@Disabled
public class TestSpringScheduled {

    private static int loops = 0;

    @Component
//    @EnableScheduling
    public static class ScheduleService1 {

//        @Scheduled(fixedDelay = 1_000)
        public void fixedDelay_1() {
            System.out.println("fixedDelay_1");
        }
    }

    @Component
//    @EnableScheduling
    public static class ScheduleService2 {

//        @Scheduled(fixedDelay = 1_000)
        public void fixedDelay_2() throws InterruptedException {
            System.out.println("fixedDelay_2");
            Thread.sleep(5_000);
            loops++;
        }
    }

    @Autowired
    public Globals globals;

    @PostConstruct
    public void prepare_1() {
    }

    @BeforeEach
    public void prepare_2() {
    }

    @Test
    public void testSchedule() throws InterruptedException {
//        while(loops<10) {
//            Thread.sleep(1_000);
//        }
    }
}
