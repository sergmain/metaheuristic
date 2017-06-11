package aiai.ai.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * User: Serg
 * Date: 11.06.2017
 * Time: 15:17
 */
@Controller
public class DefaultController {

    // inject via application.properties
    @Value("${welcome.message:test}")
    private String message = "Hello World";

    @RequestMapping("/")
    public String index1(Map<String, Object> model) {
        return toIndex(model);
    }

    @GetMapping("/index")
    public String index2(Map<String, Object> model) {
        return toIndex(model);
    }

    private String toIndex(Map<String, Object> model) {
        model.put("message", this.message);
        return "/index";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @GetMapping("/user")
    public String user() {
        return "user";
    }

    @GetMapping("/about")
    public String about() {
        return "/about";
    }

    @GetMapping("/login")
    public String login() {
        return "/login";
    }

    @GetMapping("/403")
    public String error403() {
        return "/error/403";
    }

}