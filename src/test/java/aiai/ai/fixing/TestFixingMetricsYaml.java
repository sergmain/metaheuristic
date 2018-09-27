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
package aiai.ai.fixing;

import aiai.ai.launchpad.beans.ExperimentSequence;
import aiai.ai.launchpad.repositories.ExperimentSequenceRepository;
import aiai.ai.yaml.metrics.MetricValues;
import aiai.ai.yaml.metrics.Metrics;
import aiai.ai.yaml.metrics.MetricsUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.StringReader;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class TestFixingMetricsYaml {


    @Autowired
    public ExperimentSequenceRepository experimentSequenceRepository;

    @Test
    public void fix() {
        Iterable<ExperimentSequence> seqs = experimentSequenceRepository.findByExperimentId(105);
        for (ExperimentSequence seq : seqs) {
            Metrics metrics = MetricsUtils.to(seq.metrics);
            if (metrics==null || metrics.getStatus()!= Metrics.Status.Ok) {
                continue;
            }

            boolean isOk = true;
            try {
                MetricValues metricValues = MetricsUtils.getValues(metrics);
            }
            catch (YAMLException e) {
                isOk = false;
            }
            if (isOk) {
                continue;
            }
            log.info("update seq: " + seq);


            String s = metrics.getMetrics();
            LineIterator iterator = IOUtils.lineIterator(new StringReader(s));
//            iterator.next();
            StringBuilder builder = new StringBuilder("values:\n");
            while (iterator.hasNext()) {
                String line = iterator.next();
                builder.append("    ").append(line).append("\n");
//                builder.append(line).append("\n");
            }
            metrics.setMetrics(builder.toString());
            seq.setMetrics( MetricsUtils.toString(metrics));
            experimentSequenceRepository.save(seq);
        }
    }

}
