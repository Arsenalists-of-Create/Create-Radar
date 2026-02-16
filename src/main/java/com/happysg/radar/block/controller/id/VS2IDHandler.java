package com.happysg.radar.block.controller.id;

import com.happysg.radar.compat.vs2.VS2Utils;
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
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.core.api.ships.Ship;

// Done to avoid loading vs2 classes when the mod is not loaded
public class VS2IDHandler {

    public static InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, @NotNull Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        Ship ship = VS2Utils.getShipManagingPos(pLevel, pPos);
        if (ship == null) {
            pPlayer.displayClientMessage(Component.translatable("create_radar.id_block.not_on_ship"), true);
            return InteractionResult.PASS;
        }

        if (FMLLoader.getDist() == Dist.CLIENT) {
            displayScreen(ship, pPlayer);
        }
        return InteractionResult.SUCCESS;

    }

    @OnlyIn(Dist.CLIENT)
    private static void displayScreen(Ship ship, Player player) {
        if (!(player instanceof LocalPlayer))
            return;
        ScreenOpener.open(new IDBlockScreen(ship));
    }

    public static void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
        Ship ship = VS2Utils.getShipManagingPos(pLevel, pPos);
        if (ship != null) {
            // uses ship.getId() internally now
            IDManager.removeIDRecord(ship);
        }
    }
}
