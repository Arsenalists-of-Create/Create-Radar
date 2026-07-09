package com.happysg.radar.block.controller.id;

import com.happysg.radar.compat.vs2.SableUtils;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
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
            displayScreen(ship, pPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @OnlyIn(Dist.CLIENT)
    private static void displayScreen(SubLevelAccess ship, Player player) {
        if (!(player instanceof LocalPlayer))
            return;
        ScreenOpener.open(new IDBlockScreen(ship));
    }

    public static void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
        if (pLevel.isClientSide || pMovedByPiston || pState.is(pNewState.getBlock())) {
            return;
        }
        if (!(pLevel instanceof ServerLevel serverLevel)) {
            return;
        }

        SubLevelAccess ship = SableUtils.getShipManagingPos(pLevel, pPos);
        if (ship == null) {
            return;
        }

        removeIdRecordsForConnectedChain(serverLevel, ship.getUniqueId());
    }

    private static void removeIdRecordsForConnectedChain(ServerLevel level, UUID sourceId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            removeIdRecord(sourceId);
            return;
        }

        SubLevel source = container.getSubLevel(sourceId);
        if (source == null || source.boundingBox() == null) {
            removeIdRecord(sourceId);
            return;
        }

        for (SubLevel attached : SubLevelHelper.getConnectedChain(source)) {
            if (attached != null) {
                removeIdRecord(attached.getUniqueId());
            }
        }
    }

    private static void removeIdRecord(UUID shipId) {
        if (IDManager.ID_RECORDS.remove(shipId) != null) {
            IDManager.INSTANCE.setDirty();
        }
    }
}
