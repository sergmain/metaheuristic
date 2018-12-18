package aiai.ai;

import aiai.ai.station.LaunchpadLookupExtendedService;
import org.omg.CORBA.BooleanHolder;

import java.util.HashMap;
import java.util.Map;

public class RoundRobinForLaunchpad {

    public Map<String, BooleanHolder> urls = new HashMap<>();

    public RoundRobinForLaunchpad(Map<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> launchpads) {
        for (Map.Entry<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> entry : launchpads.entrySet()) {
            this.urls.putIfAbsent(entry.getValue().launchpadLookup.url, new BooleanHolder(true));
        }
    }

    public String next() {
        String url = findNext();
        if (url != null) {
            return url;
        }
        reset();
        url = findNext();
        if (url != null) {
            return url;
        }
        throw new IllegalStateException("Can't produce next url");
    }

    public void reset() {
        for (Map.Entry<String, BooleanHolder> entry : urls.entrySet()) {
            entry.getValue().value = true;
        }
    }

    private String findNext() {
        String url = null;
        for (Map.Entry<String, BooleanHolder> entry : urls.entrySet()) {
            if (entry.getValue().value) {
                entry.getValue().value = false;
                url = entry.getKey();
                break;
            }
        }
        return url;
    }
}
