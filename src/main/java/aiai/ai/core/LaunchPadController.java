package aiai.ai.core;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * User: Serg
 * Date: 11.06.2017
 * Time: 19:22
 */
@Controller
@RequestMapping("/launchpad")
public class LaunchPadController {


    @GetMapping("/stations")
    public String stations(Map<String, Object> model) {
        model.put("message", "test");
        return "/stations";
    }

    @GetMapping("/datasets")
    public String datasets(Map<String, Object> model) {
        return "/datasets";
    }

    @GetMapping("/experiments")
    public String experiments(Map<String, Object> model) {
        return "/experiments";
    }

}
