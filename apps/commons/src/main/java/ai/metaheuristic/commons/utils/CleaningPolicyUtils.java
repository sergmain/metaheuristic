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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * cleaningPolicy of a Function is a comma-separated list of {@link EnumsApi.CleaningPolicy} names, e.g.
 * {@code cleaningPolicy: ASSETS, FULL_AFTER_ONE_WEEK}. It's kept as a String in every yaml - a list in one scalar
 * can't be bound to an enum field - and is turned into policies only where they are needed.
 * Static and free of any state on purpose - it's testable without a Spring context.
 *
 * <p>Error code prefix: {@code 01.329.} (unique to this class).
 */
public class CleaningPolicyUtils {

    /**
     * @param onUnknown what is done with a name which isn't a CleaningPolicy - the strict caller throws, a lenient one
     *                  logs it and goes on with the rest of the list
     * @return the policies of the list; a blank item is skipped, a repeated one counts once
     */
    public static EnumSet<EnumsApi.CleaningPolicy> parse(@Nullable String cleaningPolicy, Consumer<String> onUnknown) {
        final EnumSet<EnumsApi.CleaningPolicy> policies = EnumSet.noneOf(EnumsApi.CleaningPolicy.class);
        if (cleaningPolicy==null || cleaningPolicy.isBlank()) {
            return policies;
        }
        for (String item : cleaningPolicy.split(",")) {
            final String name = item.strip();
            if (name.isEmpty()) {
                continue;
            }
            policyOf(name).ifPresentOrElse(policies::add, () -> onUnknown.accept(name));
        }
        return policies;
    }

    /**
     * As strict as the enum-typed field was: a name which isn't a CleaningPolicy is refused.
     */
    public static EnumSet<EnumsApi.CleaningPolicy> parseStrict(@Nullable String cleaningPolicy) {
        return parse(cleaningPolicy, name -> {
            throw new IllegalStateException("01.329.020 unknown cleaningPolicy '" + name +
                    "', expected a comma-separated list of " + Arrays.toString(EnumsApi.CleaningPolicy.values()));
        });
    }

    /**
     * @return the canonical form of a list - names in the order of the enum, joined by ", " - or null for no policies,
     * the same as a Function which declares none
     */
    public static @Nullable String asString(Set<EnumsApi.CleaningPolicy> policies) {
        if (policies.isEmpty()) {
            return null;
        }
        return EnumSet.copyOf(policies).stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    /**
     * @return true when the timeout of the policy since finishedOn has passed at the moment 'now'. A policy with
     * a zero timeout is due at once, with or without finishedOn - as ALL and ASSETS always were. A policy with
     * a timeout is never due for a Task without finishedOn.
     */
    public static boolean isDue(EnumsApi.CleaningPolicy policy, @Nullable Long finishedOn, long now) {
        if (policy.timeout.isZero()) {
            return true;
        }
        return finishedOn!=null && now - finishedOn >= policy.timeout.toMillis();
    }

    private static Optional<EnumsApi.CleaningPolicy> policyOf(String name) {
        return Arrays.stream(EnumsApi.CleaningPolicy.values()).filter(p -> p.name().equals(name)).findFirst();
    }
}
