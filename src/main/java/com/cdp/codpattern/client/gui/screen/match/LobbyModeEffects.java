package com.cdp.codpattern.client.gui.screen.match;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

final class LobbyModeEffects {
    // The approved artwork is a 56-unit-high face with four-unit sculk tiles.
    private static final int HEIGHT = 56;
    private static final int TILE = 4;
    private static final int RAIL = 3;
    private static final int BREATH_PERIOD_MS = 3200;
    private static final String[] BRANCHES = {
            "v#", "#ov...v+", "#v#v.v", "#ov#v", "#v#..vv+", "#ov", "v#",
            "#v", "#ov", "v#vv..v+", "#vo.vv", "#ovv..vv+", "#v..v", "v#...+"
    };
    private static final int[] DARK_LENGTHS = {2, 3, 5, 5, 8, 2, 1, 2, 2, 3, 6, 9, 3, 2};
    private static final Spark[] SPARKS = {
            new Spark(.15f, 4, -3, 920, 1f, .46f),
            new Spark(.71f, 5, 4, 1740, .86f, .41f),
            new Spark(.37f, 3, -4, 3200, 1.2f, .26f),
            new Spark(.84f, 3, -2, 410, 1.05f, .32f),
            new Spark(.51f, 4, 3, 3710, .94f, .28f),
            new Spark(.23f, 3, 4, 2530, 1.12f, .36f)
    };

    private LobbyModeEffects() {}

    static void draw(GuiGraphics graphics, ScreenRectangle bounds, int accent, boolean joined,
                     float growth, float glow, long hoveredMillis) {
        if (bounds.height() <= 0 || bounds.width() <= 0) return;
        float scale = bounds.height() / (float) HEIGHT;
        float border = joined ? RAIL : 0;
        float width = bounds.width() / scale - 2 * border;
        if (width <= 0) return;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(bounds.left() + border * scale, bounds.top(), 0);
        pose.scale(scale, scale, 1);
        Layer layer = new Layer(graphics.bufferSource().getBuffer(RenderType.gui()), pose.last().pose(), width);
        drawBranches(layer, accent, joined, growth);
        if (glow > 0) drawSparks(layer, accent, joined, glow, hoveredMillis);
        graphics.flush();
        pose.popPose();
    }

    private static void drawBranches(Layer layer, int accent, boolean joined, float growth) {
        int stone = joined ? 0x355E58 : accent;
        int vein = joined ? 0x51867E : FastColor.ARGB32.lerp(.22f, accent, 0xFF263D37);
        int tip = joined ? 0x97BFB7 : FastColor.ARGB32.lerp(.24f, accent, 0xFFF5FFF8);
        float opacity = joined ? .24f : .12f;
        for (int row = 0; row < BRANCHES.length; row++) {
            String pattern = BRANCHES[row];
            int columns = joined ? pattern.length() : DARK_LENGTHS[row];
            // Middle branches open a little sooner. All rows reach the same mask at either end.
            float lead = (5 - Math.abs(row - 3)) * .045f;
            float reveal = growth + lead * growth * (1 - growth);
            float front = Mth.lerp(reveal, 2.88f, 54f);
            for (int column = 0; column < columns; column++) {
                float x = column * TILE;
                if (x >= front + 15 || x + RAIL >= layer.width) break;
                char tile = pattern.charAt(column);
                if (joined && tile == '.') continue;
                int color = switch (tile) {
                    case 'v' -> vein;
                    case '+' -> tip;
                    default -> stone;
                };
                float leftAlpha = opacity * Mth.clamp((front + 15 - x) / 30, 0f, 1f);
                float rightAlpha = opacity * Mth.clamp((front + 15 - x - TILE) / 30, 0f, 1f);
                layer.quad(RAIL + x, row * TILE, RAIL + x + TILE, (row + 1) * TILE, color, leftAlpha, rightAlpha);
            }
        }
    }

    private static void drawSparks(Layer layer, int accent, boolean joined, float glow, long hoveredMillis) {
        int color = joined ? 0xECFFF6 : FastColor.ARGB32.lerp(.5f, accent, 0xFFCEFFF4);
        for (int i = 0; i < SPARKS.length; i++) {
            Spark spark = SPARKS[i];
            double period = BREATH_PERIOD_MS * (double) spark.pace * (joined ? 1 : 3.25);
            double clock = hoveredMillis + spark.phaseMillis;
            float travel = (float) ((clock % period) / period);
            if (!joined && i % 2 == 1) clock += BREATH_PERIOD_MS;
            float phase = (float) ((clock % period) / period);
            float peak = joined ? .42f : .32f;
            float end = joined ? .80f : .35f;
            if (phase <= .08f || phase >= end) continue;
            float lift = phase <= peak ? ramp(phase, .08f, peak) : 1 - ramp(phase, peak, end);
            float strength = joined ? lift : darkBreath(phase, lift);
            float alpha = glow * spark.power * strength;
            float x = travel * layer.width;
            float y = spark.lane * HEIGHT + spark.rise * lift;
            float radius = joined ? 2 : 1;
            float halo = alpha * (joined ? .12f : .055f);
            layer.quad(x - radius, y - radius, x + spark.size + radius, y + spark.size + radius, color, halo, halo);
            layer.quad(x, y, x + spark.size, y + spark.size, color, alpha, alpha);
        }
    }

    private static float darkBreath(float phase, float lift) {
        if (phase <= .32f) return .80f * lift;
        if (phase < .328f) return Mth.lerp(ramp(phase, .32f, .328f), .80f, .15f);
        if (phase < .3375f) return Mth.lerp(ramp(phase, .328f, .3375f), .15f, .035f);
        return .035f * (1 - ramp(phase, .3375f, .35f));
    }

    private static float ramp(float phase, float start, float end) {
        float t = (phase - start) / (end - start);
        return t * t * (3 - 2 * t);
    }

    private record Spark(float lane, int size, int rise, int phaseMillis, float pace, float power) {}

    // Each tile is emitted once, keeping both the cell opacity and the number of GPU submissions constant.
    private record Layer(VertexConsumer vertices, Matrix4f pose, float width) {
        void quad(float left, float top, float right, float bottom, int rgb, float leftAlpha, float rightAlpha) {
            left = Math.max(0, left);
            top = Math.max(0, top);
            right = Math.min(width, right);
            bottom = Math.min(HEIGHT, bottom);
            int a = Math.round(leftAlpha * 255);
            int b = Math.round(rightAlpha * 255);
            if (left >= right || top >= bottom || (a | b) == 0) return;
            int leftColor = a << 24 | rgb & 0xFFFFFF;
            int rightColor = b << 24 | rgb & 0xFFFFFF;
            vertices.vertex(pose, left, top, 0).color(leftColor).endVertex();
            vertices.vertex(pose, left, bottom, 0).color(leftColor).endVertex();
            vertices.vertex(pose, right, bottom, 0).color(rightColor).endVertex();
            vertices.vertex(pose, right, top, 0).color(rightColor).endVertex();
        }
    }
}
