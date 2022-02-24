/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.db_copy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalDateTime;

/**
 * @author Serge
 * Date: 2/21/2022
 * Time: 8:56 PM
 */
@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class DbCopyApplication implements CommandLineRunner {

    public final DbCopyService dbCopyService;

    public static void main(String[] args) {
        SpringApplication.run(DbCopyApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("Started at " + LocalDateTime.now());
        dbCopyService.process();
        System.out.println("Finished at " + LocalDateTime.now());
    }
}
