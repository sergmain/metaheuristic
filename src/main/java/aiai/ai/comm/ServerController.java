package aiai.ai.comm;

import aiai.ai.launchpad.Dataset;
import aiai.ai.launchpad.StationsRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 19:19
 */
@Controller
@RequestMapping("/srv")
public class ServerController {

    private StationsRepository repository;

    public ServerController(StationsRepository repository) {
        this.repository = repository;
    }

//    @PostMapping("/in")
    @GetMapping("/in")
    public void getDatasets(@RequestParam String json )  {
//    public void getDatasets(String json )  {

        System.out.println("received json: " + json);
/*
        List<Protocol.Command> commands

        repository.save( dataset );

        final Optional<Dataset> value = repository.findById(id);
        repository.save( dataset );
*/
    }

}
