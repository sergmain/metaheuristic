package aiai.ai.launchpad;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:39
 */
@Controller
@RequestMapping("/launchpad")
public class LaunchpadIndexController {

    @GetMapping("/index")
    public String index2(Map<String, Object> model) {
        return "/launchpad/index";
    }

}
