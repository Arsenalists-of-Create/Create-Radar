package com.happysg.radar.block.controller.id;

import com.happysg.radar.compat.sable.SableDataLinkRelocation;
import com.happysg.radar.compat.vs2.SableUtils;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

// Done to avoid loading vs2 classes when the mod is not loaded
public class VS2IDHandler {

    public static @NotNull InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, @NotNull Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        SubLevelAccess ship = SableUtils.getShipManagingPos(pLevel, pPos);
        if (ship == null) {
            pPlayer.displayClientMessage(Component.translatable("create_radar.id_block.not_on_ship"), true);
            return InteractionResult.PASS;
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            displayScreen(ship, pPos, pPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @OnlyIn(Dist.CLIENT)
    private static void displayScreen(SubLevelAccess ship, BlockPos transponderPos, Player player) {
        if (!(player instanceof LocalPlayer))
            return;
        ScreenOpener.open(new IDBlockScreen(ship, transponderPos));
    }

    public static void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
        if (pLevel.isClientSide || pMovedByPiston || pState.is(pNewState.getBlock())) {
            return;
        }

        if (!(pLevel instanceof ServerLevel serverLevel)) {
            return;
        }

        if (SableDataLinkRelocation.isRelocatingEndpoint(serverLevel, pPos)) {
            return;
        }

        SubLevelAccess ship = SableUtils.getShipManagingPos(pLevel, pPos);
        if (ship == null) {
            return;
        }

        UUID shipId = ship.getUniqueId();
        IDManager.unregisterTransponder(shipId, pPos);
    }
}
