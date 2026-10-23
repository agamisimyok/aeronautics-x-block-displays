package tr.agasis.aebd.client.screen;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tr.agasis.aebd.client.ClientDisplays;
import tr.agasis.aebd.client.EditorSession;
import tr.agasis.aebd.common.DisplayProps;
import tr.agasis.aebd.common.DisplayXform;

/**
 * Numeric editor for the current selection.
 *
 * <p>Rotation, scale and the property fields are written as absolute values to every selected
 * display. The centre is applied as an offset from the first selected display's centre, so a
 * multi-selection moves as one piece instead of collapsing onto a single point.
 */
public class DisplayPropsScreen extends Screen {

    private static final int PANEL_WIDTH = 252;
    private static final int BOX_WIDTH = 78;
    private static final int BOX_HEIGHT = 16;
    private static final int GAP = 6;
    private static final int ROW_HEIGHT = 30;

    private final EditorSession session = EditorSession.INSTANCE;

    private DisplayXform baseXform = DisplayXform.atCenter(Vec3.ZERO);
    private DisplayProps baseProps = DisplayProps.DEFAULT;
    private byte billboard = DisplayProps.DEFAULT.billboard();
    private byte itemTransform = DisplayProps.DEFAULT.itemTransform();

    private EditBox centerX;
    private EditBox centerY;
    private EditBox centerZ;
    private EditBox rotateX;
    private EditBox rotateY;
    private EditBox rotateZ;
    private EditBox scaleX;
    private EditBox scaleY;
    private EditBox scaleZ;
    private EditBox glow;
    private EditBox brightnessBlock;
    private EditBox brightnessSky;
    private EditBox viewRange;
    private EditBox shadowRadius;
    private EditBox shadowStrength;

    private int panelLeft;
    private int panelTop;

    public DisplayPropsScreen() {
        super(Component.translatable("aebd.screen.title"));
    }

    @Override
    protected void init() {
        var selected = this.session.currentXforms();
        if (!selected.isEmpty()) {
            this.baseXform = selected.values().iterator().next();
        }
        this.baseProps = currentProps();
        this.billboard = this.baseProps.billboard();
        this.itemTransform = this.baseProps.itemTransform();

        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = Math.max(16, this.height / 2 - 120);

        Vector3f euler = new Quaternionf(this.baseXform.left()).getEulerAnglesXYZ(new Vector3f());
        Vec3 center = this.baseXform.center();
        Vector3f scale = this.baseXform.scale();

        int row = this.panelTop + 24;
        this.centerX = box(0, row, number(center.x));
        this.centerY = box(1, row, number(center.y));
        this.centerZ = box(2, row, number(center.z));

        row += ROW_HEIGHT;
        this.rotateX = box(0, row, number(Math.toDegrees(euler.x)));
        this.rotateY = box(1, row, number(Math.toDegrees(euler.y)));
        this.rotateZ = box(2, row, number(Math.toDegrees(euler.z)));

        row += ROW_HEIGHT;
        this.scaleX = box(0, row, number(scale.x));
        this.scaleY = box(1, row, number(scale.y));
        this.scaleZ = box(2, row, number(scale.z));

        row += ROW_HEIGHT;
        this.glow = box(0, row, String.valueOf(this.baseProps.glowColor()));
        this.brightnessBlock = box(1, row, String.valueOf(this.baseProps.brightnessBlock()));
        this.brightnessSky = box(2, row, String.valueOf(this.baseProps.brightnessSky()));

        row += ROW_HEIGHT;
        this.viewRange = box(0, row, number(this.baseProps.viewRange()));
        this.shadowRadius = box(1, row, number(this.baseProps.shadowRadius()));
        this.shadowStrength = box(2, row, number(this.baseProps.shadowStrength()));

        row += ROW_HEIGHT;
        int halfWidth = (PANEL_WIDTH - GAP) / 2;
        addRenderableWidget(Button.builder(billboardLabel(), button -> {
            this.billboard = (byte) ((this.billboard + 1) % Display.BillboardConstraints.values().length);
            button.setMessage(billboardLabel());
        }).bounds(this.panelLeft, row, halfWidth, 20).build());
        addRenderableWidget(Button.builder(itemTransformLabel(), button -> {
            this.itemTransform = (byte) ((this.itemTransform + 1) % ItemDisplayContext.values().length);
            button.setMessage(itemTransformLabel());
        }).bounds(this.panelLeft + halfWidth + GAP, row, halfWidth, 20).build());

        row += 26;
        addRenderableWidget(Button.builder(Component.translatable("aebd.screen.apply"), button -> apply())
                .bounds(this.panelLeft, row, halfWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.panelLeft + halfWidth + GAP, row, halfWidth, 20).build());
    }

