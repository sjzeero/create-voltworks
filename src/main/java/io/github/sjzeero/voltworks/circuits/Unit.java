/*
 * Copyright 2025 patryk3211
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by sjzeero: ported from Create: Power Grid to CEE: Voltworks.
 */
package io.github.sjzeero.voltworks.circuits;

import net.minecraft.network.chat.Component;

public enum Unit {
    VOLTAGE("V"),
    CURRENT("A"),
    POWER("W"),
    RESISTANCE("\u03a9"),
    TEMPERATURE("\u00b0C"),
    ENERGY("J");

    private final String symbol;

    Unit(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public Formatted formatWithPrefixes(double value) {
        String text;
        if (Math.abs(value) < 1)
            text = number(value * 1000) + " m" + symbol;
        else if (Math.abs(value) < 1000)
            text = number(value) + " " + symbol;
        else if (Math.abs(value) < 1000000)
            text = number(value / 1000) + " k" + symbol;
        else
            text = number(value / 1000000) + " M" + symbol;
        return new Formatted(text);
    }

    public Component format(double v) {
        return Component.literal(number(v) + " " + symbol);
    }

    private static String number(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith("."))
                s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public record Formatted(String string) {
        public Component component() {
            return Component.literal(string);
        }
    }
}
