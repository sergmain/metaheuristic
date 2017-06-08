package aiai.ai.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * User: Serg
 * Date: 08.06.2017
 * Time: 16:45
 */
@Controller
public class IndexController {

/*
    @RequestMapping("/")
    @ResponseBody
    String home() {
        return "Hello World!";
    }
*/

    // inject via application.properties
    @Value("${welcome.message:test}")
    private String message = "Hello World";

    @RequestMapping("/")
    public String welcome(Map<String, Object> model) {
        model.put("message", this.message);
        return "index";
    }
}
