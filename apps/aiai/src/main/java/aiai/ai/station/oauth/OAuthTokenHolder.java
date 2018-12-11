package aiai.ai.station.oauth;

import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class OAuthTokenHolder {

    private String token=null;


}
