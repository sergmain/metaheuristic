package aiai.ai.station.oauth;

import lombok.Data;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Data
@Profile("station")
public class OAuthTokenHolder {

    private String token=null;


}
