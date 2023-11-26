/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.ExitApplicationException;
import ai.metaheuristic.commons.utils.*;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYaml;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;

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
            CommandLine cmd = parseArgs(args);
            BundleData.Cfg cfg = initPackaging(cmd);

            BundleCfgYaml bundleCfgYaml = initBundleCfg(cmd, cfg.baseDir, cfg.gitInfo);
            BundleUtils.createBundle(cfg, bundleCfgYaml);
            System.out.println("All done.");
        } catch (ExitApplicationException e) {
            System.out.println(e.message);
            System.exit(SpringApplication.exit(appCtx, () -> -2));
        }
    }

    private static BundleCfgYaml initBundleCfg(CommandLine cmd, Path currDir, @Nullable GitInfo gitInfo) throws IOException {
        String bundleFilename = cmd.getOptionValue("b");
        if (S.b(bundleFilename)) {
            bundleFilename = CommonConsts.BUNDLE_CFG_YAML;
        }
        System.out.println("Effective bundle filename is " + bundleFilename);

        return BundleUtils.readBundleCfgYaml(currDir, gitInfo, bundleFilename);
    }

    private static BundleData.Cfg initPackaging(CommandLine cmd) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        PrivateKey privateKey = getPrivateKey(cmd);

        Path baseDir = Path.of(SystemUtils.USER_DIR);

        GitInfo gitInfo = null;
        if (cmd.hasOption("git-repo")) {
            if (!cmd.hasOption("git-branch")) {
                throw new ExitApplicationException("Option --git-branch was missed");
            }
            if (!cmd.hasOption("git-commit")) {
                throw new ExitApplicationException("Option --git-commit was missed");
            }
            gitInfo = new GitInfo(
                cmd.getOptionValue("git-repo"),
                cmd.getOptionValue("git-branch"),
                cmd.getOptionValue("git-commit"),
                cmd.getOptionValue("git-path"));

            if (gitInfo.branch.indexOf('/')!=-1) {
                throw new ExitApplicationException("Option --git-branch can't contain '/', i.e. origin/main or remote/master are wrong, must be main only.");
            }
            if (privateKey!=null) {
                privateKey = null;
                System.out.println("Warning. Even PrivateKey was specified, signature won't be calculated because source is git.");
            }
        }

        BundleData.Cfg cfg = new BundleData.Cfg(privateKey, baseDir, gitInfo);

        System.out.println("\tbaseDir dir: " + baseDir);

        return cfg;
    }

    @Nullable
    private static PrivateKey getPrivateKey(CommandLine cmd) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        PrivateKey privateKey = null;
        if (cmd.hasOption("key")) {
            Path privateKeyFile = Path.of(cmd.getOptionValue("key"));
            if (Files.notExists(privateKeyFile)) {
                throw new ExitApplicationException("Private key file wasn't found. File: " + privateKeyFile);
            }
            String privateKeyStr = Files.readString(privateKeyFile, StandardCharsets.UTF_8);
            privateKey = SecUtils.getPrivateKey(privateKeyStr);
        }
        return privateKey;
    }

    public static CommandLine parseArgs(String... args) throws ParseException {
        Options options = new Options();

        //        Option versionOption = new Option("v", "version", true, "version of cli parameters");
//        versionOption.setRequired(false);
//        options.addOption(versionOption);

        Option keyOption = new Option("key", "private-key", true, "Private key for signing content");
        keyOption.setRequired(false);
        options.addOption(keyOption);

        Option bundleOption = new Option("b", "bundle", true, "path to bundle file");
        bundleOption.setRequired(false);
        options.addOption(bundleOption);

        Option gitRepoOption = new Option("repo", "git-repo", true, "URL of git repository");
        gitRepoOption.setRequired(false);
        options.addOption(gitRepoOption);

        Option gitBranchOption = new Option("branch", "git-branch", true, "Branch of git repository");
        gitBranchOption.setRequired(false);
        options.addOption(gitBranchOption);

        Option gitCommitOption = new Option("commit", "git-commit", true, "Commit in git repository");
        gitCommitOption.setRequired(false);
        options.addOption(gitCommitOption);

        Option gitPathOption = new Option("path", "git-path", true, "Path in git repository to dir with bundle.yaml");
        gitPathOption.setRequired(false);
        options.addOption(gitPathOption);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        return cmd;
    }
}
