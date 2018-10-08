/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.launchpad.env;

import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Env;
import aiai.ai.launchpad.repositories.EnvRepository;
import aiai.ai.utils.checksum.ChecksumWithSignatureService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/launchpad")
@Slf4j
public class EnvController {

    private final Globals globals;
    private final EnvRepository envRepository;

    @Data
    public static class Result {
        Iterable<Env> envs;
    }

    public EnvController(Globals globals, EnvRepository envRepository) {
        this.globals = globals;
        this.envRepository = envRepository;
    }

    @GetMapping("/envs")
    public String init(@ModelAttribute Result result, @ModelAttribute("errorMessage") final String errorMessage) {
        result.envs = envRepository.findAll();
        return "launchpad/envs";
    }

    @PostMapping(value = "/register-new-env-commit")
    public String uploadSnippet(@RequestParam(name = "env") String envStr, String signature, final RedirectAttributes redirectAttributes) {
        int idx = envStr.indexOf(':');
        if (idx==-1) {
            redirectAttributes.addFlashAttribute("errorMessage", "#322.01 ':' wasn't found in environment, format is <key>:<value>, actual: " + envStr);
            return "redirect:/launchpad/envs";
        }
        if (!ChecksumWithSignatureService.isValid(envStr.getBytes(), signature, globals.publicKey)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#325.01 Signature is incorrect, can't store new environment data: " + envStr);
            return "redirect:/launchpad/envs";
        }

        String key = envStr.substring(0, idx).trim();
        String value = envStr.substring(idx+1).trim();

        Env env = envRepository.findByKey(key);
        if (env==null) {
            env = new Env();
            env.key = key;
        }
        env.value = value;
        env.signature = signature;

        envRepository.save(env);
        return "redirect:/launchpad/envs";
    }
}
