package com.happysg.radar.gametest;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.WeaponFiringControl;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.firing.FireControllerBlock;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.debug.DiagnosticSnapshotBuilder;
import com.happysg.radar.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.breech.AutocannonBreechBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.breeches.quickfiring_breech.QuickfiringBreechBlockEntity;
import rbasamoyai.createbigcannons.index.CBCBlocks;
import rbasamoyai.createbigcannons.index.CBCDataComponents;
import rbasamoyai.createbigcannons.index.CBCItems;

import java.util.List;

@GameTestHolder(CreateRadar.MODID)
@PrefixGameTestTemplate(false)
public final class CannonControlGameTests {
    private CannonControlGameTests() {
    }

    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing")
    public static void autocannonPreviewSurvivesQueueDrainAndMagazineReload(GameTestHelper helper) {
        var state = CBCBlocks.CAST_IRON_AUTOCANNON_BREECH.getDefaultState();
        var breech = (AutocannonBreechBlockEntity) CBCBlocks.CAST_IRON_AUTOCANNON_BREECH
                .get().newBlockEntity(BlockPos.ZERO, state);
        var cannon = new MountedAutocannonContraption() {
            { startPos = BlockPos.ZERO; initialOrientation = Direction.NORTH; }
        };
        cannon.presentBlockEntities.put(BlockPos.ZERO, breech);
        ItemStack round = CBCItems.MACHINE_GUN_ROUND.asStack();
        ItemStack tracer = round.copy();
        tracer.set(CBCDataComponents.AUTOCANNON_TRACER, true);

        for (int i = 0; i < breech.getQueueLimit(); i++) {
            breech.getInputBuffer().addLast(i == 1 ? tracer.copy() : round.copy());
        }
        breech.extractNextInput();
        require(breech.createItemHandler().getStackInSlot(1).isEmpty(),
                "Fixture must exercise CBC's hidden partial input queue");
        require(ItemStack.isSameItemSameComponents(CannonUtil.peekAutocannonAmmo(breech), tracer),
                "Preview did not select the next queued round");
        require(CannonUtil.resolveLoadedAutocannonBallistics(cannon, helper.getLevel()) != null,
                "Partly filled autocannon lost its ballistic profile after firing");

        ItemStack magazine = CBCBlocks.AUTOCANNON_AMMO_CONTAINER.asStack();
        magazine.set(CBCDataComponents.AMMO, ItemContainerContents.fromItems(List.of(round.copyWithCount(4))));
        magazine.set(CBCDataComponents.TRACERS, ItemContainerContents.fromItems(List.of(tracer.copyWithCount(2))));
        magazine.set(CBCDataComponents.TRACER_SPACING, 1);
        breech.setMagazine(magazine);
        while (!breech.getInputBuffer().isEmpty()) breech.extractNextInput();
        for (int i = 0; i < 6; i++) {
            ItemStack before = magazine.copy();
            ItemStack preview = CannonUtil.peekAutocannonAmmo(breech);
            require(!preview.isEmpty(), "Magazine ammunition was invisible to targeting");
            require(ItemStack.matches(before, magazine), "Preview consumed ammo or advanced tracer selection");
            require(CannonUtil.resolveLoadedAutocannonBallistics(cannon, helper.getLevel()) != null,
                    "Magazine lost its ballistic profile between shots");
            require(ItemStack.matches(preview, breech.extractNextInput()), "Preview differed from CBC's next shot");
        }
        require(CannonUtil.resolveLoadedAutocannonBallistics(cannon, helper.getLevel()) == null,
                "Empty magazine retained a loaded ballistic profile");
        magazine.set(CBCDataComponents.AMMO, ItemContainerContents.fromItems(List.of(round.copyWithCount(2))));
        require(CannonUtil.resolveLoadedAutocannonBallistics(cannon, helper.getLevel()) != null,
                "Reload did not restore the ballistic profile");
        helper.succeed();
    }

    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing")
    public static void fireControllerActuallyTicksPulsesAndTimeout(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.FIRE_CONTROLLER_BLOCK.getDefaultState());
        helper.getLevel().setBlockAndUpdate(pos.above(), Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.DELAY, 2));
        var fire = (FireControllerBlockEntity) helper.getLevel().getBlockEntity(pos);
        var sequence = helper.startSequence();
        // Let placement/contact reconciliation finish before issuing commands.
        sequence.thenIdle(2).thenExecute(() -> fire.setPowered(true));
        sequence.thenIdle(2).thenExecute(() -> require(!fire.isPowered(), "Pulse never switched off"));
        sequence.thenIdle(2).thenExecute(() -> require(fire.isPowered(), "Second pulse never switched on"));
        sequence.thenIdle(9).thenExecute(() -> require(!fire.isPowered(), "Uncommanded pulse train did not time out"));
        sequence.thenExecute(() -> {
            helper.getLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
            fire.setPowered(true);
        });
        sequence.thenIdle(12).thenExecute(() -> {
            require(!fire.isPowered(), "Continuous output did not time out");
            require(!helper.getLevel().getBlockState(pos).getValue(FireControllerBlock.POWERED),
                    "Block redstone state disagrees with the timed-out controller");
        });
        sequence.thenSucceed();
    }

    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing")
    public static void openBreechBlocksTriggerUntilFullyClosed(GameTestHelper helper) {
        var state = CBCBlocks.CAST_IRON_QUICKFIRING_BREECH.getDefaultState();
        var breech = (QuickfiringBreechBlockEntity) CBCBlocks.CAST_IRON_QUICKFIRING_BREECH
                .get().newBlockEntity(BlockPos.ZERO.south(), state);
        breech.setLevel(helper.getLevel());
        var cannon = new MountedBigCannonContraption() {
            { startPos = BlockPos.ZERO; initialOrientation = Direction.NORTH; }
        };
        cannon.presentBlockEntities.put(BlockPos.ZERO.south(), breech);
        cannon.bounds = new AABB(0, 0, 0, 1, 1, 2);
        var entity = PitchOrientedContraptionEntity.create(helper.getLevel(), cannon, Direction.NORTH, false);
        var mount = new CannonMountBlockEntity(CBCBlocks.CANNON_MOUNT.get()
                .newBlockEntity(BlockPos.ZERO, CBCBlocks.CANNON_MOUNT.getDefaultState()).getType(),
                BlockPos.ZERO, CBCBlocks.CANNON_MOUNT.getDefaultState()) {
            @Override
            public PitchOrientedContraptionEntity getContraption() { return entity; }
        };
        require(CannonUtil.isCannonReadyToFire(mount), "Closed breech should allow a trigger");
        breech.toggleOpening();
        for (int i = 0; i <= QuickfiringBreechBlockEntity.getOpeningTime(); i++) breech.tickAnimation();
        require(breech.getOpenProgress() > 0, "Fixture breech did not open");
        require(!CannonUtil.isCannonReadyToFire(mount), "Open breech consumed the firing edge");
        breech.toggleOpening();
        for (int i = 0; i <= QuickfiringBreechBlockEntity.getOpeningTime()
                && breech.getOpenProgress() > 0; i++) {
            require(!CannonUtil.isCannonReadyToFire(mount), "Closing breech allowed a premature trigger");
            breech.tickAnimation();
        }
        require(breech.getOpenProgress() == 0, "Fixture breech did not close");
        require(CannonUtil.isCannonReadyToFire(mount), "Closed breech did not recover without reselection");
        helper.succeed();
    }

    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing")
    public static void retryPreservesRadarAndBinocularDesignation(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlockAndUpdate(pos, CBCBlocks.CANNON_MOUNT.getDefaultState());
        var mount = (CannonMountBlockEntity) helper.getLevel().getBlockEntity(pos);
        var control = new WeaponFiringControl(null, CannonMountContext.of(mount), null);
        RadarTrack track = new RadarTrack("test-target", new Vec3(20, 5, 20), Vec3.ZERO,
                1, TrackCategory.MISC, "misc", 1);
        control.setTarget(track.position(), TargetingConfig.DEFAULT, track, null);
        control.resetAimForRetry();
        require("radar track".equals(mode(helper, control, pos)), "Retry lost the radar designation");
        control.setBinoTarget(pos.north(20), TargetingConfig.DEFAULT, null, false);
        control.resetAimForRetry();
        require("binocular".equals(mode(helper, control, pos)), "Retry lost the binocular designation");
        control.resetTarget();
        require("idle".equals(mode(helper, control, pos)), "Explicit clear retained a designation");
        helper.succeed();
    }

    private static String mode(GameTestHelper helper, WeaponFiringControl control, BlockPos pos) {
        var builder = new DiagnosticSnapshotBuilder(helper.getLevel(), pos,
                ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "test"), "test");
        control.appendDiagnosticInfo(builder);
        return builder.build().sections().stream().flatMap(section -> section.entries().stream())
                .filter(entry -> entry.key().equals("mode")).findFirst().orElseThrow().value();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
