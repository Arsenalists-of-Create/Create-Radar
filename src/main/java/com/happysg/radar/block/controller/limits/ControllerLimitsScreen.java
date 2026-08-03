package com.happysg.radar.block.controller.limits;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.limits.collision.ControllerCollisionProjection;
import com.happysg.radar.block.controller.limits.collision.ControllerCollisionSnapshot;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.networking.packets.ControllerCollisionSnapshotRequestPacket;
import com.happysg.radar.networking.packets.SetControllerMovementLimitsPacket;
import com.happysg.radar.utils.screenelements.MonitorButton;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class ControllerLimitsScreen extends Screen {
    private static final int DESIGN_SIZE = 512;
    private static final int TARGET_UI_PIXELS = 900;
    private static final int DISPLAY_MARGIN = 24;
    private static final float[] ENVIRONMENT_BRIGHTNESS =
            {1.0f, 0.84f, 0.68f, 0.52f, 0.38f};
    private static final int ENVIRONMENT_ALPHA = 235;
    private static final float[] CANNON_BRIGHTNESS =
            {1.0f, 0.94f, 0.86f, 0.77f, 0.68f};
    private static final int CANNON_ALPHA = 255;
    private static final int DIAL_DIM_COLOR = 0xA0808890;
    private static final int DIAL_ALLOWED_COLOR = 0xFFF0D060;
    private static final int LOWER_ARM_COLOR = 0xFFFFA040;
    private static final int UPPER_ARM_COLOR = 0xFF40C8FF;

    private final BlockPos controllerPos;
    private final int sessionNonce = ThreadLocalRandom.current().nextInt();
    private int left;
    private int top;
    private int uiSize;
    private boolean snapshotRequested;
    private boolean showEnvironmentProjection;
    private ControllerCollisionSnapshot collisionSnapshot;
    private ControllerCollisionProjection.ProjectedView collisionProjection;
    private MonitorButton projectionToggleButton;
    private Button applyButton;
    private CannonAxis dialAxis = CannonAxis.PITCH;
    private ControllerLimitDialMath.Handle draggedHandle;
    private double dialCenterU;
    private double dialCenterV;
    private double dialZeroDegrees;
    private double supportedMinDegrees = -90.0;
    private double supportedMaxDegrees = 90.0;
    private double savedMinDegrees = -90.0;
    private double savedMaxDegrees = 90.0;
    private double draftMinDegrees = -90.0;
    private double draftMaxDegrees = 90.0;
    private boolean dialReady;
    private boolean limitsDirty;

    public ControllerLimitsScreen(BlockPos controllerPos) {
        super(Component.translatable("create_radar.controller_limits.title"));
        this.controllerPos = controllerPos.immutable();
    }

    @Override
    protected void init() {
        super.init();
        layoutPanel();
        showEnvironmentProjection =
                RadarConfig.client().showControllerSurroundings.get();
        addProjectionToggleButton();
        addApplyButton();
        rebuildCollisionProjection();
        requestCollisionSnapshot();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        layoutPanel();
        rebuildCollisionProjection();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        // Match MonitorScreen: retain the visible world without screen blur.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                       float partialTick) {
        RenderSystem.enableBlend();
        graphics.blit(CreateRadar.asResource("textures/gui/monitor_gui.png"),
                left, top, uiSize, uiSize,
                0, 0, DESIGN_SIZE, DESIGN_SIZE,
                DESIGN_SIZE, DESIGN_SIZE);
        RenderSystem.disableBlend();

        renderCollisionView(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    public void acceptCollisionSnapshot(
            BlockPos position, int nonce,
            ControllerCollisionSnapshot snapshot
    ) {
        if (!controllerPos.equals(position) || sessionNonce != nonce) {
            return;
        }
        if (snapshot.status() == ControllerCollisionSnapshot.Status.NO_MOUNT) {
            onClose();
            return;
        }
        collisionSnapshot = snapshot;
        dialAxis = snapshot.axis();
        dialCenterU = snapshot.dialCenterU();
        dialCenterV = snapshot.dialCenterV();
        dialZeroDegrees = snapshot.dialZeroDegrees();
        supportedMinDegrees = snapshot.supportedMinDegrees();
        supportedMaxDegrees = snapshot.supportedMaxDegrees();
        dialReady = snapshot.status() == ControllerCollisionSnapshot.Status.OK;
        if (!limitsDirty && draggedHandle == null) {
            savedMinDegrees = snapshot.minDegrees();
            savedMaxDegrees = snapshot.maxDegrees();
            draftMinDegrees = savedMinDegrees;
            draftMaxDegrees = savedMaxDegrees;
        }
        refreshApplyButton();
        rebuildCollisionProjection();
    }


    public void submitLimits(double minDegrees, double maxDegrees) {
        SetControllerMovementLimitsPacket.send(
                controllerPos, minDegrees, maxDegrees);
    }

    public ControllerLimitAccess getController() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        BlockEntity blockEntity = minecraft.level.getBlockEntity(controllerPos);
        return blockEntity instanceof ControllerLimitAccess controller
                ? controller : null;
    }

    private void requestCollisionSnapshot() {
        if (snapshotRequested) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        snapshotRequested = true;
        ControllerCollisionSnapshotRequestPacket.send(controllerPos,
                sessionNonce, minecraft.player.getDirection());
    }

    private void renderCollisionView(GuiGraphics graphics) {
        int margin = collisionDisplayMargin();
        int displayLeft = left + margin;
        int displayTop = top + margin;
        int displaySize = collisionDisplaySize();

        graphics.enableScissor(displayLeft, displayTop,
                displayLeft + displaySize, displayTop + displaySize);
        try {
            graphics.fill(displayLeft, displayTop,
                    displayLeft + displaySize, displayTop + displaySize,
                    0x26000000);
            if (collisionProjection != null) {
                renderWireframe(graphics, displayLeft, displayTop,
                        displaySize, collisionProjection);
            }
            if (dialReady) {
                renderLimitDial(graphics, displayLeft, displayTop,
                        displaySize);
            }
            renderCollisionStatus(graphics, displayLeft, displayTop,
                    displaySize);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderWireframe(
            GuiGraphics graphics, int displayLeft, int displayTop,
            int displaySize,
            ControllerCollisionProjection.ProjectedView projection
    ) {
        int resolution = projection.resolution();
        RenderSystem.enableBlend();
        for (ControllerCollisionProjection.LineRun run : projection.runs()) {
            int x1 = displayLeft + run.startX() * displaySize / resolution;
            int x2 = displayLeft + (run.endXExclusive() * displaySize
                    + resolution - 1) / resolution;
            int y1 = displayTop + run.y() * displaySize / resolution;
            int y2 = displayTop + ((run.y() + 1) * displaySize
                    + resolution - 1) / resolution;
            graphics.fill(x1, y1, Math.max(x1 + 1, x2),
                    Math.max(y1 + 1, y2), wireframeColor(run));
        }
        RenderSystem.disableBlend();
    }

    private int wireframeColor(
            ControllerCollisionProjection.LineRun run
    ) {
        int band = Mth.clamp(run.depthBand(), 0, 4);
        boolean cannon = run.category()
                == ControllerCollisionSnapshot.Category.CANNON;
        int base = cannon ? 0xFFFFFF
                : RadarConfig.client().groundRadarColor.get();
        float brightness = cannon ? CANNON_BRIGHTNESS[band]
                : ENVIRONMENT_BRIGHTNESS[band];
        int alpha = cannon ? CANNON_ALPHA : ENVIRONMENT_ALPHA;
        int red = Math.min(255,
                Math.round(((base >> 16) & 0xFF) * brightness));
        int green = Math.min(255,
                Math.round(((base >> 8) & 0xFF) * brightness));
        int blue = Math.min(255,
                Math.round((base & 0xFF) * brightness));
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private void renderCollisionStatus(GuiGraphics graphics,
                                       int displayLeft, int displayTop,
                                       int displaySize) {
        if (collisionSnapshot == null) {
            graphics.drawCenteredString(font,
                    Component.translatable(
                            "create_radar.controller_limits.scanning"),
                    displayLeft + displaySize / 2,
                    displayTop + displaySize / 2 - 4, 0xD0D0D0);
            return;
        }
        String statusKey = switch (collisionSnapshot.status()) {
            case OK -> null;
            case NO_CONTROLLER -> "no_controller";
            case NO_MOUNT -> "no_mount";
            case INVALID_REQUEST -> "invalid_request";
            case RATE_LIMITED -> "rate_limited";
        };
        if (statusKey != null) {
            graphics.drawCenteredString(font,
                    Component.translatable(
                            "create_radar.controller_limits." + statusKey),
                    displayLeft + displaySize / 2,
                    displayTop + 8, 0xFFD080);
        }
        if (collisionSnapshot.spanClipped()) {
            drawClippedEdges(graphics, displayLeft, displayTop, displaySize);
        }
        if (collisionSnapshot.spanClipped()
                || collisionSnapshot.scanTruncated()) {
            String key = collisionSnapshot.scanTruncated()
                    ? "scan_truncated" : "view_clipped";
            graphics.drawCenteredString(font,
                    Component.translatable(
                            "create_radar.controller_limits." + key),
                    displayLeft + displaySize / 2,
                    displayTop + displaySize - 12, 0xFFB060);
        }
    }

    private void drawClippedEdges(GuiGraphics graphics, int x, int y,
                                  int size) {
        int marker = Math.max(4, size / 24);
        int color = 0xFFFF8040;
        graphics.fill(x, y, x + marker, y + 2, color);
        graphics.fill(x, y, x + 2, y + marker, color);
        graphics.fill(x + size - marker, y, x + size, y + 2, color);
        graphics.fill(x + size - 2, y, x + size, y + marker, color);
        graphics.fill(x, y + size - 2, x + marker, y + size, color);
        graphics.fill(x, y + size - marker, x + 2, y + size, color);
        graphics.fill(x + size - marker, y + size - 2,
                x + size, y + size, color);
        graphics.fill(x + size - 2, y + size - marker,
                x + size, y + size, color);
    }

    private void renderLimitDial(
            GuiGraphics graphics, int displayLeft, int displayTop,
            int displaySize
    ) {
        DialLayout layout = dialLayout(displayLeft, displayTop, displaySize);
        RenderSystem.enableBlend();
        drawArc(graphics, layout, layout.radius(), -180.0, 180.0,
                DIAL_DIM_COLOR);
        drawArc(graphics, layout, layout.radius(), draftMinDegrees,
                draftMaxDegrees, DIAL_ALLOWED_COLOR);

        ControllerLimitDialMath.Point lowerDirection =
                ControllerLimitDialMath.direction(dialAxis,
                        draftMinDegrees, dialZeroDegrees);
        ControllerLimitDialMath.Point upperDirection =
                ControllerLimitDialMath.direction(dialAxis,
                        draftMaxDegrees, dialZeroDegrees);
        int lowerX = endpointX(layout, lowerDirection,
                layout.lowerRadius());
        int lowerY = endpointY(layout, lowerDirection,
                layout.lowerRadius());
        int upperX = endpointX(layout, upperDirection,
                layout.upperRadius());
        int upperY = endpointY(layout, upperDirection,
                layout.upperRadius());

        drawLine(graphics, layout.centerX(), layout.centerY(),
                lowerX, lowerY, LOWER_ARM_COLOR);
        drawLine(graphics, layout.centerX(), layout.centerY(),
                upperX, upperY, UPPER_ARM_COLOR);
        drawHandle(graphics, lowerX, lowerY, layout.handleRadius(),
                LOWER_ARM_COLOR);
        drawHandle(graphics, upperX, upperY, layout.handleRadius(),
                UPPER_ARM_COLOR);
        drawLimitLabel(graphics, layout, displayLeft, displayTop,
                displaySize, lowerDirection, layout.radius() + 13,
                "create_radar.controller_limits.lower_value",
                draftMinDegrees, LOWER_ARM_COLOR);
        drawLimitLabel(graphics, layout, displayLeft, displayTop,
                displaySize, upperDirection, layout.radius() + 26,
                "create_radar.controller_limits.upper_value",
                draftMaxDegrees, UPPER_ARM_COLOR);
        RenderSystem.disableBlend();
    }

    private void drawArc(
            GuiGraphics graphics, DialLayout layout, int radius,
            double startDegrees, double endDegrees, int color
    ) {
        double span = Math.max(0.0, endDegrees - startDegrees);
        int segments = Math.max(1, (int) Math.ceil(span));
        ControllerLimitDialMath.Point previous =
                ControllerLimitDialMath.direction(dialAxis,
                        startDegrees, dialZeroDegrees);
        int previousX = endpointX(layout, previous, radius);
        int previousY = endpointY(layout, previous, radius);
        for (int index = 1; index <= segments; index++) {
            double progress = index / (double) segments;
            double degrees = startDegrees + span * progress;
            ControllerLimitDialMath.Point next =
                    ControllerLimitDialMath.direction(dialAxis, degrees,
                            dialZeroDegrees);
            int nextX = endpointX(layout, next, radius);
            int nextY = endpointY(layout, next, radius);
            drawLine(graphics, previousX, previousY, nextX, nextY, color);
            previousX = nextX;
            previousY = nextY;
        }
    }

    private void drawLimitLabel(
            GuiGraphics graphics, DialLayout layout,
            int displayLeft, int displayTop, int displaySize,
            ControllerLimitDialMath.Point direction, int radius,
            String translationKey, double degrees, int color
    ) {
        Component label = Component.translatable(translationKey,
                String.format(Locale.ROOT, "%+.1f\u00b0", degrees));
        int labelX = endpointX(layout, direction, radius)
                - font.width(label) / 2;
        int labelY = endpointY(layout, direction, radius) - 4;
        labelX = Mth.clamp(labelX, displayLeft + 2,
                displayLeft + displaySize - font.width(label) - 2);
        labelY = Mth.clamp(labelY, displayTop + 2,
                displayTop + displaySize - font.lineHeight - 2);
        graphics.drawString(font, label, labelX, labelY, color, false);
    }

    private static void drawHandle(
            GuiGraphics graphics, int x, int y, int radius, int color
    ) {
        graphics.fill(x - radius, y - radius,
                x + radius + 1, y + radius + 1, color);
        if (radius > 1) {
            graphics.fill(x - radius + 1, y - radius + 1,
                    x + radius, y + radius, 0xFF101010);
        }
    }

    private static void drawLine(
            GuiGraphics graphics, int startX, int startY,
            int endX, int endY, int color
    ) {
        int x = startX;
        int y = startY;
        int deltaX = Math.abs(endX - startX);
        int stepX = startX < endX ? 1 : -1;
        int deltaY = -Math.abs(endY - startY);
        int stepY = startY < endY ? 1 : -1;
        int error = deltaX + deltaY;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == endX && y == endY) {
                break;
            }
            int doubledError = error * 2;
            if (doubledError >= deltaY) {
                error += deltaY;
                x += stepX;
            }
            if (doubledError <= deltaX) {
                error += deltaX;
                y += stepY;
            }
        }
    }

    private static int endpointX(
            DialLayout layout, ControllerLimitDialMath.Point direction,
            int radius
    ) {
        return layout.centerX() + (int) Math.round(direction.x() * radius);
    }

    private static int endpointY(
            DialLayout layout, ControllerLimitDialMath.Point direction,
            int radius
    ) {
        return layout.centerY() + (int) Math.round(direction.y() * radius);
    }

    private DialLayout dialLayout(
            int displayLeft, int displayTop, int displaySize
    ) {
        double halfSpan = collisionSnapshot == null
                ? ControllerCollisionSnapshot.DEFAULT_HALF_SPAN
                : collisionSnapshot.halfSpan();
        int drawableSize = Math.max(0, displaySize - 1);
        int centerX = displayLeft + (int) Math.round(
                (0.5 + dialCenterU / (halfSpan * 2.0)) * drawableSize);
        int centerY = displayTop + (int) Math.round(
                (0.5 - dialCenterV / (halfSpan * 2.0)) * drawableSize);
        centerX = Mth.clamp(centerX, displayLeft,
                displayLeft + drawableSize);
        centerY = Mth.clamp(centerY, displayTop,
                displayTop + drawableSize);
        int radius = Math.max(24, Math.round(displaySize * 0.34f));
        int edgeClearance = Math.min(
                Math.min(centerX - displayLeft,
                        displayLeft + drawableSize - centerX),
                Math.min(centerY - displayTop,
                        displayTop + drawableSize - centerY));
        radius = Math.min(radius, Math.max(8, edgeClearance - 30));
        int radialOffset = Math.max(3, displaySize / 120);
        int handleRadius = Math.max(2, displaySize / 180);
        int hitRadius = Math.max(7, handleRadius + 4);
        return new DialLayout(centerX, centerY, radius,
                radius - radialOffset, radius + radialOffset,
                handleRadius, hitRadius);
    }

    private void layoutPanel() {
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = minecraft.getWindow().getGuiScale();
        if (guiScale <= 0.0) {
            guiScale = 1.0;
        }
        int desired = (int) Math.round(TARGET_UI_PIXELS / guiScale);
        int maximum = Math.max(120, Math.min(width - 20, height - 20));
        uiSize = Mth.clamp(desired, 120, maximum);
        left = (width - uiSize) / 2;
        top = (height - uiSize) / 2;
    }

    private int collisionDisplayMargin() {
        return Math.max(6,
                Math.round(DISPLAY_MARGIN * (uiSize / (float) DESIGN_SIZE)));
    }

    private int collisionDisplaySize() {
        return Math.max(1, uiSize - collisionDisplayMargin() * 2);
    }

    private void addProjectionToggleButton() {
        float buttonScale = Math.max(2.0f / 3.0f,
                uiSize / (float) DESIGN_SIZE);
        int buttonSize = Math.round(15.0f * buttonScale);
        int inset = Math.max(2, Math.round(4.0f * buttonScale));
        int x = left + collisionDisplayMargin() + collisionDisplaySize()
                - buttonSize - inset;
        int y = top + collisionDisplayMargin() + inset;
        projectionToggleButton = new MonitorButton(
                x, y, buttonScale, Component.empty(),
                button -> toggleEnvironmentProjection());
        refreshProjectionToggleButton();
        addRenderableWidget(projectionToggleButton);
    }

    private void addApplyButton() {
        float scale = Math.max(2.0f / 3.0f,
                uiSize / (float) DESIGN_SIZE);
        int buttonWidth = Math.max(48, Math.round(58.0f * scale));
        int buttonHeight = Math.max(16, Math.round(20.0f * scale));
        int inset = Math.max(3, Math.round(8.0f * scale));
        int displayRight = left + collisionDisplayMargin()
                + collisionDisplaySize();
        int displayBottom = top + collisionDisplayMargin()
                + collisionDisplaySize();
        applyButton = Button.builder(
                        Component.translatable(
                                "create_radar.controller_limits.button.apply"),
                        button -> applyDraftLimits())
                .bounds(displayRight - buttonWidth - inset,
                        displayBottom - buttonHeight - inset,
                        buttonWidth, buttonHeight)
                .build();
        refreshApplyButton();
        addRenderableWidget(applyButton);
    }

    private void applyDraftLimits() {
        if (!dialReady || !limitsDirty
                || ControllerMovementLimits.validated(dialAxis,
                draftMinDegrees, draftMaxDegrees).isEmpty()
                || draftMinDegrees < supportedMinDegrees
                || draftMaxDegrees > supportedMaxDegrees) {
            return;
        }
        submitLimits(draftMinDegrees, draftMaxDegrees);
        savedMinDegrees = draftMinDegrees;
        savedMaxDegrees = draftMaxDegrees;
        limitsDirty = false;
        refreshApplyButton();
    }

    private void refreshApplyButton() {
        if (applyButton != null) {
            applyButton.active = dialReady && limitsDirty;
        }
    }

    private void toggleEnvironmentProjection() {
        showEnvironmentProjection = !showEnvironmentProjection;
        RadarConfig.client().showControllerSurroundings.set(
                showEnvironmentProjection);
        if (RadarConfig.client().specification != null
                && RadarConfig.client().specification.isLoaded()) {
            RadarConfig.client().specification.save();
        }
        refreshProjectionToggleButton();
        rebuildCollisionProjection();
    }

    private void refreshProjectionToggleButton() {
        if (projectionToggleButton == null) {
            return;
        }
        Component action = Component.translatable(
                showEnvironmentProjection
                        ? "create_radar.controller_limits.button.hide_surroundings"
                        : "create_radar.controller_limits.button.show_surroundings");
        projectionToggleButton.setMessage(action);
        projectionToggleButton.setTooltip(Tooltip.create(action));
    }

    private void rebuildCollisionProjection() {
        collisionProjection = collisionSnapshot == null
                || collisionSnapshot.boxes().isEmpty() ? null
                : ControllerCollisionProjection.project(collisionSnapshot,
                collisionDisplaySize(), category -> showEnvironmentProjection
                        || category
                        == ControllerCollisionSnapshot.Category.CANNON);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || !dialReady) {
            return false;
        }
        DialLayout layout = dialLayout(
                left + collisionDisplayMargin(),
                top + collisionDisplayMargin(), collisionDisplaySize());
        draggedHandle = hitHandle(layout, mouseX, mouseY);
        if (draggedHandle == null) {
            return false;
        }
        updateDraggedLimit(layout, mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX, double mouseY, int button,
            double dragX, double dragY
    ) {
        if (button == 0 && draggedHandle != null) {
            DialLayout layout = dialLayout(
                    left + collisionDisplayMargin(),
                    top + collisionDisplayMargin(), collisionDisplaySize());
            updateDraggedLimit(layout, mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggedHandle != null) {
            draggedHandle = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        applyDraftLimits();
        super.removed();
    }

    private ControllerLimitDialMath.Handle hitHandle(
            DialLayout layout, double mouseX, double mouseY
    ) {
        ControllerLimitDialMath.Point lowerDirection =
                ControllerLimitDialMath.direction(dialAxis,
                        draftMinDegrees, dialZeroDegrees);
        ControllerLimitDialMath.Point upperDirection =
                ControllerLimitDialMath.direction(dialAxis,
                        draftMaxDegrees, dialZeroDegrees);
        double lowerDistance = squaredDistance(mouseX, mouseY,
                endpointX(layout, lowerDirection, layout.lowerRadius()),
                endpointY(layout, lowerDirection, layout.lowerRadius()));
        double upperDistance = squaredDistance(mouseX, mouseY,
                endpointX(layout, upperDirection, layout.upperRadius()),
                endpointY(layout, upperDirection, layout.upperRadius()));
        double maximum = layout.hitRadius() * layout.hitRadius();
        if (lowerDistance > maximum && upperDistance > maximum) {
            return null;
        }
        return lowerDistance <= upperDistance
                ? ControllerLimitDialMath.Handle.LOWER
                : ControllerLimitDialMath.Handle.UPPER;
    }

    private void updateDraggedLimit(
            DialLayout layout, double mouseX, double mouseY
    ) {
        double current = draggedHandle == ControllerLimitDialMath.Handle.LOWER
                ? draftMinDegrees : draftMaxDegrees;
        double value = ControllerLimitDialMath.draggedValue(dialAxis,
                draggedHandle, mouseX - layout.centerX(),
                mouseY - layout.centerY(), dialZeroDegrees, current,
                draftMinDegrees, draftMaxDegrees,
                supportedMinDegrees, supportedMaxDegrees);
        if (draggedHandle == ControllerLimitDialMath.Handle.LOWER) {
            draftMinDegrees = value;
        } else {
            draftMaxDegrees = value;
        }
        limitsDirty = Math.abs(draftMinDegrees - savedMinDegrees) > 1.0e-7
                || Math.abs(draftMaxDegrees - savedMaxDegrees) > 1.0e-7;
        refreshApplyButton();
    }

    private static double squaredDistance(
            double x1, double y1, double x2, double y2
    ) {
        double deltaX = x1 - x2;
        double deltaY = y1 - y2;
        return deltaX * deltaX + deltaY * deltaY;
    }

    private record DialLayout(
            int centerX, int centerY, int radius,
            int lowerRadius, int upperRadius,
            int handleRadius, int hitRadius
    ) {
    }
}
