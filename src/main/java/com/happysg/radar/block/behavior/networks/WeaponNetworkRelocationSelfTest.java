package com.happysg.radar.block.behavior.networks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Deterministic checks for the position-index migration used by Sable
 * assembly. This runs without starting a Minecraft server.
 */
public final class WeaponNetworkRelocationSelfTest {
    private WeaponNetworkRelocationSelfTest() {
    }

    public static void main(String[] args) {
        movesEveryWeaponIndexAtomically();
        refusesForeignOwnershipWithoutPartialMutation();
        reportsMissingEndpointsWithoutCreatingState();
        System.out.println("PASS weapon network relocation self-test");
    }

    private static void movesEveryWeaponIndexAtomically() {
        NetworkData data = new NetworkData();
        BlockPos filterer = new BlockPos(1, 2, 3);
        BlockPos oldController = new BlockPos(10, 20, 30);
        BlockPos newController = new BlockPos(110, 120, 130);
        BlockPos oldMount = new BlockPos(11, 20, 30);
        BlockPos newMount = new BlockPos(111, 120, 130);
        BlockPos oldDataLink = new BlockPos(9, 20, 30);
        BlockPos newDataLink = new BlockPos(109, 120, 130);

        NetworkData.Group group = data.getOrCreateGroup(Level.OVERWORLD, filterer);
        data.attachWeaponEndpoint(group, oldController, oldMount);
        data.addDataLinkToGroup(group, oldDataLink, oldController);

        expect(data.relocateWeaponEndpoint(
                        Level.OVERWORLD, oldController, newController, oldMount, newMount)
                        == NetworkData.WeaponRelocationResult.UPDATED,
                "weapon relocation was not committed");
        expect(group.weaponEndpoints.contains(newController)
                        && !group.weaponEndpoints.contains(oldController),
                "weapon endpoint set retained the old controller");
        expect(group.usedWeaponMounts.contains(newMount)
                        && !group.usedWeaponMounts.contains(oldMount),
                "used mount set retained the old mount");
        expect(filterer.equals(data.getFiltererForEndpoint(Level.OVERWORLD, newController))
                        && data.getFiltererForEndpoint(Level.OVERWORLD, oldController) == null,
                "endpoint ownership index did not move");
        expect(filterer.equals(data.getFiltererForWeaponMount(Level.OVERWORLD, newMount))
                        && data.getFiltererForWeaponMount(Level.OVERWORLD, oldMount) == null,
                "mount ownership index did not move");
        expect(newMount.equals(data.getWeaponMountForController(Level.OVERWORLD, newController))
                        && data.getWeaponMountForController(Level.OVERWORLD, oldController) == null,
                "controller-to-mount index did not move");
        expect(newController.equals(data.peekEndpointForDataLink(Level.OVERWORLD, oldDataLink)),
                "DataLink endpoint index did not follow the controller");

        expect(data.updateDataLinkPosition(Level.OVERWORLD, oldDataLink, newDataLink),
                "DataLink position did not move");
        expect(filterer.equals(data.getFiltererForDataLink(Level.OVERWORLD, newDataLink))
                        && data.getFiltererForDataLink(Level.OVERWORLD, oldDataLink) == null,
                "DataLink ownership retained the old position");
        expect(newController.equals(data.peekEndpointForDataLink(Level.OVERWORLD, newDataLink)),
                "moved DataLink lost its relocated endpoint");

        expect(data.relocateWeaponEndpoint(
                        Level.OVERWORLD, oldController, newController, oldMount, newMount)
                        == NetworkData.WeaponRelocationResult.UPDATED,
                "idempotent relocation did not recognize the committed state");
    }

    private static void refusesForeignOwnershipWithoutPartialMutation() {
        NetworkData data = new NetworkData();
        BlockPos firstFilterer = new BlockPos(1, 0, 0);
        BlockPos secondFilterer = new BlockPos(2, 0, 0);
        BlockPos oldController = new BlockPos(10, 0, 0);
        BlockPos oldMount = new BlockPos(11, 0, 0);
        BlockPos claimedController = new BlockPos(20, 0, 0);
        BlockPos claimedMount = new BlockPos(21, 0, 0);

        NetworkData.Group first = data.getOrCreateGroup(Level.OVERWORLD, firstFilterer);
        NetworkData.Group second = data.getOrCreateGroup(Level.OVERWORLD, secondFilterer);
        data.attachWeaponEndpoint(first, oldController, oldMount);
        data.attachWeaponEndpoint(second, claimedController, claimedMount);

        expect(data.relocateWeaponEndpoint(
                        Level.OVERWORLD, oldController, claimedController, oldMount, claimedMount)
                        == NetworkData.WeaponRelocationResult.CONFLICT,
                "foreign-owned destination was not rejected");
        expect(first.weaponEndpoints.contains(oldController)
                        && first.usedWeaponMounts.contains(oldMount),
                "conflicting relocation partially changed the source group");
        expect(firstFilterer.equals(data.getFiltererForEndpoint(Level.OVERWORLD, oldController))
                        && firstFilterer.equals(data.getFiltererForWeaponMount(Level.OVERWORLD, oldMount)),
                "conflicting relocation partially changed source ownership");
        expect(secondFilterer.equals(data.getFiltererForEndpoint(Level.OVERWORLD, claimedController))
                        && secondFilterer.equals(data.getFiltererForWeaponMount(Level.OVERWORLD, claimedMount)),
                "conflicting relocation changed foreign ownership");
    }

    private static void reportsMissingEndpointsWithoutCreatingState() {
        NetworkData data = new NetworkData();
        expect(data.relocateWeaponEndpoint(
                        Level.OVERWORLD,
                        new BlockPos(1, 1, 1),
                        new BlockPos(2, 2, 2),
                        new BlockPos(3, 3, 3),
                        new BlockPos(4, 4, 4))
                        == NetworkData.WeaponRelocationResult.NOT_FOUND,
                "missing endpoint unexpectedly created relocation state");
    }

    private static void expect(boolean condition, String failure) {
        if (!condition) {
            throw new IllegalStateException(failure);
        }
    }
}
