package aiai.ai.station.actors;

import aiai.ai.Globals;
import aiai.ai.station.oauth.OAuthTokenHolder;
import aiai.ai.station.tasks.OAuthTokenTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OAuthTokenRequestorActor extends AbstractTaskQueue<OAuthTokenTask>{

    private final Globals globals;
    private final OAuthTokenHolder oAuthTokenHolder;

    public OAuthTokenRequestorActor(Globals globals, OAuthTokenHolder oAuthTokenHolder) {
        this.globals = globals;
        this.oAuthTokenHolder = oAuthTokenHolder;
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        OAuthTokenTask task;
        while((task = poll())!=null) {
            String token = null;
            
            // ocalhost:8080/oauth/token -d "grant_type=password&scope=read&username=greg&password=turnquist" -u foo:bar
            // ...


            if (token!=null) {
                oAuthTokenHolder.setToken(token);
            }
        }
        if (oAuthTokenHolder.getToken()==null) {
            add(new OAuthTokenTask());
        }
    }
}
