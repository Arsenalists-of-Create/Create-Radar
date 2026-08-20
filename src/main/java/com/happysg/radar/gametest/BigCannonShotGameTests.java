package com.happysg.radar.gametest;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.compat.cbc.CannonUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBlock;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.index.CBCBlocks;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.PowderChargeBlock;

@GameTestHolder(CreateRadar.MODID)
public final class BigCannonShotGameTests {
    private static final BlockPos FIXTURE_CENTER =
            new BlockPos(896, 96, 8);
    private static final int FIXTURE_RADIUS = 4;

    private BigCannonShotGameTests() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void projectileBeforePropellantUsesLiveChargePower(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        ChunkPos fixtureChunk = new ChunkPos(FIXTURE_CENTER);
        boolean alreadyForced = level.getForcedChunks()
                .contains(fixtureChunk.toLong());
        if (!alreadyForced) {
            level.setChunkForced(fixtureChunk.x, fixtureChunk.z, true);
        }
        clearFixture(level);

        try {
            CannonMountBlockEntity mount = assembleTestCannon(
                    level, FIXTURE_CENTER);
            if (!(mount.getContraption().getContraption()
                    instanceof MountedBigCannonContraption cannon)) {
                throw new GameTestAssertException(
                        "CBC test cannon did not assemble as a big cannon");
            }

            Direction direction = cannon.initialOrientation();
            BlockPos projectilePos = cannon.getStartPos();
            BlockPos propellantPos = projectilePos.relative(direction);
            if (!(cannon.presentBlockEntities.get(projectilePos)
                    instanceof IBigCannonBlockEntity projectileCannonBlock)
                    || !(cannon.presentBlockEntities.get(propellantPos)
                    instanceof IBigCannonBlockEntity propellantCannonBlock)) {
                throw new GameTestAssertException(
                        "CBC test cannon is missing expected internal positions");
            }

            BlockState projectileState = CBCBlocks.SOLID_SHOT
                    .getDefaultState();
            if (projectileState.hasProperty(
                    BlockStateProperties.FACING)) {
                projectileState = projectileState.setValue(
                        BlockStateProperties.FACING, direction);
            }
            StructureTemplate.StructureBlockInfo projectileInfo =
                    new StructureTemplate.StructureBlockInfo(
                            projectilePos, projectileState, null);
            projectileCannonBlock.cannonBehavior()
                    .loadBlock(projectileInfo);

            BlockState propellantState = CBCBlocks.POWDER_CHARGE
                    .getDefaultState();
            if (propellantState.hasProperty(BlockStateProperties.AXIS)) {
                propellantState = propellantState.setValue(
                        BlockStateProperties.AXIS, direction.getAxis());
            }
            StructureTemplate.StructureBlockInfo propellantInfo =
                    new StructureTemplate.StructureBlockInfo(
                            propellantPos, propellantState, null);
            propellantCannonBlock.cannonBehavior()
                    .loadBlock(propellantInfo);

            PowderChargeBlock powderCharge =
                    CBCBlocks.POWDER_CHARGE.get();
            float livePower = Math.max(0.0F,
                    powderCharge.getChargePower(propellantInfo));
            CannonUtil.BigCannonShotState shot =
                    CannonUtil.resolveBigCannonShotState(cannon, level);
            float expectedSpeed = livePower
                    + shot.projectileAddedPower();

            require(shot.hasProjectile(),
                    "Launch preview did not resolve the loaded projectile");
            require(shot.propellantCharges() == 1,
                    "Launch preview stopped before the later powder charge");
            require(close(shot.propellantPower(), livePower),
                    "Launch preview used " + shot.propellantPower()
                            + " instead of live powder strength "
                            + livePower);
            if (shot.reason().contains("cbc_at_physics")) {
                require(shot.speed() > 0.0F,
                        "CBC:AT launch physics produced a non-positive speed");
            } else {
                require(close(shot.speed(), expectedSpeed),
                        "Launch preview speed " + shot.speed()
                                + " did not include live powder strength; expected "
                                + expectedSpeed);
            }
        } finally {
            clearFixture(level);
            if (!alreadyForced) {
                level.setChunkForced(
                        fixtureChunk.x, fixtureChunk.z, false);
            }
        }
        helper.succeed();
    }

    private static CannonMountBlockEntity assembleTestCannon(
            ServerLevel level,
            BlockPos mountPos
    ) {
        level.setBlockAndUpdate(mountPos,
                CBCBlocks.CANNON_MOUNT.getDefaultState()
                        .setValue(CannonMountBlock.HORIZONTAL_FACING,
                                Direction.NORTH));
        BlockPos barrelPos = mountPos.above(2);
        level.setBlockAndUpdate(barrelPos.south(),
                CBCBlocks.CAST_IRON_CANNON_END.getDefaultState()
                        .setValue(BlockStateProperties.FACING,
                                Direction.SOUTH));
        for (int i = 0; i < 3; i++) {
            level.setBlockAndUpdate(barrelPos.north(i),
                    CBCBlocks.CAST_IRON_CANNON_BARREL.getDefaultState()
                            .setValue(BlockStateProperties.FACING,
                                    Direction.NORTH));
        }
        BigCannonBlock.onPlace(level, barrelPos.south());
        for (int i = 0; i < 3; i++) {
            BigCannonBlock.onPlace(level, barrelPos.north(i));
        }
        if (!(level.getBlockEntity(mountPos)
                instanceof CannonMountBlockEntity mount)) {
            throw new GameTestAssertException(
                    "CBC cannon mount block entity was not created");
        }
        mount.onRedstoneUpdate(true, false, false, false, 0);
        require(mount.getContraption() != null
                        && mount.getContraption().isAlive(),
                "CBC test cannon did not assemble: "
                        + (mount.getLastAssemblyException() == null
                        ? "no assembly exception"
                        : mount.getLastAssemblyException()
                        .component.getString()));
        return mount;
    }

    private static void clearFixture(ServerLevel level) {
        BlockPos min = FIXTURE_CENTER.offset(
                -FIXTURE_RADIUS, -FIXTURE_RADIUS, -FIXTURE_RADIUS);
        BlockPos max = FIXTURE_CENTER.offset(
                FIXTURE_RADIUS, FIXTURE_RADIUS, FIXTURE_RADIUS);
        AABB fixtureBounds = new AABB(
                min.getX(), min.getY(), min.getZ(),
                max.getX() + 1, max.getY() + 1,
                max.getZ() + 1);
        for (Entity entity : level.getEntities(null, fixtureBounds)) {
            entity.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static boolean close(float first, float second) {
        return Math.abs(first - second) < 1.0E-5F;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
