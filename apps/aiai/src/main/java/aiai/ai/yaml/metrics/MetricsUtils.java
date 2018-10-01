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
package aiai.ai.yaml.metrics;

import aiai.ai.yaml.config.DatasetPreparingConfig;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Because of xxx.class can't use generics (or don't have enough time to find out how to)
 */
public class MetricsUtils {

    private static Yaml yaml;
    private static Yaml valueYaml;
    public static final Metrics EMPTY_METRICS = new Metrics(Metrics.Status.NotFound, null, null);

    static {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Representer representer = new Representer();
        representer.addClassTag(Metrics.class, Tag.MAP);

        yaml = new Yaml(new Constructor(Metrics.class), representer, options);
    }

    static {
        final DumperOptions options1 = new DumperOptions();
        options1.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options1.setPrettyFlow(true);

        Representer representer1 = new Representer();
        representer1.addClassTag(MetricValues.class, Tag.MAP);

        valueYaml = new Yaml(new Constructor(MetricValues.class), representer1, options1);
    }

    public static String toString(Metrics config) {
        return yaml.dump(config);
    }

    public static Metrics to(String s) {
        if (s==null) {
            return null;
        }
        return yaml.load(s);
    }

    public static Metrics to(InputStream is) {
        return yaml.load(is);
    }

    public static Metrics to(File file) {
        try(FileInputStream fis =  new FileInputStream(file)) {
            return yaml.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Error while loading file: " + file.getPath(), e);
        }
    }

    public static MetricValues getValues(Metrics metrics) {
        if (metrics==null || metrics.getStatus()!= Metrics.Status.Ok) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        MetricValues metricValues = valueYaml.load(metrics.metrics);
        return metricValues;
    }

}
