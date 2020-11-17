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

package ai.metaheuristic.ai.db;

import ai.metaheuristic.ai.dispatcher.beans.LogData;
import ai.metaheuristic.ai.dispatcher.repositories.LogDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@DirtiesContext
@AutoConfigureCache
public class TestLogDataRepository {

    @Autowired
    private LogDataRepository logDataRepository;

    private LogData logData = null;

    @BeforeEach
    public void before() {
        LogData logDataTemp = logDataRepository.findById(42L).orElse(null);
        assertNull(logDataTemp);

        logData = new LogData();
        logData.setId(42L);
        logData.setVersion(5);
        logData.setLogData("This is log data");
        logData.setType(LogData.Type.ASSEMBLING);
        logData.setRefId(42L);
        logData = logDataRepository.save(logData);
        assertNotNull(logData);

/*
        logData.version = 10;
        logData = logDataRepository.saveAndFlush(logData);
        assertNotNull(logData);
        assertEquals(10, (int)logData.version);
*/
    }

    @AfterEach
    public void after() {
        if (logData!=null) {
            try {
                logDataRepository.deleteById(logData.getId());
            } catch (EmptyResultDataAccessException e) {
                //
            }
        }
    }

/*
    <Table schema="TEST" name="WM_FAQ_IDS" type="TABLE">
        <Field name="sequence_name" dataType="VARCHAR2" javaType="12" javaStringType="java.sql.Types.VARCHAR" size="50" nullable="0"/>
        <Field name="sequence_next_value" dataType="NUMBER" javaType="3" javaStringType="java.sql.Types.DECIMAL" size="10" decimalDigit="0" nullable="0"/>
    </Table>
*/
/*
@TableGenerator(
        name="TABLE_CASH_CURRENCY",
        table="wm_portal_ids",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "wm_cash_currency",
        allocationSize = 1,
        initialValue = 1
)
*/
/*
@Id
@GeneratedValue(strategy= GenerationType.TABLE, generator = "TABLE_CASH_CURRENCY")
*/

    @Test
    public void testLogData(){

        LogData logDataTemp = logDataRepository.findById(-1L).orElse(null);
        assertNull(logDataTemp);


        LogData datasetWithLogs = logDataRepository.findById(logData.getId()).orElse(null);
        assertNotNull(datasetWithLogs);

        logDataRepository.delete(datasetWithLogs);

        LogData newlogData = logDataRepository.findById(datasetWithLogs.getId()).orElse(null);
        assertNull(newlogData);

    }
}
