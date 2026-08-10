package com.happysg.radar.ponder;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.skyradar.SkyRadarBlockEntity;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModBlocks;

import com.happysg.radar.registry.ModSounds;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.*;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.core.impl.shadow.Bl;

public class PonderScenes {
    public static void radarContraption(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("radar_contraption", "Creating a Radar!");
        scene.rotateCameraY(180);
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(40);
        BlockPos bearing = util.grid().at(2, 2, 2);
        scene.world().showSection(util.select().position(bearing), Direction.DOWN);
        Vec3 bearingSide = util.vector().blockSurface(bearing, Direction.EAST);

        scene.overlay().showText(40)
                .pointAt(bearingSide)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Place Radar Bearing");
        scene.idle(60);

        BlockPos receiverPos = util.grid().at(2, 3, 2);
        ElementLink<WorldSectionElement> receiver =
                scene.world().showIndependentSection(util.select().position(receiverPos), Direction.DOWN);
        Vec3 receiverSide = util.vector().blockSurface(receiverPos, Direction.EAST);

        scene.overlay().showText(40)
                .pointAt(receiverSide)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Place Radar Receiver");
        scene.idle(40);

        BlockPos dish1 = util.grid().at(3, 3, 2);
        BlockPos dish2 = util.grid().at(1, 3, 2);
        ElementLink<WorldSectionElement> simple_dishes =
                scene.world().showIndependentSection(util.select().position(dish1).add(util.select().position(dish2)), Direction.DOWN);
        Vec3 dishSide = util.vector().blockSurface(dish1, Direction.EAST);
        scene.overlay().showText(40)
                .pointAt(dishSide)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Add Radar Plates");
        scene.idle(50);


        scene.world().replaceBlocks(util.select().position(dish1), ModBlocks.RADAR_DISH_BLOCK.get().defaultBlockState(), true);
        scene.world().replaceBlocks(util.select().position(dish2), ModBlocks.RADAR_DISH_BLOCK.get().defaultBlockState(), true);
        scene.overlay().showText(40)
                .pointAt(dishSide)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Radar Dishes can be used interchangeably with plates");
        scene.idle(50);

        ElementLink<WorldSectionElement> large_dishes =
                scene.world().showIndependentSection(util.select().layer(4), Direction.DOWN);
        scene.overlay().showText(40)
                .pointAt(dishSide.add(0, 1, 0))
                .placeNearTarget()
                .attachKeyFrame()
                .text("Additional dishes/plates extend range");
        scene.idle(40);


        scene.overlay().showText(40)
                .pointAt(bearingSide)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Power Radar Bearing");

        scene.idle(10);
//        scene.world().rotateBearing(bearing, 360, 200);
        scene.world().rotateSection(receiver, 0, 360, 0, 200);
        scene.world().rotateSection(simple_dishes, 0, 360, 0, 200);
        scene.world().rotateSection(large_dishes, 0, 360, 0, 200);
//        scene.world().setKineticSpeed(util.select().layer(1), 32);
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void networkSetup(SceneBuilder scene, SceneBuildingUtil util){
        scene.title("network_setup", "Creating A Radar Network");
        scene.configureBasePlate(0, 0, 10);
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(20);
        Selection networkcontroller = util.select().position(4,1,4);
        scene.world().showSection(networkcontroller,Direction.DOWN);
        scene.idle(30);
        scene.overlay().showText(100)
                .text("")
                .pointAt(networkcontroller.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(120);
        scene.overlay().showText(100)
                .text("")
                .pointAt(networkcontroller.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);
        scene.rotateCameraY(90);
        scene.idle(40);



        scene.world().showSection(util.select().fromTo(0,1,6,0,3,8),Direction.DOWN);
        Selection monlink = util.select().position(0,2,5);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, networkcontroller, new AABB(new BlockPos(4,1,4)), 60);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, monlink, new AABB(new BlockPos(0,2,5)).contract(0, 0, -.5), 60);
        scene.overlay().showText(40)
                .text("")
                .pointAt(monlink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(5);
        scene.world().showSection(monlink,Direction.SOUTH);
        scene.idle(50);
        scene.world().hideSection((util.select().fromTo(0,1,6,0,3,8)),Direction.UP);
        scene.world().hideSection(monlink,Direction.UP);

        scene.idle(40);
        scene.rotateCameraY(90);
        scene.world().showSection(util.select().fromTo(4,1,0,4,1,3),Direction.DOWN );
        scene.world().showSection(util.select().column(3,2 ),Direction.DOWN);
        scene.world().showSection(util.select().column(4,2),Direction.DOWN);
        scene.world().showSection(util.select().fromTo(5,3,2,5,4,2),Direction.DOWN);
        Selection radarlink = util.select().position(5,2,2);
        Selection gunlink = util.select().position(8,2,5);

        scene.world().showSection(util.select().fromTo(9,9,9,9,0, 0 ),Direction.DOWN);
        scene.idle(30);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, networkcontroller, new AABB(new BlockPos(4,1,4)), 60);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, radarlink, new AABB(new BlockPos(5,2,2)).contract(.5, 0, 0), 60);
        scene.overlay().showText(40)
                .text("")
                .pointAt(radarlink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(10);
        scene.world().showSection(radarlink,Direction.WEST);
        scene.idle(50);
        scene.rotateCameraY(90);
        scene.idle(40);
        scene.overlay().showText(40)
                .text("")
                .pointAt(gunlink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, networkcontroller, new AABB(new BlockPos(4,1,4)), 60);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, gunlink, new AABB(new BlockPos(8,2,5)).contract(-.5, 0, 0), 60);
        scene.idle(20);
        scene.world().showSection(gunlink,Direction.EAST);






    }

    public static void rwrPonder(SceneBuilder scene, SceneBuildingUtil util){
        scene.title("rwr_ponder", "rwr");
        scene.scaleSceneView(0.75f);
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(40);
        Selection monitor = util.select().fromTo(3, 1, 6, 5, 3, 6);
        Selection firstRwr = util.select().position(0, 1, 2);
        Selection secondRwr = util.select().position(2, 1, 6);
        Selection dataLink = util.select().position(3, 4, 6);

        scene.world().showSection(firstRwr,Direction.DOWN);
        scene.overlay().showText(80)
                .text("The Radar Warning Receiver passively detects and report nearby radar sources")
                .pointAt(firstRwr.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.world().showSection(monitor,Direction.DOWN);
        scene.idle(100);
        scene.overlay().showText(80)
                .text("It can be linked to a monitor using datalinks...")
                .pointAt(dataLink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, firstRwr, new AABB(new BlockPos(0,1,2)), 60);
        scene.idle(20);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, monitor, new AABB(3, 1, 6, 6, 4, 7), 60);
        scene.idle(20);
        scene.world().showSection(dataLink,Direction.DOWN);
        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::beginPonderRwrVisual
        );
        scene.idle(40);
        scene.world().hideSection(dataLink,Direction.UP);
        scene.world().hideSection(firstRwr,Direction.UP);
        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::endPonderRwrVisual
        );
        scene.idle(40);
        scene.world().showSection(secondRwr,Direction.DOWN);
        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::beginPonderRwrVisual
        );
        scene.overlay().showText(80)
                .text("Or by direct contact with the monitor")
                .pointAt(secondRwr.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(90)
                .text("The monitor is used to determine the type, threat level, and approximate bearings of nearby radar sources")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(100);
        scene.scaleSceneView(1.2f);
        scene.overlay().showText(80)
                .text("The rings of the display the threat level of the contact, not distance")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);

        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::showPonderFriendlyOuterRingContact
        );
        scene.overlay().showText(80)
                .text("When a contact first enters the RWR's detection range, this sound will play")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(15);
        scene.addInstruction(ponderScene ->
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(ModSounds.RWR_IN_RANGE.get(), 1.0f,5)
                )
        );
        scene.idle(90-15);

        scene.overlay().showText(110)
                .text("A radar contact on the outer ring indicates that the RWR's sublevel is 1-1.5x of the radar's effective range, It can not see the Sublevel")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(120);
        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::showPonderCenterRingContacts
        );
        scene.overlay().showText(100)
                .text("A radar contact on the Center ring indicates that the sublevel is within range of the radar. The sublevel is now visible")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(110);
        scene.overlay().showText(100)
                .text("The RWR will now begin to emit a redstone signal, scaling with the sublevels proximity to the radar, from 1-14")
                .pointAt(secondRwr.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(110);
        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::showPonderInnerRingHighThreatContact
        );
        scene.overlay().showText(110)
                .text("When the radar locks on to the RWR's sublevel, the contact will move to the inner ring and become red, and the RWR will play this sound")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(30);
        scene.addInstruction(ponderScene ->
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(ModSounds.RWR_LOCK.get(), 1.0f,5)
                )
        );
        scene.addInstruction(ponderScene ->
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(ModSounds.RWR_LOCK.get(), 1.0f,5)
                )
        );
        scene.idle(80);
        scene.overlay().showText(70)
                .text("The RWR will also produce a redstone strength of 15")
                .pointAt(secondRwr.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(80);




    }

    public static void rwrContactsPonder(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rwr_contacts_ponder", "Reading RWR Contacts");
        scene.scaleSceneView(0.75f);
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(20);

        Selection monitor = util.select().fromTo(3, 1, 6, 5, 3, 6);
        Selection secondRwr = util.select().position(2, 1, 6);
        Selection planeRadar = util.select().position(6, 1, 1);
        Selection bearingRadar = util.select().fromTo(6, 1, 0, 6, 3, 0);
        Selection skyRadar = util.select().fromTo(6, 1, 2, 6, 4, 2);

        scene.world().showSection(monitor, Direction.DOWN);
        scene.world().showSection(secondRwr, Direction.DOWN);
        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::beginPonderRwrVisual
        );
        scene.idle(40);

        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::showPonderGroundRadarIcon
        );
        scene.world().showSection(bearingRadar,Direction.DOWN);
        scene.overlay().showText(60)
                .text("This icon designates a standard radar contact")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(70);
        scene.world().hideSection(bearingRadar, Direction.UP);
        scene.idle(10);
        scene.world().showSection(planeRadar,Direction.DOWN);

        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::showPonderAirborneRadarIcon
        );
        scene.overlay().showText(60)
                .text("A plane radar contact")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(70);
        scene.world().hideSection(planeRadar, Direction.UP);
        scene.idle(10);
        scene.world().showSection(skyRadar,Direction.DOWN);
        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::showPonderSkyRadarIcon
        );
        scene.overlay().showText(80)
                .text("and a Sky radar contact")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);

        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::showPonderFriendlySkyRadarIcon
        );
        scene.overlay().showText(100)
                .text("If a radar shares the same code in its Identification Filter as the RWR's Sublevel, Its icon will be shown in blue. ")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(110);

        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::showPonderHighThreatSkyRadarIcon
        );
        scene.overlay().showText(80)
                .text("The Highest threat contact will be highlighted")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);

        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::showPonderLockingSkyRadarIcon
        );
        scene.overlay().showText(90)
                .text("When a radar locks on to the sublevel, it will be shown in red")
                .pointAt(monitor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);
        scene.world().modifyBlockEntity(
                new BlockPos(3, 1, 6),
                MonitorBlockEntity.class,
                MonitorBlockEntity::endPonderRwrVisual
        );



    }

    public static void skyRadarSetup(SceneBuilder scene, SceneBuildingUtil util) {
        scene.rotateCameraY(180);
        scene.title("sky_radar_ponder", "Using the sky radar");
        scene.configureBasePlate(0, 0, 15);
        scene.scaleSceneView(0.5f);
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(20);
        scene.world().showSection(util.select().layer(1), Direction.DOWN);

        BlockPos radarPos = new BlockPos(10, 2, 2);
        Selection skyRadar = util.select().fromTo(radarPos, radarPos.above());
        scene.world().showSection(skyRadar, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("The Sky Radar Mount is an alternative, high power variant of the Radar Bearing")
                .pointAt(radarPos.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(70);
        Selection radar = util.select().fromTo(8, 4, 2, 12, 10, 2);
        ElementLink<WorldSectionElement> radarElement = scene.world()
                .showIndependentSectionImmediately(radar);


        scene.idle(20);
        scene.overlay().showText(60)
                .text("The Radar Contraption is the same between both radar types")
                .pointAt(radar.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);
        scene.world().configureCenterOfRotation(
                radarElement,
                util.vector().centerOf(util.grid().at(10, 2, 2))
        );
        scene.world().modifyBlockEntity(
                radarPos,
                SkyRadarBlockEntity.class,
                SkyRadarBlockEntity::beginPonderVisual
        );

        for (int i = 0; i < 4; i++) {
            scene.world().modifyBlockEntity(
                    radarPos,
                    SkyRadarBlockEntity.class,
                    be -> be.animatePonderYaw(90.0f, 20)
            );

            scene.world().rotateSection(radarElement, 0, 90, 0, 20);

            scene.idle(20);
        }
        scene.idle(20);
        Selection datalink = util.select().position(10,2,3);
        BlockPos network = new BlockPos(12,1,4);
        scene.overlay().showText(60)
                .text("And is linked to the Radar Network in the same way as well")
                .pointAt(datalink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, network, new AABB(new BlockPos(12,1,4)), 60);
        scene.idle(10);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, datalink, new AABB(new BlockPos(10,2,3)).contract(0,0 , 0.5f), 50);
        scene.world().showSection(datalink, Direction.NORTH);

        scene.idle(90);
        scene.overlay().showText(80)
                .text("",RadarConfig.server().skyRadarMinY.get())
                .pointAt(datalink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);
        scene.special().createBirb(new Vec3(5,1,5), ParrotPose.DancePose::new);
        scene.overlay().showText(80)
                .text("Unlike the normal radar, The sky radar can not detect targets below its horizon")
                .pointAt(new Vec3(5,1,5))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);
        ElementLink<EntityElement> targetMob = scene.world().createEntity(level -> {
            Parrot parrrot = new Parrot(EntityType.PARROT, level);

            parrrot.setPos(1,5,5);
            parrrot.setYRot(180.0f);
            parrrot.setYHeadRot(180.0f);

            parrrot.setNoAi(true);
            parrrot.setInvulnerable(true);
            return parrrot;
        });
        //scene.special().createBirb(new Vec3(1,4,5), ParrotPose.FlappyPose::new);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("However, There is no upper detection bound, meaning that it can see anything above itself that is within its range")
                .pointAt(new Vec3(1,5,5))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);


    }

    public static void weaponSimpleWeaponSetup(@NotNull SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("weapon_setup", "Automatic Weapon Setup");
        scene.configureBasePlate(0, 0, 8);

        Selection lowerYawController = util.select().position(3, 1, 2);
        Selection yawControllerLink = util.select().position(3, 1, 3);
        Selection upperYawController = util.select().position(3, 1, 4);
        Selection networkController = util.select().position(7, 1, 6);

        Selection lowerCannonMount = util.select().position(3, 2, 2);
        Selection lowerPitchController = util.select().position(4, 2, 2);
        Selection upperMountBaseExtension = util.select().position(3, 2, 4);
        Selection upperCannonMount = util.select().position(3, 3, 4);
        Selection upperMountSideExtension = util.select().position(4, 3, 4);
        Selection upperPitchController = util.select().position(5, 3, 4);

        Selection lowerPitchDataLink = util.select().position(4, 3, 2);
        Selection upperPitchControllerLink = util.select().position(5, 3, 3);
        Selection upperPitchDataLink = util.select().position(5, 4, 4);

        Selection lowerCannon = util.select().fromTo(3, 4, 1, 3, 4, 3);
        Selection upperCannon = util.select().fromTo(3, 5, 2, 3, 5, 5);

        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(10);

        showSelection(scene, networkController);

        showSelection(scene, lowerYawController);
        showSelection(scene, yawControllerLink);
        showSelection(scene, lowerCannonMount);
        showSelection(scene, lowerPitchController);
        showSelection(scene, lowerPitchDataLink);
        showSelection(scene, lowerCannon);

        showSelection(scene, upperYawController);
        showSelection(scene, upperMountBaseExtension);
        showSelection(scene, upperCannonMount);
        showSelection(scene, upperMountSideExtension);
        showSelection(scene, upperPitchController);
        showSelection(scene, upperPitchControllerLink);
        showSelection(scene, upperPitchDataLink);
        showSelection(scene, upperCannon);

        scene.idle(10);
        scene.markAsFinished();
    }

    public static void tPitchSetup(@NotNull SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("t_pitch_ponder", "Using a T-Pitch Controller");
        scene.configureBasePlate(0, 0, 8);

        Selection leftYawController = util.select().position(2, 1, 4);
        Selection leftYawControllerLink = util.select().position(2, 1, 3);
        Selection rightYawController = util.select().position(4, 1, 4);
        Selection rightYawControllerLink = util.select().position(4, 1, 3);
        Selection networkController = util.select().position(6, 1, 2);

        Selection leftCannonMount = util.select().position(2, 2, 4);
        Selection leftCannon =
                util.select().fromTo(2, 4, 2, 2, 4, 5);
        Selection tPitchController = util.select().position(3, 2, 4);
        Selection rightCannonMount = util.select().position(4, 2, 4);
        Selection rightCannon =
                util.select().fromTo(4, 4, 2, 4, 4, 5);
        Selection tPitchControllerLink = util.select().position(3, 2, 5);
        Selection tPitchDataLink = util.select().position(3, 3, 4);

        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(10);

        showSelection(scene, networkController);
        showSelection(scene, rightYawController);
        showSelection(scene, rightCannonMount);
        showSelection(scene, rightCannon);
        scene.idle(10);
        showSelection(scene, rightYawControllerLink);
//
        showSelection(scene, tPitchController);
//        showSelection(scene, tPitchControllerLink);
//        showSelection(scene, tPitchDataLink);
        scene.overlay().showText(60)
                .text("The T-Pitch Controller is a dual-output alternative to the Auto Pitch Controller")
                .pointAt(tPitchController.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(70);
        scene.overlay().showText(60)
                .text("It can support 2 cannon mounts at the same time")
                .pointAt(tPitchController.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(5);
        showSelection(scene, leftYawController);
        showSelection(scene, leftYawControllerLink);
        showSelection(scene, leftCannonMount);
        showSelection(scene, leftCannon);
        scene.idle(65);
        scene.rotateCameraY(180);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, leftCannonMount, new AABB(new BlockPos(2, 2, 4)), 60);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, tPitchController, new AABB(new BlockPos(12,1,4)), 60);
        scene.idle(40);
        showSelection(scene,tPitchControllerLink);
        scene.overlay().showText(60)
                .text("")
                .pointAt(tPitchController.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(70);
        scene.markAsFinished();
    }

    public static void swivelSetup(@NotNull SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("swivel_ponder", "Using Swivel Bearings");
        scene.configureBasePlate(0, 0, 7);

        Selection networkController = util.select().position(0, 1, 1);

        Selection mountedYawSwivel = util.select().position(2, 1, 4);
        Selection mountedYawController = util.select().position(3, 1, 4);
        Selection mountedYawControllerLink = util.select().position(3, 1, 5);
        Selection mountedCannonMount = util.select().position(2, 2, 4);
        Selection mountedPitchController = util.select().position(1, 2, 4);
        Selection mountedPitchControllerLink = util.select().position(1, 2, 5);
        Selection mountedNetworkDataLink = util.select().position(1, 3, 4);

        Selection dualYawSwivel = util.select().position(3, 1, 2);
        Selection dualYawController = util.select().position(4, 1, 2);
        Selection dualYawControllerLink = util.select().position(4, 1, 1);
        Selection dualPitchController = util.select().position(3, 2, 2);
        Selection dualNetworkDataLink = util.select().position(3, 2, 3);
        Selection dualPitchSwivel = util.select().position(3, 3, 2);
        Selection dualCannonMount = util.select().position(2, 3, 2);

        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(10);


    }

    private static void showSelection(SceneBuilder scene, Selection selection) {
        scene.world().showSection(selection, Direction.DOWN);
        scene.idle(5);
    }

    public static void sonarSetup(SceneBuilder scene, SceneBuildingUtil util){}

    public static void jammerSetup(SceneBuilder scene, SceneBuildingUtil util){}



}
