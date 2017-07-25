package aiai.ai.core;

import aiai.ai.launchpad.dataset.DatasetColumn;
import aiai.ai.launchpad.dataset.repo.DatasetColumnRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

/**
 * User: Serg
 * Date: 25.07.2017
 * Time: 17:40
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@EnableTransactionManagement
@ComponentScan("aiai.ai.launchpad.dataset.repo")
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