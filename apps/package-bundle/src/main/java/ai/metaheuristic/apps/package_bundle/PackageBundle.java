/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
package ai.metaheuristic.apps.package_bundle;

import ai.metaheuristic.commons.bundle.PackageBundleUtils;
import ai.metaheuristic.commons.exceptions.BundleProcessingException;
import org.apache.commons.cli.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.security.GeneralSecurityException;

@SpringBootApplication
public class PackageBundle implements CommandLineRunner {

    private final ApplicationContext appCtx;

    public PackageBundle(ApplicationContext appCtx) {
        this.appCtx = appCtx;
    }

    public static void main(String[] args) {
            SpringApplication.run(PackageBundle.class, args);
        }

    @Override
    public void run(String... args) throws IOException, GeneralSecurityException, ParseException {
        try {
            PackageBundleUtils.packageBundle(args);
        } catch (BundleProcessingException e) {
            System.out.println(e.message);
            System.exit(SpringApplication.exit(appCtx, () -> -2));
        }
    }

}
