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
        movesStationaryControllersWithTheirAssembledMount();
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

        expect(data.relocateWeaponEndpoint(
                        Level.OVERWORLD, newController, oldController, newMount, oldMount)
                        == NetworkData.WeaponRelocationResult.UPDATED,
                "weapon relocation could not be rolled back");
        expect(group.weaponEndpoints.contains(oldController)
                        && !group.weaponEndpoints.contains(newController),
                "rollback retained the relocated controller");
        expect(group.usedWeaponMounts.contains(oldMount)
                        && !group.usedWeaponMounts.contains(newMount),
                "rollback retained the relocated mount");
        expect(oldMount.equals(data.getWeaponMountForController(
                        Level.OVERWORLD, oldController)),
                "rollback did not restore the controller-to-mount index");
        expect(oldController.equals(data.peekEndpointForDataLink(
                        Level.OVERWORLD, newDataLink)),
                "rollback did not restore the DataLink endpoint index");
    }

    private static void movesStationaryControllersWithTheirAssembledMount() {
        NetworkData data = new NetworkData();
        BlockPos filterer = new BlockPos(1, 4, 1);
        BlockPos worldYaw = new BlockPos(10, 4, 10);
        BlockPos oldPitch = new BlockPos(11, 4, 10);
        BlockPos movedPitch = new BlockPos(111, 104, 110);
        BlockPos oldMount = new BlockPos(12, 4, 10);
        BlockPos movedMount = new BlockPos(112, 104, 110);
        BlockPos yawLink = new BlockPos(10, 3, 10);
        BlockPos pitchLink = new BlockPos(11, 3, 10);

        NetworkData.Group group = data.getOrCreateGroup(
                Level.OVERWORLD, filterer);
        data.attachWeaponEndpoint(group, worldYaw, oldMount);
        data.addDataLinkToGroup(group, yawLink, worldYaw);
        data.attachWeaponEndpoint(group, oldPitch, oldMount);
        data.addDataLinkToGroup(group, pitchLink, oldPitch);

        expect(data.relocateWeaponEndpoint(
                        Level.OVERWORLD, oldPitch, movedPitch,
                        oldMount, movedMount)
                        == NetworkData.WeaponRelocationResult.UPDATED,
                "assembled pitch endpoint was not relocated");
        NetworkData.WeaponMountReferenceRelocation stationary =
                data.relocateWeaponMountReferences(
                        Level.OVERWORLD, oldMount, movedMount);
        expect(stationary.result()
                        == NetworkData.WeaponRelocationResult.UPDATED,
                "world-space yaw mapping was not relocated with its mount");
        expect(movedMount.equals(data.getWeaponMountForController(
                        Level.OVERWORLD, worldYaw)),
                "world-space yaw retained the pre-assembly mount coordinate");
        expect(movedMount.equals(data.getWeaponMountForController(
                        Level.OVERWORLD, movedPitch)),
                "moved pitch did not share the relocated yaw mount");
        expect(filterer.equals(data.getFiltererForWeaponMount(
                        Level.OVERWORLD, movedMount))
                        && data.getFiltererForWeaponMount(
                        Level.OVERWORLD, oldMount) == null,
                "stationary and moved controllers left split mount ownership");

        data.rollbackWeaponMountReferences(stationary);
        expect(data.relocateWeaponEndpoint(
                        Level.OVERWORLD, movedPitch, oldPitch,
                        movedMount, oldMount)
                        == NetworkData.WeaponRelocationResult.UPDATED,
                "assembled pitch rollback failed");
        expect(oldMount.equals(data.getWeaponMountForController(
                        Level.OVERWORLD, worldYaw))
                        && oldMount.equals(data.getWeaponMountForController(
                        Level.OVERWORLD, oldPitch)),
                "mount transaction rollback did not restore both controllers");
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
