package io.github.sjzeero.voltworks.client;

import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.Point;

import java.util.ArrayList;
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
        if(marked.isEmpty())
            return byNodeOrder(fp);
        return marked;
    }

    // two-pole parts without pad labels: node 0 is +, node 1 is -
    private static List<Map.Entry<Point, Boolean>> byNodeOrder(ComponentFootprint fp) {
        var marked = new ArrayList<Map.Entry<Point, Boolean>>();
        if(fp.getPads().size() != 2)
            return marked;
        Point plus = null, minus = null;
        for(var entry : fp.getPads().entrySet()) {
            int node = entry.getValue().nodeIndex();
            if(node == 0)
                plus = entry.getKey();
            else if(node == 1)
                minus = entry.getKey();
        }
        if(plus != null && minus != null) {
            marked.add(Map.entry(plus, true));
            marked.add(Map.entry(minus, false));
        }
        return marked;
    }
}
