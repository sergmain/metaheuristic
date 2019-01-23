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
package aiai.ai.schedule;

import aiai.ai.Globals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.PostConstruct;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@Import({TestSpringScheduled.ScheduleService1.class, TestSpringScheduled.ScheduleService2.class})
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
    public void preapre_1() {
    }

    @Before
    public void prepare_2() {
    }

    @Test
    public void testSchedule() throws InterruptedException {
//        while(loops<10) {
//            Thread.sleep(1_000);
//        }
    }
}
