package com.cdp.codpattern.client.gui.screen.match;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class LobbyModeEffects {
    // The approved artwork is a 56-unit-high face with four-unit sculk tiles.
    private static final ResourceLocation BAG_SCULK =
            new ResourceLocation("codpattern", "textures/gui/backpack/sculk_row.png");
    private static final int HEIGHT = 56;
    private static final int TILE = 4;
    private static final int RAIL = 3;
    private static final int BREATH_PERIOD_MS = 3200;
    private static final float HOVER_GRADIENT_SLOPE = (float) Math.tan(Math.toRadians(2));
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

    public static final class Motion {
        private long lastUpdate = -1L;
        private boolean engaged;
        private long hoveredMillis;
        private float hover, rail, growth, glow;

        public void update(boolean nextEngaged, long nowMs) {
            long elapsed = lastUpdate < 0L ? 0L : Math.max(0L, nowMs - lastUpdate);
            lastUpdate = nowMs;
            if (engaged) hoveredMillis += elapsed;
            float target = engaged ? 1f : 0f;
            hover = Mth.approach(hover, target, elapsed / 220f);
            rail = Mth.approach(rail, target, elapsed / 260f);
            growth = Mth.approach(growth, target, elapsed / (engaged ? 1450f : 1100f));
            glow = Mth.approach(glow, target, elapsed / 320f);
            engaged = nextEngaged;
        }

        float highlight() {
            return ease(hover);
        }
    }

    private static float ease(float t) {
        return t * t * (3f - 2f * t);
    }

    static void drawFace(GuiGraphics graphics, ScreenRectangle bounds, int accent, boolean joined,
                         Motion motion, float opacity, int edgeWidth) {
        if (opacity <= 0f || bounds.height() <= 0 || bounds.width() <= 0) return;
        opacity = Math.min(1f, opacity);
        int railWidth = Mth.clamp(edgeWidth, 0, bounds.width());
        int edge = joined ? Math.min(railWidth, bounds.width() / 2) : 0;
        drawSurface(graphics, bounds, accent, joined, edge, motion.highlight(), opacity);
        drawSculk(graphics, bounds, accent, joined, motion.growth, ease(motion.glow), motion.hoveredMillis, opacity);

        drawRail(graphics, bounds, accent, joined, motion, opacity, railWidth, edge);
    }

    /** Same face, rail, and animation clock as the lobby, with the bag's soft, pre-baked sculk mask. */
    public static void drawBagFace(GuiGraphics graphics, ScreenRectangle bounds, int accent, boolean joined,
                                   Motion motion, int edgeWidth) {
        int railWidth = Mth.clamp(edgeWidth, 0, bounds.width());
        int edge = joined ? Math.min(railWidth, bounds.width() / 2) : 0;
        drawSurface(graphics, bounds, accent, joined, edge, motion.highlight(), 1f);
        drawRail(graphics, bounds, accent, joined, motion, 1f, railWidth, edge);
        drawBagSculk(graphics, bounds, accent, joined, motion, edge + railWidth);
    }

    private static void drawBagSculk(GuiGraphics graphics, ScreenRectangle bounds, int accent, boolean joined,
                                     Motion motion, int inset) {
        if (!joined && motion.glow <= 0f && motion.growth <= 0f) return;
        float scale = bounds.height() / (float) HEIGHT;
        int available = bounds.width() - inset * 2;
        if (available <= 0 || scale <= 0f) return;
        float growth = ease(motion.growth);
        int sourceWidth = Math.min(Math.round(10 + 30 * growth), (int) (available / scale));
        if (sourceWidth <= 0) return;

        float glow = ease(motion.glow);
        int tint = joined ? FastColor.ARGB32.lerp(.21f, 0xFF163C38, accent) : accent;
        float opacity = joined ? .19f + .22f * glow : .34f * Math.max(glow, growth);
        int x = bounds.left() + inset, y = bounds.top();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(FastColor.ARGB32.red(tint) / 255f, FastColor.ARGB32.green(tint) / 255f,
                FastColor.ARGB32.blue(tint) / 255f, opacity);
        graphics.blit(BAG_SCULK, x, y, Math.max(1, Math.round(sourceWidth * scale)), bounds.height(),
                2f, 2f, sourceWidth, HEIGHT, 64, 64);

        if (glow > 0f) {
            int trailWidth = Math.min(available, Math.max(1, Math.round(20 * scale)));
            float phase = (motion.hoveredMillis % BREATH_PERIOD_MS) / (float) BREATH_PERIOD_MS;
            float pulse = (float) Math.sin(Math.PI * phase);
            int travel = Math.round((available - trailWidth) * phase);
            RenderSystem.setShaderColor(.78f, 1f, .94f, .40f * glow * pulse * pulse);
            graphics.blit(BAG_SCULK, x + travel, y + bounds.height() / 3, trailWidth,
                    Math.max(1, Math.round(12 * scale)), 43f, 3f, 20, 12, 64, 64);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void drawRail(GuiGraphics graphics, ScreenRectangle bounds, int accent, boolean joined,
                                 Motion motion, float opacity, int railWidth, int edge) {
        float rail = ease(motion.rail);
        int railHeight = Math.max(1, Math.round(bounds.height() * .82f * (.52f + .48f * rail)));
        int railY = bounds.top() + (bounds.height() - railHeight) / 2;
        int railColor = joined ? FastColor.ARGB32.lerp(.32f, 0xFF101918, accent) : accent;
        graphics.fill(bounds.left() + edge, railY, Math.min(bounds.right(), bounds.left() + edge + railWidth),
                railY + railHeight, (Math.round((143 + 112 * rail) * opacity) << 24) | (railColor & 0xFFFFFF));
    }

    private static int fade(int color, float opacity) {
        return (Math.round((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF);
    }

    private static void drawSurface(GuiGraphics graphics, ScreenRectangle bounds, int accent, boolean joined,
                                    int edge, float highlight, float opacity) {
        int x = bounds.left();
        int y = bounds.top();
        if (joined) {
            if (edge > 0) {
                int borderColor = fade(0xFF080A09, opacity);
                graphics.fill(x, y, x + edge, bounds.bottom(), borderColor);
                graphics.fill(bounds.right() - edge, y, bounds.right(), bounds.bottom(), borderColor);
            }
            graphics.fill(x + edge, y, bounds.right() - edge, bounds.bottom(),
                    fade(FastColor.ARGB32.lerp(.07f * highlight, accent, 0xFFFFFFFF), opacity));
            return;
        }
        // GuiGraphics supplies a vertical gradient. Rotate its local axes to shade the row left to right.
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZN.rotationDegrees(90));
        graphics.fillGradient(-bounds.height(), 0, 0, bounds.width(), fade(0xED101719, opacity), fade(0xCF11181A, opacity));
        graphics.pose().popPose();
        if (highlight > 0f) drawDarkHover(graphics, bounds, accent, highlight * opacity);
    }

    private static void drawDarkHover(GuiGraphics graphics, ScreenRectangle bounds, int accent, float highlight) {
        // The 92-degree overlay reaches its final translucent color at the 75% stop.
        float skew = Math.min(bounds.width() / 3f, bounds.height() * HOVER_GRADIENT_SLOPE);
        float stop = .75f * (bounds.width() + skew);
        int left = bounds.left(), right = bounds.right();
        int top = bounds.top(), bottom = bounds.bottom();
        int accentMix = FastColor.ARGB32.lerp(.22f, 0xFF141F20, accent);
        int start = (Math.round(255 * highlight) << 24) | (accentMix & 0xFFFFFF);
        int end = (Math.round(201 * highlight) << 24) | 0x293539;
        int bottomLeft = FastColor.ARGB32.lerp(skew / stop, start, end);

        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.gui());
        vertices.vertex(pose, left, top, 0).color(start).endVertex();
        vertices.vertex(pose, left, bottom, 0).color(bottomLeft).endVertex();
        vertices.vertex(pose, left + stop - skew, bottom, 0).color(end).endVertex();
        vertices.vertex(pose, left + stop, top, 0).color(end).endVertex();
        vertices.vertex(pose, left + stop, top, 0).color(end).endVertex();
        vertices.vertex(pose, left + stop - skew, bottom, 0).color(end).endVertex();
        vertices.vertex(pose, right, bottom, 0).color(end).endVertex();
        vertices.vertex(pose, right, top, 0).color(end).endVertex();
    }

    private static void drawSculk(GuiGraphics graphics, ScreenRectangle bounds, int accent, boolean joined,
                                  float growth, float glow, long hoveredMillis, float opacity) {
        float scale = bounds.height() / (float) HEIGHT;
        float border = joined ? RAIL : 0;
        float width = bounds.width() / scale - 2 * border;
        if (width <= 0) return;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(bounds.left() + border * scale, bounds.top(), 0);
        pose.scale(scale, scale, 1);
        Layer layer = new Layer(graphics.bufferSource().getBuffer(RenderType.gui()), pose.last().pose(), width);
        drawBranches(layer, accent, joined, growth, opacity);
        if (glow > 0) drawSparks(layer, accent, joined, glow * opacity, hoveredMillis);
        graphics.flush();
        pose.popPose();
    }

    private static void drawBranches(Layer layer, int accent, boolean joined, float growth, float faceOpacity) {
        int stone = joined ? 0x355E58 : accent;
        int vein = joined ? 0x51867E : FastColor.ARGB32.lerp(.22f, accent, 0xFF263D37);
        int tip = joined ? 0x97BFB7 : FastColor.ARGB32.lerp(.24f, accent, 0xFFF5FFF8);
        float opacity = (joined ? .24f : .12f) * faceOpacity;
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
