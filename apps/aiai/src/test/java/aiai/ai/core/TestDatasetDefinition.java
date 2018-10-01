/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * User: Serg
 * Date: 25.07.2017
 * Time: 17:40
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@EnableTransactionManagement
@ComponentScan("aiai.ai.launchpad.repositories")
public class TestDatasetDefinition {

    // just stub for future tests

/*
    @Autowired
    private DatasetColumnRepository columnRepository;
*/

    @Test
    //@Transactional
    public void whenCreatingUser_thenCreated() {
/*
        DatasetColumn c = new DatasetColumn();
        c = columnRepository.save(c);

        Assert.assertNotNull(columnRepository.findById(c.getId()));
*/
    }
}