    private DisplayProps currentProps() {
        for (int id : this.session.selection()) {
            Display display = this.minecraft == null || this.minecraft.level == null
                    ? null
                    : ClientDisplays.displayById(this.minecraft.level, id);
            if (display != null) {
                return ClientDisplays.propsOf(display);
            }
        }
        return DisplayProps.DEFAULT;
    }

    private EditBox box(int column, int y, String value) {
        EditBox editBox = new EditBox(this.font,
                this.panelLeft + column * (BOX_WIDTH + GAP), y, BOX_WIDTH, BOX_HEIGHT, Component.empty());
        editBox.setMaxLength(24);
        editBox.setValue(value);
        return addRenderableWidget(editBox);
    }

    private void apply() {
        Vec3 newCenter = new Vec3(
                parse(this.centerX, this.baseXform.center().x),
                parse(this.centerY, this.baseXform.center().y),
                parse(this.centerZ, this.baseXform.center().z));
        Vec3 offset = newCenter.subtract(this.baseXform.center());

        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(parse(this.rotateX, 0.0)),
                (float) Math.toRadians(parse(this.rotateY, 0.0)),
                (float) Math.toRadians(parse(this.rotateZ, 0.0)));
        Vector3f scale = new Vector3f(
                (float) parse(this.scaleX, 1.0),
                (float) parse(this.scaleY, 1.0),
                (float) parse(this.scaleZ, 1.0));

        this.session.applyXformToSelection(xform -> new DisplayXform(
                xform.center().add(offset), new Quaternionf(rotation), new Vector3f(scale), xform.right()));

        this.session.applyProps(new DisplayProps(
                this.billboard,
                (int) parse(this.brightnessBlock, -1.0),
                (int) parse(this.brightnessSky, -1.0),
                (float) parse(this.viewRange, this.baseProps.viewRange()),
                (float) parse(this.shadowRadius, this.baseProps.shadowRadius()),
                (float) parse(this.shadowStrength, this.baseProps.shadowStrength()),
                (int) parse(this.glow, -1.0),
                this.itemTransform));

        // Re-read so the fields show what actually landed rather than drifting on repeated applies.
        this.baseXform = new DisplayXform(newCenter, rotation, scale, this.baseXform.right());
    }

    private static double parse(@Nullable EditBox box, double fallback) {
        if (box == null) {
            return fallback;
        }
        try {
            String text = box.getValue().trim();
            return text.isEmpty() ? fallback : Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E7) {
            return String.valueOf((long) value);
        }
        return String.format("%.4f", value);
    }

    private Component billboardLabel() {
        Display.BillboardConstraints[] values = Display.BillboardConstraints.values();
        return Component.translatable("aebd.screen.billboard",
                values[Math.floorMod(this.billboard, values.length)].getSerializedName());
    }

    private Component itemTransformLabel() {
        ItemDisplayContext[] values = ItemDisplayContext.values();
        return Component.translatable("aebd.screen.item_transform",
                values[Math.floorMod(this.itemTransform, values.length)].getSerializedName());
    }

    /** Keeps the world visible and running so edits can be judged while the panel is open. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.panelLeft - 8, this.panelTop - 8,
                this.panelLeft + PANEL_WIDTH + 8, this.panelTop + 200, 0xC0101010);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.panelLeft, this.panelTop, 0x4FC3F7);
        int row = this.panelTop + 24;
        label(graphics, "aebd.screen.center", row);
        label(graphics, "aebd.screen.rotation", row + ROW_HEIGHT);
        label(graphics, "aebd.screen.scale", row + ROW_HEIGHT * 2);
        label(graphics, "aebd.screen.light", row + ROW_HEIGHT * 3);
        label(graphics, "aebd.screen.view_shadow", row + ROW_HEIGHT * 4);
    }

    private void label(GuiGraphics graphics, String key, int boxY) {
        graphics.drawString(this.font, Component.translatable(key), this.panelLeft, boxY - 11, 0xA0A0A0);
    }
}
