package tr.agasis.aebd.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import tr.agasis.aebd.common.DisplayOps;
import tr.agasis.aebd.registry.AEBDComponents;

/**
 * The editor wand. Sneak + right-click arms it; sneaking and right-clicking again disarms it. While
 * armed, the client takes over left- and right-click so the usual break/place behaviour stays out of
 * the way.
 */
public class DisplayEditorItem extends Item {

    public DisplayEditorItem(Properties properties) {
        super(properties);
    }

    public static boolean isEditing(ItemStack stack) {
        return stack.getItem() instanceof DisplayEditorItem
                && Boolean.TRUE.equals(stack.get(AEBDComponents.EDITING.get()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        toggle(level, player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        toggle(context.getLevel(), player, context.getItemInHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /**
     * Only the server flips the flag; the change reaches the client through the normal inventory
     * sync. Toggling on both sides would race and leave the mode inverted.
     */
    private static void toggle(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        if (!DisplayOps.canEdit(player)) {
            player.displayClientMessage(Component.translatable("aebd.message.no_permission"), true);
            return;
        }
        boolean editing = !isEditing(stack);
        stack.set(AEBDComponents.EDITING.get(), editing);
        level.playSound(null, player.blockPosition(),
                editing ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.4f, editing ? 1.6f : 1.2f);
        player.displayClientMessage(Component.translatable(
                editing ? "aebd.message.editing_on" : "aebd.message.editing_off"), true);
    }

    /** Stops armed left-clicks from chewing through the build while selecting displays. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !isEditing(player.getMainHandItem());
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(isEditing(stack) ? "aebd.tooltip.armed" : "aebd.tooltip.idle")
                .withStyle(isEditing(stack) ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("aebd.tooltip.toggle").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("aebd.tooltip.palette").withStyle(ChatFormatting.DARK_GRAY));
    }
}
