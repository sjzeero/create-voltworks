package io.github.sjzeero.voltworks.client;

import io.github.sjzeero.voltworks.circuits.components.Component;
import io.github.sjzeero.voltworks.circuits.components.ComponentRegistry;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchematicSymbols {
    public static final int LINE = 0xFFF4F7F8;
    public static final int OUTLINE = 0xFF0B1016;
    public static final int GLOW = 0xFF8CF08C;
    public static final int TERMINAL = 0xFFFCB603;
    public static final int POSITIVE = 0xFFFF5A5A;
    public static final int NEGATIVE = 0xFF5AA8FF;

    private static final Map<String, List<String[]>> ART = new HashMap<>();
    private static final Map<String, String[]> GLYPHS = new HashMap<>();

    static {
        art("via", "0");
        art("connector",
                "###",
                "#0#",
                "###");
        art("resistor",
                ".###.",
                "0#.#1",
                ".###.");
        art("resistor",
                "###",
                "0.1",
                "###");
        art("diode",
                ".#.#.",
                "0###1",
                ".#.#.");
        art("diode",
                ".##",
                "0#1",
                ".##");
        art("capacitor",
                "#.#",
                "0.1",
                "#.#");
        art("inductor",
                "###",
                "0.1",
                "...");
        art("varistor",
                ".##.",
                "0#..",
                "..#1",
                ".##.");
        art("potentiometer",
                ".###.",
                ".0#2.",
                ".#1#.",
                ".....",
                ".....");
        art("switch",
                ".#..",
                "0#.1",
                "....");
        art("button",
                "###",
                "0.1",
                "...");
        art("fuse_holder",
                ".##.",
                "0##1",
                ".##.");
        art("relay",
                "0.2#",
                "#..3",
                "1.4.");
        art("relay_dpdt",
                "0.2.5",
                "#.3.6",
                "1.4.7");
        art("redstone_relay",
                ".0.",
                ".#.",
                "#..",
                "...",
                ".1.");
        art("voltage_gauge",
                ".#0#.",
                "#...#",
                "#...#",
                "#...#",
                ".#1#.");
        art("current_gauge",
                ".#0#.",
                "#...#",
                "#...#",
                "#...#",
                ".#1#.");
        art("light_bulb",
                ".#.",
                "0o1",
                ".#.");
        art("regulator_tube",
                "#.#",
                "0o1",
                "#.#");
        art("neon_bulb",
                "0o",
                "o1");
        art("barretter_tube",
                "0#",
                "#1");
        art("triode",
                "2.1",
                "#0#",
                "3#4");
        art("pentode",
                "251",
                "#0#",
                "3#4");
        art("thyratron",
                "o1o",
                "#0#",
                "3#4");
        art("vfet",
                "0.1",
                "#.#",
                ".2.");
        art("bjt_npn",
                "0.2",
                "#1#");
        art("bjt_pnp",
                "2.0",
                "e1e");
        art("display_module",
                "##2#",
                "0oo#",
                "#oo1",
                "####");
        GLYPHS.put("voltage_gauge", new String[] {"#.#", "#.#", ".#."});
        GLYPHS.put("current_gauge", new String[] {".#.", "###", "#.#"});
    }

    private SchematicSymbols() {
    }

    private static void art(String id, String... rows) {
        ART.computeIfAbsent(id, k -> new ArrayList<>()).add(rows);
    }

    public static List<Pixel> symbol(Component component, ComponentFootprint fp) {
        var id = ComponentRegistry.getId(component).getPath();
        int w = fp.getWidth();
        int h = fp.getHeight();
        var grid = new int[h][w];
        var drawn = find(id, fp);
        if(drawn != null) {
            for(int y = 0; y < h; y++) {
                for(int x = 0; x < w; x++) {
                    char c = drawn[y][x];
                    if(c == '#')
                        grid[y][x] = LINE;
                    else if(c == 'o' || c == 'e')
                        grid[y][x] = GLOW;
                }
            }
        } else if(!id.equals("label")) {
            frame(grid, w, h);
        } else {
            for(int x = 0; x < w; x += 2)
                grid[0][x] = LINE;
        }
        var glyph = GLYPHS.get(id);
        if(glyph != null && w >= 5 && h >= 5) {
            for(int y = 0; y < 3; y++) {
                for(int x = 0; x < 3; x++) {
                    if(glyph[y].charAt(x) == '#')
                        grid[y + 1][x + 1] = GLOW;
                }
            }
        }
        var colors = new HashMap<Point, Integer>();
        for(var mark : marks(fp))
            colors.put(mark.getKey(), mark.getValue() ? POSITIVE : NEGATIVE);
        for(var pad : fp.getPads().keySet())
            grid[pad.y()][pad.x()] = colors.getOrDefault(pad, TERMINAL);

        var out = new ArrayList<Pixel>();
        for(int y = 0; y < h; y++) {
            for(int x = 0; x < w; x++) {
                int color = grid[y][x];
                if(color == 0 && touches(grid, x, y))
                    color = OUTLINE;
                if(color != 0)
                    out.add(new Pixel(x, y, color));
            }
        }
        return out;
    }

    private static void frame(int[][] grid, int w, int h) {
        for(int x = 0; x < w; x++) {
            grid[0][x] = LINE;
            grid[h - 1][x] = LINE;
        }
        for(int y = 0; y < h; y++) {
            grid[y][0] = LINE;
            grid[y][w - 1] = LINE;
        }
    }

    private static boolean touches(int[][] grid, int x, int y) {
        for(int dy = -1; dy <= 1; dy++) {
            for(int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if(ny >= 0 && ny < grid.length && nx >= 0 && nx < grid[ny].length && grid[ny][nx] != 0 && grid[ny][nx] != OUTLINE)
                    return true;
            }
        }
        return false;
    }

    private static char[][] find(String id, ComponentFootprint fp) {
        for(var rows : ART.getOrDefault(id, List.of())) {
            var grid = new char[rows.length][];
            for(int i = 0; i < rows.length; i++)
                grid[i] = rows[i].toCharArray();
            for(int flip = 0; flip < 2; flip++) {
                for(int turn = 0; turn < 4; turn++) {
                    if(fits(grid, fp))
                        return grid;
                    grid = rotate(grid);
                }
                grid = mirror(grid);
            }
        }
        return null;
    }

    private static boolean fits(char[][] grid, ComponentFootprint fp) {
        if(grid.length != fp.getHeight() || grid[0].length != fp.getWidth())
            return false;
        int pads = 0;
        for(var row : grid) {
            for(char c : row) {
                if(Character.isDigit(c))
                    pads++;
            }
        }
        if(pads != fp.getPads().size())
            return false;
        for(var entry : fp.getPads().entrySet()) {
            char c = grid[entry.getKey().y()][entry.getKey().x()];
            int index = entry.getValue().nodeIndex();
            if(c != (char) ('0' + Math.max(index, 0)))
                return false;
        }
        return true;
    }

    private static char[][] rotate(char[][] grid) {
        int h = grid.length;
        int w = grid[0].length;
        var out = new char[w][h];
        for(int y = 0; y < h; y++) {
            for(int x = 0; x < w; x++)
                out[x][h - 1 - y] = grid[y][x];
        }
        return out;
    }

    private static char[][] mirror(char[][] grid) {
        int h = grid.length;
        int w = grid[0].length;
        var out = new char[h][w];
        for(int y = 0; y < h; y++) {
            for(int x = 0; x < w; x++)
                out[y][w - 1 - x] = grid[y][x];
        }
        return out;
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
