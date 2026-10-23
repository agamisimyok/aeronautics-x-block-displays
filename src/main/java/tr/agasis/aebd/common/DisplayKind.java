package tr.agasis.aebd.common;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.joml.Vector3f;

/**
 * The flavours of vanilla display entity this mod can edit.
 *
 * <p>Each kind declares where its rendered geometry sits in display-local space. A block display
 * draws the block in {@code [0,1]^3}, an item display draws the item centred on the origin. The
 * {@linkplain #pivot() pivot} is the local point the editor treats as "the thing's centre", which is
 * what keeps rotation and scaling from sliding the display around.
 */
public enum DisplayKind {
    BLOCK(0.0f, 1.0f),
    ITEM(-0.5f, 0.5f);

    private final float min;
    private final float max;

    DisplayKind(float min, float max) {
        this.min = min;
        this.max = max;
    }

    public Vector3f localMin() {
        return new Vector3f(this.min, this.min, this.min);
    }

    public Vector3f localMax() {
        return new Vector3f(this.max, this.max, this.max);
    }

    public Vector3f pivot() {
        float mid = (this.min + this.max) * 0.5f;
        return new Vector3f(mid, mid, mid);
    }

    public EntityType<? extends Display> entityType() {
        return this == BLOCK ? EntityType.BLOCK_DISPLAY : EntityType.ITEM_DISPLAY;
    }

    public static DisplayKind byId(int id) {
        return id == BLOCK.ordinal() ? BLOCK : ITEM;
    }

    @Nullable
    public static DisplayKind of(@Nullable Entity entity) {
        if (entity instanceof Display.BlockDisplay) {
            return BLOCK;
        }
        if (entity instanceof Display.ItemDisplay) {
            return ITEM;
        }
        return null;
    }
}
