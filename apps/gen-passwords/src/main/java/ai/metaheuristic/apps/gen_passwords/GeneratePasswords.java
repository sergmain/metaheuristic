/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
package ai.metaheuristic.apps.gen_passwords;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.security.*;
import java.util.UUID;

@SpringBootApplication
public class GeneratePasswords implements CommandLineRunner {

    private final PasswordEncoder passwordEncoder;

    public GeneratePasswords(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public static void main(String[] args) {
        SpringApplication.run(GeneratePasswords.class, args);
    }

    @Override
    public void run(String... args) throws IOException, GeneralSecurityException {

        if (args.length<2) {
            System.out.println("GeneratePasswords <master password> <rest password>");
            return;
        }

        System.out.println("Passwords and tokens:");
        System.out.println("\tmaster password: " + args[0]);
        System.out.println("\tmaster password encode: " + passwordEncoder.encode(args[0]));
        System.out.println("\tmaster token: " + UUID.randomUUID());
        System.out.println("\trest password: " + args[1]);
        System.out.println("\trest password encoded: " + passwordEncoder.encode(args[1]));
        System.out.println("\trest token: " + UUID.randomUUID());
   }
}