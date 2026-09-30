package io.github.sjzeero.voltworks.client;

import io.github.sjzeero.voltworks.circuits.components.Component;
import io.github.sjzeero.voltworks.circuits.components.ComponentRegistry;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.Point;

import java.util.ArrayList;
import java.util.List;

public final class SchematicSymbols {
    public static final int LINE = 0xFFE8F1F2;
    public static final int POSITIVE = 0xFFFF5A5A;
    public static final int NEGATIVE = 0xFF5AA8FF;

    private SchematicSymbols() {
    }

    public static List<Pixel> symbol(Component component, ComponentFootprint fp) {
        var out = new ArrayList<Pixel>();
        var id = ComponentRegistry.getId(component).getPath();
        int w = fp.getWidth();
        int h = fp.getHeight();
        var pads = new ArrayList<>(fp.getPads().keySet());
        for(var pad : pads)
            add(out, pad.x(), pad.y(), LINE);

        if(id.equals("via") || id.equals("connector")) {
            if(!pads.isEmpty())
                add(out, pads.getFirst().x(), pads.getFirst().y(), LINE);
            return out;
        }
        if(id.contains("resistor") || id.equals("varistor") || id.equals("barretter_tube")) {
            zigzag(out, w, h);
        } else if(id.equals("capacitor")) {
            capacitor(out, w, h);
        } else if(id.equals("inductor")) {
            inductor(out, w, h);
        } else if(id.equals("diode") || id.equals("regulator_tube")) {
            diode(out, w, h);
        } else if(id.contains("bjt") || id.equals("vfet")) {
            transistor(out, w, h, id.contains("pnp"));
        } else if(id.contains("switch") || id.equals("button") || id.contains("relay") || id.equals("fuse_holder")) {
            sw(out, w, h);
        } else if(id.contains("bulb") || id.contains("gauge")) {
            circle(out, w, h);
        } else if(id.contains("tube") || id.equals("triode") || id.equals("pentode") || id.equals("thyratron")) {
            tube(out, w, h);
        } else if(id.equals("potentiometer")) {
            zigzag(out, w, h);
            add(out, w / 2, h / 2, LINE);
            add(out, w / 2, Math.min(h - 1, h / 2 + 1), LINE);
        } else if(id.equals("label") || id.equals("display_module")) {
            box(out, w, h);
        } else {
            box(out, w, h);
        }
        return out;
    }

    public static List<Pixel> polarity(ComponentFootprint fp) {
        var out = new ArrayList<Pixel>();
        for(var entry : fp.getPads().entrySet()) {
            var text = entry.getValue().shortText();
            if(text == null)
                continue;
            var mark = text.getString();
            if(mark.equals("+") || mark.equalsIgnoreCase("P"))
                mark(out, entry.getKey(), fp, true);
            else if(mark.equals("-") || mark.equalsIgnoreCase("N"))
                mark(out, entry.getKey(), fp, false);
        }
        return out;
    }

    private static void mark(List<Pixel> out, Point pad, ComponentFootprint fp, boolean plus) {
        int x = pad.x() < fp.getWidth() / 2 ? Math.min(fp.getWidth() - 2, pad.x() + 1) : Math.max(1, pad.x() - 1);
        int y = pad.y() < fp.getHeight() / 2 ? Math.min(fp.getHeight() - 2, pad.y() + 1) : Math.max(1, pad.y() - 1);
        int color = plus ? POSITIVE : NEGATIVE;
        add(out, x - 1, y, color);
        add(out, x, y, color);
        add(out, x + 1, y, color);
        if(plus) {
            add(out, x, y - 1, color);
            add(out, x, y + 1, color);
        }
    }

    private static void zigzag(List<Pixel> out, int w, int h) {
        int y = h / 2;
        for(int x = 0; x < w; x++)
            add(out, x, x == 0 || x == w - 1 ? y : y + ((x & 1) == 0 ? -1 : 1), LINE);
    }

    private static void capacitor(List<Pixel> out, int w, int h) {
        int cx = w / 2;
        int cy = h / 2;
        if(w >= h) {
            for(int y = 0; y < h; y++) {
                add(out, Math.max(0, cx - 1), y, LINE);
                add(out, Math.min(w - 1, cx + 1), y, LINE);
            }
            add(out, 0, cy, LINE);
            add(out, w - 1, cy, LINE);
        } else {
            for(int x = 0; x < w; x++) {
                add(out, x, Math.max(0, cy - 1), LINE);
                add(out, x, Math.min(h - 1, cy + 1), LINE);
            }
            add(out, cx, 0, LINE);
            add(out, cx, h - 1, LINE);
        }
    }

    private static void inductor(List<Pixel> out, int w, int h) {
        int y = h / 2;
        for(int x = 0; x < w; x++)
            add(out, x, x > 0 && x < w - 1 && (x & 1) == 1 ? Math.max(0, y - 1) : y, LINE);
    }

    private static void diode(List<Pixel> out, int w, int h) {
        int cy = h / 2;
        for(int x = 0; x < w; x++)
            add(out, x, cy, LINE);
        int cx = w / 2;
        add(out, cx - 1, Math.max(0, cy - 1), LINE);
        add(out, cx - 1, Math.min(h - 1, cy + 1), LINE);
        add(out, cx + 1, Math.max(0, cy - 1), LINE);
        add(out, cx + 1, Math.min(h - 1, cy + 1), LINE);
    }

    private static void transistor(List<Pixel> out, int w, int h, boolean pnp) {
        int cx = w / 2;
        for(int y = 0; y < h; y++)
            add(out, cx, y, LINE);
        for(int x = 0; x < w; x++)
            add(out, x, h - 1, LINE);
        add(out, pnp ? Math.max(0, cx - 1) : Math.min(w - 1, cx + 1), 0, LINE);
    }

    private static void sw(List<Pixel> out, int w, int h) {
        int y = h / 2;
        add(out, 0, y, LINE);
        add(out, w - 1, y, LINE);
        for(int x = 1; x < w - 1; x++)
            add(out, x, Math.max(0, y - 1), LINE);
    }

    private static void circle(List<Pixel> out, int w, int h) {
        int cx = w / 2;
        int cy = h / 2;
        add(out, cx, 0, LINE);
        add(out, cx, h - 1, LINE);
        add(out, 0, cy, LINE);
        add(out, w - 1, cy, LINE);
        add(out, cx, cy, LINE);
    }

    private static void tube(List<Pixel> out, int w, int h) {
        box(out, w, h);
        for(int x = 1; x < w - 1; x++)
            add(out, x, h / 2, LINE);
    }

    private static void box(List<Pixel> out, int w, int h) {
        for(int x = 0; x < w; x++) {
            add(out, x, 0, LINE);
            add(out, x, h - 1, LINE);
        }
        for(int y = 0; y < h; y++) {
            add(out, 0, y, LINE);
            add(out, w - 1, y, LINE);
        }
    }

    private static void add(List<Pixel> out, int x, int y, int color) {
        if(x >= 0 && y >= 0 && out.stream().noneMatch(p -> p.x == x && p.y == y && p.color == color))
            out.add(new Pixel(x, y, color));
    }

    public record Pixel(int x, int y, int color) {
    }
}
