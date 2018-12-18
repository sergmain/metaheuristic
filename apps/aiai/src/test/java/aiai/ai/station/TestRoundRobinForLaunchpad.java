package aiai.ai.station;

import aiai.ai.RoundRobinForLaunchpad;
import aiai.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestRoundRobinForLaunchpad {

    @Test
    public void test() {

        LinkedHashMap<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> lookupExtendedMap = new LinkedHashMap<>();

        LaunchpadLookupExtendedService.LaunchpadLookupExtended lle1 = new LaunchpadLookupExtendedService.LaunchpadLookupExtended();
        lle1.launchpadLookup = new LaunchpadLookupConfig.LaunchpadLookup();
        lle1.launchpadLookup.url = "url1";
        lookupExtendedMap.put( "url1", lle1);

        LaunchpadLookupExtendedService.LaunchpadLookupExtended lle2 = new LaunchpadLookupExtendedService.LaunchpadLookupExtended();
        lle2.launchpadLookup = new LaunchpadLookupConfig.LaunchpadLookup();
        lle2.launchpadLookup.url = "url2";
        lookupExtendedMap.put( "url2", lle2);

        RoundRobinForLaunchpad rr = new RoundRobinForLaunchpad(lookupExtendedMap);

        String url = rr.next();
        assertEquals("url1", url);

        rr.reset();

        url = rr.next();
        assertEquals("url1", url);

        url = rr.next();
        assertEquals("url2", url);

        url = rr.next();
        assertEquals("url1", url);


    }
}
