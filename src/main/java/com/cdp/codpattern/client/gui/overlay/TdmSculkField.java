package com.cdp.codpattern.client.gui.overlay;

import java.util.Arrays;
import java.util.Objects;

/** Low-resolution, deterministic sculk mask rooted at the edges of the result glyphs. */
final class TdmSculkField {
    enum Outcome {
        VICTORY(88, 40, 0x3A8CD4, new int[][] {{2,14},{15,1},{25,38},{63,0},{87,22},{79,39}},
                0x2B4E5B, 0x356B7B, 0x5095A2, 0x7EBFC9),
        DEFEAT(89, 41, 0xB8302A, new int[][] {{2,15},{20,0},{6,39},{64,2},{83,23},{80,36}},
                0x522E37, 0x713D44, 0x9E5758, 0xC6847A),
        DRAW(87, 39, 0x587E6E, new int[][] {{0,21},{15,1},{18,38},{62,0},{86,22},{78,38}},
                0x394846, 0x4B625D, 0x69897D, 0x9CB19B);

        final int glyphWidth, glyphHeight, rimColor;
        final int[][] edges;
        final int[] palette;

        Outcome(int glyphWidth, int glyphHeight, int rimColor, int[][] edges, int... palette) {
            this.glyphWidth = glyphWidth;
            this.glyphHeight = glyphHeight;
            this.rimColor = rimColor;
            this.edges = edges;
            this.palette = palette;
        }

        static Outcome from(String key) {
            if ("victory".equals(key)) return VICTORY;
            if ("defeat".equals(key)) return DEFEAT;
            return DRAW;
        }
    }

    // Destination in viewport fractions, signed bends along the route, and stem radius in texels.
    private record Route(double x, double y, double firstBend, double lastBend, double radius) {}
    private static final Route[] ROUTES = {
            new Route(-.03, .53, -.17, .19, 3.4), new Route(.17, -.04, .15, -.17, 2.8),
            new Route(.13, 1.04, -.17, .16, 3.2), new Route(.74, -.04, .21, .12, 2.9),
            new Route(1.03, .29, .18, -.12, 3.5), new Route(.91, .97, -.17, .13, 3.3)
    };

    static final class Tile {
        final int x, y;
        private double birth = Double.POSITIVE_INFINITY;
        private double opacity;
        private int pigment;
        int previousAlpha = -1;

        Tile(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int alphaAt(double progress) {
            double t = Math.max(0, Math.min(1, (progress - birth) / .09));
            return (int) Math.round(255 * opacity * t * t * (3 - 2 * t));
        }

        int rgba(int alpha) {
            // NativeImage expects ABGR packed into a Java int.
            return alpha << 24 | pigment;
        }
    }

    record Geometry(int width, int height, int columns, int rows, int titleX, int titleY,
                    int titleWidth, int titleHeight, Outcome outcome) {}

    private final Geometry geometry;
    private final Tile[] grid;

    private TdmSculkField(Geometry geometry) {
        this.geometry = geometry;
        grid = new Tile[geometry.columns() * geometry.rows()];
    }

    static Tile[] build(Geometry geometry) {
        TdmSculkField field = new TdmSculkField(geometry);
        Outcome outcome = geometry.outcome();
        for (int i = 0; i < ROUTES.length; i++) {
            int[] edge = outcome.edges[i];
            double x = geometry.titleX() + (edge[0] + .5) * geometry.titleWidth() / outcome.glyphWidth;
            double y = geometry.titleY() + (edge[1] + .5) * geometry.titleHeight() / outcome.glyphHeight;
            Route route = ROUTES[i];
            field.grow(x * geometry.columns() / geometry.width(), y * geometry.rows() / geometry.height(),
                    route.x * geometry.columns(), route.y * geometry.rows(), route.firstBend, route.lastBend,
                    -.055 + i % 3 * .009, .94 + i % 2 * .04, route.radius, 31 + i * 43, true);
        }
        return Arrays.stream(field.grid).filter(Objects::nonNull).toArray(Tile[]::new);
    }

