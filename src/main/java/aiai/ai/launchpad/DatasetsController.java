package aiai.ai.launchpad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
public class DatasetsController {

    public static final int TOTAL_NUMBER = 10;

    public static class Item {
        public Item(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String id;
        public String description;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    private DatasetsRepository repository;

    public static class Result {
        public Slice<Datasets> items;

        public Slice<Datasets> getItems() {
            return items;
        }
    }

    @Value("${aiai.table.rows.limit}")
    private int limit;

    private static List<Item> items = null;

    @GetMapping("/datasets")
    public String init(@ModelAttribute Result result)  {
        return "/launchpad/datasets";
    }



    // fix default Pabeable - https://stackoverflow.com/questions/27032433/set-default-page-size-for-jpa-pageable-object
    /**
     * It's used to get as an Ajax call
     */
    @PostMapping("/datasets-part")
    public String getDatasets(@ModelAttribute Result result, Pageable pageable /* @RequestParam(required = false, defaultValue = "0") int start */)  {

        result.items = repository.findAll(pageable);
        return "/launchpad/datasets :: table";
    }
}
