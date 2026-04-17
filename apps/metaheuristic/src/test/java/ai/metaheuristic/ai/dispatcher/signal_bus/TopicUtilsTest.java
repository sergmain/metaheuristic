/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.signal_bus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sergio Lissner
 * Plan 01 — Foundation, Step 11.
 * Regex-driven so it gets its own test class — regexes are a known foot-gun.
 */
class TopicUtilsTest {

    @Test
    void stripVersion_removesSemverSuffix() {
        assertThat(TopicUtils.stripVersion("mhdg-rg-flat-1.0.0")).isEqualTo("mhdg-rg-flat");
        assertThat(TopicUtils.stripVersion("cv-redundancy-1.0.0")).isEqualTo("cv-redundancy");
    }

    @Test
    void stripVersion_noVersion_returnsInputUnchanged() {
        assertThat(TopicUtils.stripVersion("plain-name")).isEqualTo("plain-name");
    }

    @Test
    void stripVersion_doubleDigitVersion_stripsCorrectly() {
        assertThat(TopicUtils.stripVersion("name-with-2.3.4")).isEqualTo("name-with");
        assertThat(TopicUtils.stripVersion("workflow-12.34.567")).isEqualTo("workflow");
    }

    @Test
    void stripVersion_versionInMiddle_doesNotStrip() {
        // regex is anchored at end — versions in the middle of the name stay
        assertThat(TopicUtils.stripVersion("name-1.0.0-suffix")).isEqualTo("name-1.0.0-suffix");
    }
}
