package com.happysg.radar.api.datalink;

import com.happysg.radar.api.network.RadarNetworkApi;
import com.happysg.radar.api.network.RadarNetworkView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-side context supplied to custom Data Link interactions.
 */
public record RadarDataLinkContext(
        ServerLevel level,
        Player player,
        ItemStack dataLinkStack,
        BlockPos selectedNetworkControllerPos,
        BlockPos clickedPos,
        BlockPos placementPos,
        Direction clickedFace,
        Vec3 clickLocation
) {

    public RadarDataLinkContext {
        level = Objects.requireNonNull(level, "level");
        player = Objects.requireNonNull(player, "player");
        dataLinkStack = Objects.requireNonNull(dataLinkStack, "dataLinkStack");

        selectedNetworkControllerPos = Objects.requireNonNull(selectedNetworkControllerPos, "selectedNetworkControllerPos").immutable();

        clickedPos = Objects.requireNonNull(clickedPos, "clickedPos").immutable();
        placementPos = Objects.requireNonNull(placementPos, "placementPos").immutable();

        clickedFace = Objects.requireNonNull(clickedFace, "clickedFace");
        clickLocation = Objects.requireNonNull(clickLocation, "clickLocation");
    }

    public Optional<RadarNetworkView> selectedNetwork() {
        return RadarNetworkApi.getNetworkView(level, selectedNetworkControllerPos);
    }

    public void consumeDataLinkItem() {
        player.awardStat(Stats.ITEM_USED.get(dataLinkStack.getItem()));

        level.gameEvent(player, GameEvent.BLOCK_PLACE, clickedPos);

        if (!player.getAbilities().instabuild) {
            dataLinkStack.shrink(1);
        }
    }

    public void sendMessage(Component message) {
        if (message != null) {
            player.displayClientMessage(message, true);
        }
    }

    public void sendSuccess(Component message) {
        sendMessage(message);
    }

    public void sendFailure(Component message) {
        sendMessage(message);
    }
}