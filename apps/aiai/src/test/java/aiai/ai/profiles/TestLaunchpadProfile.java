package aiai.ai.profiles;

import aiai.ai.Globals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
@TestPropertySource(locations="classpath:test-launchpad-profile.properties")
public class TestLaunchpadProfile {

    @Autowired
    private Globals globals;

    @Test
    public void simpleTest() {
        System.out.println("We don't need any test here " +
                "because this test is about " +
                "correctness of class wiring for profile." +
                "Number of threads: " + globals.threadNumber);
    }
}
