/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.station;

import aiai.ai.beans.Env;
import aiai.ai.repositories.EnvironmentRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 14:04
 */
@Controller
@RequestMapping("/station")
public class EnvironmentController {

    @Value("${aiai.table.rows.limit:#{5}}")
    private int limit;

    private EnvironmentRepository repository;

    public EnvironmentController(EnvironmentRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/envs")
    public String init(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/station/envs";
    }

    // for AJAX
    @PostMapping("/envs-part")
    public String getEnvs(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/station/envs :: table";
    }

    @GetMapping(value = "/env-add")
    public String add(Model model) {
        model.addAttribute("env", new Env());
        return "/station/env-form";
    }

    @GetMapping(value = "/env-edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("env", repository.findById(id));
        return "/station/env-form";
    }

    @PostMapping("/env-form-commit")
    public String formCommit(Env env) {
        repository.save(env);
        return "redirect:/station/envs";
    }

    @GetMapping("/env-delete/{id}")
    public String delete(@PathVariable Long id, Model model) {
        final Optional<Env> value = repository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/station/envs";
        }
        model.addAttribute("env", value.get());
        return "/station/env-delete";
    }

    @PostMapping("/env-delete-commit")
    public String deleteCommit(Long id) {
        repository.deleteById(id);
        return "redirect:/station/envs";
    }

    private Pageable fixPageSize(Pageable pageable) {
        if (pageable.getPageSize() != limit) {
            pageable = PageRequest.of(pageable.getPageNumber(), limit);
        }
        return pageable;
    }

    @Data
    public static class Result {
        public Slice<Env> items;
    }
}

