/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher_events;

import ai.metaheuristic.ai.dispatcher.event.DispatcherApplicationEvent;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author Sergio Lissner
 * Date: 6/7/2022
 * Time: 2:47 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class DispatcherEventServiceTest {

    @Autowired
    public DispatcherEventService dispatcherEventService;

    @Test
    public void test() throws IOException {

        DispatcherEventYaml.BatchEventData batchEventData = new DispatcherEventYaml.BatchEventData();
        batchEventData.filename = "aaa.zip";
        batchEventData.size = 121345L;
        batchEventData.batchId = 42L;
        batchEventData.execContextId = 123L;

        DispatcherApplicationEvent event = new DispatcherApplicationEvent(EnumsApi.DispatcherEventType.BATCH_FILE_UPLOADED, null, null, batchEventData);

        dispatcherEventService.handleAsync(event);

        LocalDate localDate = LocalDate.now();
        int period = localDate.getYear()*100+localDate.getMonthValue();

        List<Integer> list = List.of(period);
        assertDoesNotThrow(()->dispatcherEventService.getEventsForPeriod(list));

    }
}
