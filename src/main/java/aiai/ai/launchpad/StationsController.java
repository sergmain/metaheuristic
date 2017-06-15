package aiai.ai.launchpad;

import aiai.ai.core.ExampleController;
import org.springframework.beans.factory.annotation.Value;
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

    public static class Result {
        public List<Item> items = new ArrayList<>();
        public String prevUrl;
        public String nextUrl;
        public boolean prevAvailable;
        public boolean nextAvailable;

        public List<Item> getItems() {
            return items;
        }

        public String getPrevUrl() {
            return prevUrl;
        }

        public void setPrevUrl(String prevUrl) {
            this.prevUrl = prevUrl;
        }

        public String getNextUrl() {
            return nextUrl;
        }

        public void setNextUrl(String nextUrl) {
            this.nextUrl = nextUrl;
        }

        public boolean isPrevAvailable() {
            return prevAvailable;
        }

        public void setPrevAvailable(boolean prevAvailable) {
            this.prevAvailable = prevAvailable;
        }

        public boolean isNextAvailable() {
            return nextAvailable;
        }

        public void setNextAvailable(boolean nextAvailable) {
            this.nextAvailable = nextAvailable;
        }
    }

    @Value("${aiai.table.rows.limit}")
    private int limit;

    private static List<Item> items = null;

    @GetMapping("/stations")
    public String init(@ModelAttribute ExampleController.Result result)  {
        return "/launchpad/stations"; 
    }

    /**
     * It's used to get as an Ajax call
     */
    @PostMapping("/stations-part")
    public String getStations(@ModelAttribute Result result, @RequestParam(required = false, defaultValue = "0") int start)  {

        if (items==null) {
            items = gen();
        }

        boolean prevAvailable = start > 0;
        if(prevAvailable) {
            int prevStart = start - limit;
            result.setPrevUrl("/launchpad/stations-part?start=" + prevStart);
        }
        result.setPrevAvailable(prevAvailable);

        int nextStart = start + limit;
        boolean nextAvailable = TOTAL_NUMBER > nextStart;
        if(nextAvailable) {
            result.setNextUrl("/launchpad/stations-part?start=" + nextStart);
        }
        result.setNextAvailable(nextAvailable);

        result.items.addAll( items.subList(start, nextAvailable? start+limit :10));

        return "/launchpad/stations :: table"; // *partial* update
    }


    private static Random r = new Random();

    private static List<Item> gen() {
        List<Item> items = new ArrayList<>();


        for (int i = 0; i < TOTAL_NUMBER; i++) {
            final int i1 = r.nextInt();
            items.add(new Item("Id:"+ i1, "Desc: " + i1));
        }

        return items;
    }
}