    private static double noise(int x, int y, int seed) {
        int n = (x + seed) * 0x1f123bb5 ^ y * 0x05491333;
        n = (n ^ (n >>> 15)) * 0x2c1b3c6d;
        return Integer.toUnsignedLong(n ^ (n >>> 12)) / 4294967296.0;
    }

    private void deposit(double cx, double cy, double radius, double arrival, int salt, double strength) {
        int extent = (int) Math.ceil(radius * 1.65);
        int firstY = Math.max(0, (int) Math.floor(cy) - extent);
        int firstX = Math.max(0, (int) Math.floor(cx) - extent);
        for (int y = firstY; y < Math.min(geometry.rows(), cy + extent + 1); y++) {
            for (int x = firstX; x < Math.min(geometry.columns(), cx + extent + 1); x++) {
                double coarse = noise(x >> 1, y >> 1, salt);
                double grain = noise(x, y, salt + 2);
                double distance = Math.hypot(x - cx, y - cy) / radius;
                if (distance > .78 + coarse * .55 + grain * .2) continue;
                int index = y * geometry.columns() + x;
                Tile tile = grid[index];
                if (tile == null) {
                    tile = new Tile(x, y);
                    grid[index] = tile;
                }
                tile.birth = Math.min(tile.birth, arrival + distance * .023 + grain * .012);
                double density = Math.max(.12, Math.min(1, 1.1 - distance * .65));
                double opacity = (.12 + density * .17 + grain * .06) * strength;
                if (opacity > tile.opacity) {
                    int tone = coarse > .67 ? 2 : coarse > .25 ? 1 : 0;
                    if (coarse > .85 && grain > .77 && distance < .95) tone = 3;
                    tile.opacity = opacity;
                    int rgb = geometry.outcome().palette[tone];
                    tile.pigment = ((rgb & 255) << 16) | (rgb & 0xFF00) | (rgb >>> 16);
                }
            }
        }
    }

    private void grow(double sx, double sy, double ex, double ey, double firstBend, double lastBend,
                      double start, double duration, double radius, int salt, boolean forks) {
        double dx = ex - sx, dy = ey - sy, length = Math.hypot(dx, dy);
        if (length == 0) return;
        int count = Math.max(2, (int) Math.ceil(length * 1.25));
        double phase = noise(salt, 7, 53) * Math.PI * 2;
        double dirX = dx / length, dirY = dy / length;
        double nextFork = .12 + noise(salt, 5, 81) * .09;
        int fork = 0;
        for (int i = 0; i <= count; i++) {
            double t = (double) i / count, envelope = Math.sin(Math.PI * t);
            double curve = length * envelope * ((1 - t) * firstBend + t * lastBend);
            double ripple = envelope * Math.sin(t * 11 + phase) * Math.min(3, length * .035);
            double x = sx + dx * t - dirY * (curve + ripple);
            double y = sy + dy * t + dirX * (curve + ripple);
            double taper = 1 - t * .53;
            double wave = Math.cos(17 * t + phase);
            double time = start + t * duration;
            deposit(x, y, Math.max(.8, radius * taper * (.72 + .48 * wave * wave)), time, salt, forks ? 1 : .84);

            if (forks && t >= nextFork && t < .83) {
                int side = fork % 2 == 0 ? 1 : -1;
                double push = length * (.11 + noise(fork, salt, 61) * .11) * side;
                double reach = length * (.10 + noise(fork, salt, 43) * .13);
                grow(x, y, x + dirX * reach - dirY * push, y + dirY * reach + dirX * push,
                        side * .10, side * .17, time, duration * .24, radius * .68, salt + 11 + fork * 3, false);
                nextFork += .17 + noise(fork++, salt, 67) * .08;
            }
            if (i % 9 == 0 && t > .02 && t < .95) {
                int side = noise(i, salt, 13) > .5 ? -1 : 1;
                double offset = radius * taper * (1 + noise(i, salt, 17) * .95) * side;
                deposit(x - dirY * offset, y + dirX * offset, radius * taper * .68 + .65,
                        time + .028, salt + 36, .66);
            }
        }
    }
}
