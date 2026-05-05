package com.happysg.radar.block.controller.id;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.aeronautics.AeronauticsUtils;
import com.happysg.radar.compat.vs2.VS2Utils;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.core.api.ships.Ship;
import java.util.UUID;

public class IdentificationHandler {
    public static @NotNull InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, @NotNull Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        long targetId = -1;
        String targetSlug = "";

        if (Mods.VALKYRIENSKIES.isLoaded()) {
            Ship ship = VS2Utils.getShipManagingPos(pLevel, pPos);
            if (ship != null) {
                targetId = ship.getId();
                targetSlug = ship.getSlug();
            }
        }

        if (targetId == -1 && Mods.SABLE.isLoaded()) {
            dev.ryanhcode.sable.sublevel.SubLevel subLevel = (dev.ryanhcode.sable.sublevel.SubLevel) AeronauticsUtils.getShipManagingPos(pLevel, pPos);
            if (subLevel != null) {
                targetId = subLevel.getUniqueId().getMostSignificantBits();
                targetSlug = AeronauticsUtils.getShipNamespace(subLevel);
            }
        }

        if (targetId == -1) {
            pPlayer.displayClientMessage(Component.translatable("create_radar.id_block.not_on_ship"), true);
            return InteractionResult.PASS;
        }

        if (pLevel.isClientSide) {
            displayScreen(targetId, targetSlug, pPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @OnlyIn(Dist.CLIENT)
    private static void displayScreen(long targetId, String targetSlug, Player player) {
        if (!(player instanceof LocalPlayer))
            return;
        ScreenOpener.open(new IDBlockScreen(targetId, targetSlug));
    }

    public static void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
        long targetId = -1;
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            Ship ship = VS2Utils.getShipManagingPos(pLevel, pPos);
            if (ship != null) targetId = ship.getId();
        }
        if (targetId == -1 && Mods.SABLE.isLoaded()) {
            dev.ryanhcode.sable.sublevel.SubLevel subLevel = (dev.ryanhcode.sable.sublevel.SubLevel) AeronauticsUtils.getShipManagingPos(pLevel, pPos);
            if (subLevel != null) targetId = subLevel.getUniqueId().getMostSignificantBits();
        }

        if (targetId != -1) {
            IDManager.removeIDRecord(targetId);
        }
    }
}