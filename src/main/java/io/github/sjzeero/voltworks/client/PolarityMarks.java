package io.github.sjzeero.voltworks.client;

import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.Point;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PolarityMarks {
    public static final int POSITIVE = 0xFFFF5A5A;
    public static final int NEGATIVE = 0xFF5AA8FF;

    private PolarityMarks() {
    }

    public static List<Map.Entry<Point, Boolean>> marks(ComponentFootprint fp) {
        var marked = new ArrayList<Map.Entry<Point, Boolean>>();
        for(var entry : fp.getPads().entrySet()) {
            var text = entry.getValue().shortText();
            if(text == null)
                continue;
            var mark = text.getString();
            if(mark.equals("+") || mark.equalsIgnoreCase("P"))
                marked.add(Map.entry(entry.getKey(), true));
            else if(mark.equals("-") || mark.equalsIgnoreCase("N"))
                marked.add(Map.entry(entry.getKey(), false));
        }
        return marked;
    }

    public static List<Pixel> polarity(ComponentFootprint fp) {
        var cells = new LinkedHashMap<Point, Pixel>();
        var marked = marks(fp);
        for(var entry : marked)
            cells.put(entry.getKey(), new Pixel(entry.getKey().x(), entry.getKey().y(), entry.getValue() ? POSITIVE : NEGATIVE));
        for(var entry : marked) {
            var pad = entry.getKey();
            int color = entry.getValue() ? POSITIVE : NEGATIVE;
            double dx = (fp.getWidth() - 1) / 2.0 - pad.x();
            double dy = (fp.getHeight() - 1) / 2.0 - pad.y();
            if(dx == 0 && dy == 0)
                continue;
            int sx = Math.abs(dx) >= Math.abs(dy) ? (int) Math.signum(dx) : 0;
            int sy = sx == 0 ? (int) Math.signum(dy) : 0;
            int x = pad.x() + sx;
            int y = pad.y() + sy;
            inside(cells, fp, x, y, color);
            if(entry.getValue()) {
                inside(cells, fp, x + sy, y + sx, color);
                inside(cells, fp, x - sy, y - sx, color);
                if(Math.abs(sx) * fp.getWidth() + Math.abs(sy) * fp.getHeight() >= 5)
                    inside(cells, fp, x + sx, y + sy, color);
            }
        }
        return new ArrayList<>(cells.values());
    }

    private static void inside(Map<Point, Pixel> cells, ComponentFootprint fp, int x, int y, int color) {
        if(x >= 0 && y >= 0 && x < fp.getWidth() && y < fp.getHeight())
            cells.putIfAbsent(new Point(x, y), new Pixel(x, y, color));
    }

    public record Pixel(int x, int y, int color) {
    }
}
