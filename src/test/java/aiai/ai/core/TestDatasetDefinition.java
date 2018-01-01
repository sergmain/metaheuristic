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
@ComponentScan("aiai.ai.repositories")
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