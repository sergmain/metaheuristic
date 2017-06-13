package aiai.ai.station;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 14:04
 */
@Controller
@RequestMapping("/station")
public class StationIndexController {

    @GetMapping("/index")
    public String index(Map<String, Object> model) {
        return "/station/index";
    }

}
