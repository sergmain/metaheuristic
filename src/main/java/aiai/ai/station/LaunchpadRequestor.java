package aiai.ai.station;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 16:25
 */
@Service
@EnableScheduling
public class LaunchpadRequestor {


    /**
     * This example uses fixedRate, which specifies the interval between method invocations measured from the start time of each invocation.
     * There are other options, like fixedDelay, which specifies the interval between invocations measured from the completion of the task
     */


/*
    @Scheduled(fixedDelayString  = "#{ new Integer(environment.getProperty('aiai.station.request.launchpad.timeout')) > 10 ? new Integer(environment.getProperty('aiai.station.request.launchpad.timeout'))*1000 : 10000 }")
    public void fixedDelayTask() {
        System.out.println(new Date() + " This runs in a fixed delay ");
    }
*/

/*
    @Scheduled(fixedRate = 6000)
    public void fixedRateTask() {
        System.out.println(new Date() + " This runs in a fixed rate");
    }
*/

}

