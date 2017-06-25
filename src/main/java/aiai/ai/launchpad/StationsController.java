package aiai.ai.launchpad;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:21
 */
@Controller
@RequestMapping("/launchpad")
public class StationsController {

    @Value("${aiai.table.rows.limit}")
    private int limit;

    private final StationsRepository repository;

    public StationsController(StationsRepository repository) {
        this.repository = repository;
    }

    @Data
    public static class Result {
        public Slice<Station> items;
        public String prevUrl;
        public String nextUrl;
        public boolean prevAvailable;
        public boolean nextAvailable;
    }

    @GetMapping("/stations")
    public String init(@ModelAttribute Result result, @PageableDefault(size=5) Pageable pageable)  {
        result.items = repository.findAll(pageable);
        return "/launchpad/stations";
    }

    @GetMapping(value = "/stations-add")
    public String add(@ModelAttribute Result result) {
        return "/launchpad/stations-add";
    }

    /**
     * It's used to get as an Ajax call
     */
    @PostMapping("/stations-add-commit")
    public String addCommit(@ModelAttribute Result result, @RequestParam String ip, @RequestParam String desc) {
        Station s = new Station();
        s.setIp(ip);
        s.setDescription(desc);
        repository.save( s );

        return "redirect:/launchpad/stations";
    }

    /**
     * It's used to get as an Ajax call
     */
    @PostMapping("/stations-part")
    public String getStations(@ModelAttribute Result result, @PageableDefault(size=5) Pageable pageable )  {
        result.items = repository.findAll(pageable);
        return "/launchpad/stations :: table"; // *partial* update
    }
}
