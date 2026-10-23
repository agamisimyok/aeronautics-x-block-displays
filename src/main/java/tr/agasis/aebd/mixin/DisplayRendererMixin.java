package tr.agasis.aebd.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.agasis.aebd.client.ClientAttachmentManager;

/** Applies the sub-level render matrix without mutating or teleporting the client entity. */
@Mixin(DisplayRenderer.class)
public abstract class DisplayRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void aebd$applySubLevelRenderPose(Display display, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo callback) {
        ClientAttachmentManager.applyRenderTransform(display, partialTick, poseStack);
    }
}
