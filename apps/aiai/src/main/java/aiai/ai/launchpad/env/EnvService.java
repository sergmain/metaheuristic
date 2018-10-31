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

import aiai.ai.launchpad.beans.Env;
import aiai.ai.launchpad.repositories.EnvRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Profile("launchpad")
public class EnvService {


    public final EnvRepository envRepository;

    public EnvService(EnvRepository envRepository) {
        this.envRepository = envRepository;
    }

    public Map<String, Env> envsAsMap() {
        Map<String, Env> envs = new HashMap<>();
        for (Env env : envRepository.findAll()) {
            envs.put(env.key, env);
        }
        return envs;
    }
}
