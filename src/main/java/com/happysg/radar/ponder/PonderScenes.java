package com.happysg.radar.ponder;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.skyradar.SkyRadarBlockEntity;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModBlocks;

import com.happysg.radar.registry.ModSounds;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

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
        ElementLink<WorldSectionElement> receiver = scene.world().showIndependentSection(util.select().position(receiverPos), Direction.DOWN);
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

    public static void networkSetup(SceneBuilder scene, SceneBuildingUtil util) {
        networkSetup(scene, util, false);
    }

    public static void monitorSetup(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("monitor_ponder", "Using a Radar Monitor");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.8f);
        scene.rotateCameraY(90);

        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(20);

        Selection monitor = util.select().fromTo(2, 1, 5, 2, 3, 7);
        Selection monitorLink = util.select().position(2, 2, 4);
        Selection controller = util.select().position(6, 1, 3);
        Selection radar = util.select().fromTo(5, 1, 1, 7, 4, 1);
        Selection radarLink = util.select().position(7, 2, 1);

        showSelection(scene, monitor);

        scene.overlay().showText(70)
                .pointAt(monitor.getCenter())
                .placeNearTarget()
                .attachKeyFrame()
                .text("The Monitor displays contacts detected by connected Radars.");
        scene.idle(85);

        showSelection(scene, controller);
        showSelection(scene, monitorLink);

        scene.overlay().showText(70)
                .pointAt(monitorLink.getCenter())
                .placeNearTarget()
                .attachKeyFrame()
                .text("Select a Network Controller, then connect it to the Monitor using a Data Link.");
        scene.idle(85);

        showSelection(scene, radar);
        showSelection(scene, radarLink);

        scene.overlay().showText(70)
                .pointAt(radarLink.getCenter())
                .placeNearTarget()
                .attachKeyFrame()
                .text("Connect a Radar to the same network to supply detected contacts.");
        scene.idle(85);

        scene.world().modifyBlockEntity(new BlockPos(2, 1, 5), MonitorBlockEntity.class, MonitorBlockEntity::beginPonderRadarVisual);
        scene.overlay().showText(85)
                .pointAt(monitor.getCenter())
                .placeNearTarget()
                .attachKeyFrame()
                .text("Detected contacts appear on the Monitor and can be selected for targeting.");

        scene.idle(100);
        scene.markAsFinished();
    }

    private static void networkSetup(SceneBuilder scene, SceneBuildingUtil util, boolean monitorFocus) {
        scene.title("network_setup", monitorFocus ? "Using a Radar Monitor" : "Using the Network Controller");
        scene.configureBasePlate(0, 0, 10);
        scene.scaleSceneView(0.65f);
        scene.rotateCameraY(180);
        scene.idle(20);
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(15);
        Selection controller = util.select().position(4, 1, 4);
        Selection monitor = util.select().fromTo(0, 1, 6, 0, 3, 8);
        Selection monitorLink = util.select().position(0, 2, 5);
        Selection radar = util.select().fromTo(3, 2, 2, 5, 4, 2);
        Selection radarLink = util.select().position(5, 2, 2);
        Selection yaw = util.select().position(9, 1, 6);
        Selection yawLink = util.select().position(9, 1, 7);
        Selection cannon = util.select().position(9, 2, 6);
        Selection pitch = util.select().position(9, 2, 5);
        Selection pitchNetworkLink = util.select().position(8, 2, 5);
        showSelection(scene, controller);
        scene.overlay().showText(70)
                .pointAt(controller.getCenter())
                .placeNearTarget()
                .attachKeyFrame()
                .text(monitorFocus
                        ? "A Monitor receives radar contacts from its linked Network Controller."
                        : "The Network Controller connects radars, monitors, and weapon networks.");
        scene.idle(80);
        scene.overlay().showText(50)
                .pointAt(controller.getCenter())
                .placeNearTarget()
                .attachKeyFrame()
                .text("Filters control detection and target selection.");
        scene.idle(85);
        scene.world().showSection(monitor, Direction.DOWN);
        showSelection(scene, monitorLink);

        scene.overlay().showText(75)
                .pointAt(monitor.getCenter())
                .placeNearTarget()
                .attachKeyFrame()
                .text(monitorFocus
                        ? "Select the Network Controller with a Data Link, then attach it to the Monitor."
                        : "Connect a Monitor to this network using a Data Link.");
        scene.idle(85);

        scene.world().showSection(radar, Direction.DOWN);
        showSelection(scene, radarLink);

        scene.overlay().showText(75)
                .pointAt(radarLink.getCenter())
                .placeNearTarget()
                .attachKeyFrame()
                .text(monitorFocus
                        ? "This Radar's Data Link connects it to the same network as the Monitor."
                        : "The same Network Controller can connect to Radars through their Data Links.");
        scene.idle(85);

        showSelection(scene, yaw);
        showSelection(scene, yawLink);
        showSelection(scene, cannon);
        showSelection(scene, pitch);
        showSelection(scene, pitchNetworkLink);

        scene.overlay().showText(85)
                .pointAt(pitch.getCenter())
                .placeNearTarget()
                .attachKeyFrame()
                .text(monitorFocus
                        ? "Weapon Networks receive selected targets from the Monitor through the connected Network Controller."
                        : "Weapon Networks also connect through a Data Link on the Pitch Controller.");
        scene.idle(95);
        scene.overlay().showText(85)
                .pointAt(monitorFocus ? monitor.getCenter() : controller.getCenter())
                .placeNearTarget()
                .attachKeyFrame()
                .text(monitorFocus
                        ? "Use the Monitor to inspect radar contacts and select targets for your weapons."
                        : "The controller shares contacts and target information.");
        scene.idle(90);
        scene.markAsFinished();
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
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.8f);
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(20);
        scene.world().showSection(util.select().layer(1), Direction.DOWN);

        BlockPos radarPos = new BlockPos(3, 2, 3);
        Selection skyRadar = util.select().fromTo(radarPos, radarPos.above());
        scene.world().showSection(skyRadar, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("The Sky Radar Mount is an alternative, high power variant of the Radar Bearing")
                .pointAt(radarPos.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(70);
        Selection radar = util.select().fromTo(1, 4, 3, 5, 6, 3);
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
                util.vector().centerOf(util.grid().at(3, 2, 3))
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
        Selection datalink = util.select().position(3, 2, 4);
        BlockPos network = new BlockPos(5, 1, 5);
        scene.overlay().showText(60)
                .text("And is linked to the Radar Network in the same way as well")
                .pointAt(datalink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, network, new AABB(new BlockPos(5, 1, 5)), 60);
        scene.idle(10);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, datalink, new AABB(new BlockPos(3, 2, 4)).contract(0, 0, 0.5f), 50);
        scene.world().showSection(datalink, Direction.NORTH);

        scene.idle(90);
        scene.overlay().showText(80)
                .text("",RadarConfig.server().skyRadarMinY.get())
                .pointAt(datalink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);
        scene.special().createBirb(new Vec3(1, 1, 5), ParrotPose.DancePose::new);
        scene.overlay().showText(80)
                .text("Unlike the normal radar, The sky radar can not detect targets below its horizon")
                .pointAt(new Vec3(1, 1, 5))
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

    private enum WeaponPonderFocus { GENERAL, DATA_LINK, YAW, PITCH }

    public static void dataLinkSetup(@NotNull SceneBuilder scene, SceneBuildingUtil util) {
        weaponSimpleWeaponSetup(scene, util, WeaponPonderFocus.DATA_LINK, "data_link_ponder");
    }

    public static void autoYawSetup(@NotNull SceneBuilder scene, SceneBuildingUtil util) {
        weaponSimpleWeaponSetup(scene, util, WeaponPonderFocus.YAW, "auto_yaw_ponder");
    }

    public static void autoPitchSetup(@NotNull SceneBuilder scene, SceneBuildingUtil util) {
        weaponSimpleWeaponSetup(scene, util, WeaponPonderFocus.PITCH, "auto_pitch_ponder");
    }

    public static void autoPitchFollowup(@NotNull SceneBuilder scene, SceneBuildingUtil util) {
        weaponSimpleWeaponSetup(scene, util, WeaponPonderFocus.PITCH, "auto_pitch_ponder");
    }

    public static void weaponSimpleWeaponSetup(@NotNull SceneBuilder scene, SceneBuildingUtil util) {
        weaponSimpleWeaponSetup(scene, util, WeaponPonderFocus.GENERAL);
    }

    private static void weaponSimpleWeaponSetup(@NotNull SceneBuilder scene, SceneBuildingUtil util, WeaponPonderFocus focus) {
        weaponSimpleWeaponSetup(scene, util, focus, "weapon_setup");
    }

    private static void weaponSimpleWeaponSetup(@NotNull SceneBuilder scene, SceneBuildingUtil util, WeaponPonderFocus focus, String sceneId) {
        scene.title(sceneId, switch (focus) {
            case GENERAL -> "Automatic Weapon Setup";
            case DATA_LINK -> "Using Data Links";
            case YAW -> "Using the Auto Yaw Controller";
            case PITCH -> "Using the Auto Pitch Controller";
        });

        scene.configureBasePlate(0, 0, 8);
        scene.scaleSceneView(0.75f);

        Selection lowerYawController = util.select().position(3, 1, 2);
        Selection yawControllerLink = util.select().position(3, 1, 1);
        Selection upperYawController = util.select().position(3, 1, 4);
        Selection upperYawControllerLink = util.select().position(3, 1, 5);
        Selection networkController = util.select().position(7, 1, 6);

        Selection lowerCannonMount = util.select().position(3, 2, 2);
        Selection lowerPitchController = util.select().position(4, 2, 2);
        Selection upperMountBaseExtension = util.select().position(3, 2, 4);
        Selection upperCannonMount = util.select().position(3, 3, 4);
        Selection upperMountSideExtension = util.select().position(4, 3, 4);
        Selection upperPitchController = util.select().position(5, 3, 4);

        Selection lowerPitchDataLink = util.select().position(4, 3, 2);
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
        scene.idle(20);

        if (focus == WeaponPonderFocus.DATA_LINK) {
            scene.overlay().showText(80)
                    .pointAt(lowerCannonMount.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Hold a Data Link and right-click a cannon mount to select it.");
            scene.overlay().showControls(lowerCannonMount.getCenter(), Pointing.DOWN, 35).rightClick();
            scene.idle(90);
            scene.overlay().showText(85)
                    .pointAt(yawControllerLink.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Right-click Auto Yaw to attach a Data Link connecting it to the selected cannon mount.");
            scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, yawControllerLink, new AABB(new BlockPos(3, 1, 1)), 80);
            scene.idle(100);
            scene.overlay().showText(85)
                    .pointAt(lowerPitchDataLink.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("To join a radar network, select a Network Controller, then attach its Data Link to the Pitch Controller.");
            scene.idle(100);
            scene.overlay().showText(85)
                    .pointAt(networkController.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Data Links also connect Radars, Monitors, and Radar Warning Receivers to compatible endpoints.");
            scene.idle(100);
        } else if (focus == WeaponPonderFocus.YAW) {
            scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, lowerYawController, new AABB(new BlockPos(3, 1, 2)), 80);
            scene.overlay().showText(70)
                    .pointAt(lowerYawController.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Place Auto Yaw directly beneath the cannon mount.");
            scene.idle(85);
            scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, lowerCannonMount, new AABB(new BlockPos(3, 2, 2)), 80);
            scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, yawControllerLink, new AABB(new BlockPos(3, 1, 1)), 80);
            scene.overlay().showText(75)
                    .pointAt(yawControllerLink.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Use the Data Link on Auto Yaw to connect it to the cannon mount.");
            scene.idle(90);
            scene.overlay().showText(80)
                    .pointAt(lowerYawController.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Right-click Auto Yaw to configure its minimum and maximum rotation angles.");
            scene.overlay().showControls(lowerYawController.getCenter(), Pointing.DOWN, 35).rightClick();
            scene.idle(90);
            scene.markAsFinished();
            return;
        } else if (focus == WeaponPonderFocus.PITCH) {

            scene.overlay().chaseBoundingBoxOutline(
                    PonderPalette.INPUT,
                    lowerPitchController,
                    new AABB(new BlockPos(4, 2, 2)),
                    80
            );

            scene.overlay().showText(75)
                    .pointAt(lowerPitchController.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Place Auto Pitch beside the cannon mount to control elevation and depression.");

            scene.idle(85);

            scene.overlay().showText(80)
                    .pointAt(lowerPitchController.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Right-click Auto Pitch to configure minimum depression and maximum elevation.");
            scene.overlay().showControls(lowerPitchController.getCenter(), Pointing.DOWN, 35).rightClick();
            scene.idle(95);
            scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, networkController, new AABB(new BlockPos(7, 1, 6)), 80);
            scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, lowerPitchDataLink, new AABB(new BlockPos(4, 3, 2)), 80);
            scene.overlay().showText(80)
                    .pointAt(lowerPitchDataLink.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Select the Network Controller, then connect it to Auto Pitch with a Data Link.");
            scene.idle(95);
        } else {
            scene.overlay().showText(85)
                    .pointAt(lowerCannonMount.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Combine a CBC mount with Auto Yaw and Auto Pitch for automatic aiming.");
            scene.idle(95);
            scene.overlay().showText(85)
                    .pointAt(lowerPitchDataLink.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Connect the Pitch Controller to a Network Controller with a Data Link to receive targets.");
            scene.idle(95);
        }
        showSelection(scene, upperYawController);
        showSelection(scene, upperYawControllerLink);
        showSelection(scene, upperMountBaseExtension);
        showSelection(scene, upperCannonMount);
        showSelection(scene, upperMountSideExtension);
        showSelection(scene, upperPitchController);
        showSelection(scene, upperPitchDataLink);
        showSelection(scene, upperCannon);
        if (focus == WeaponPonderFocus.PITCH || focus == WeaponPonderFocus.GENERAL) {
            scene.overlay().showText(75)
                    .pointAt(upperMountSideExtension.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("CBC Mount Extensions can connect cannon mounts to Auto Pitch.");

            scene.idle(85);
        } else if (focus == WeaponPonderFocus.DATA_LINK) {
            scene.overlay().showText(75)
                    .pointAt(upperYawControllerLink.getCenter())
                    .placeNearTarget()
                    .attachKeyFrame()
                    .text("Data Links connect cannon mounts to controllers and other components to radar networks.");
            scene.idle(85);
        } else {
            scene.idle(40);
        }
        scene.markAsFinished();
    }

    public static void tPitchSetup(@NotNull SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("t_pitch_ponder", "Using a T-Pitch Controller");
        scene.configureBasePlate(0, 0, 8);
        scene.scaleSceneView(0.75f);
        Selection leftYawController = util.select().position(2, 1, 4);
        Selection leftYawControllerLink = util.select().position(2, 1, 3);
        Selection rightYawController = util.select().position(4, 1, 4);
        Selection rightYawControllerLink = util.select().position(4, 1, 3);
        Selection networkController = util.select().position(6, 1, 2);

        Selection leftCannonMount = util.select().position(2, 2, 4);
        Selection leftCannon = util.select().fromTo(2, 4, 2, 2, 4, 5);
        Selection tPitchController = util.select().position(3, 2, 4);
        Selection rightCannonMount = util.select().position(4, 2, 4);
        Selection rightCannon = util.select().fromTo(4, 4, 2, 4, 4, 5);
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
        scene.idle(20);
        showSelection(scene, tPitchDataLink);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, networkController, new AABB(new BlockPos(6, 1, 2)), 70);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, tPitchDataLink, new AABB(new BlockPos(3, 3, 4)), 70);
        scene.overlay().showText(75)
                .text("Select the Network Controller, then attach a Data Link to the T-Pitch Gearbox.")
                .pointAt(tPitchDataLink.getCenter())
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);
        scene.overlay().showText(75)
                .text("The two sides share one pitch command. Right-click the gearbox to configure minimum and maximum pitch angles.")
                .pointAt(tPitchController.getCenter())
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);
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

    public static void sonarSetup(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder createScene = new CreateSceneBuilder(scene);
        scene.title("sonar_ponder", "Using Sonar Arrays");
        scene.rotateCameraY(180);
        scene.configureBasePlate(0, 0, 5);

        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(20);

        BlockPos bearingPos = util.grid().at(2, 2, 2);
        Selection bearing = util.select().position(bearingPos);
        scene.world().showSection(bearing, Direction.DOWN);

        scene.overlay().showText(70)
                .text("The Sonar Bearing controls a sonar array")
                .pointAt(bearing.getCenter())
                .attachKeyFrame()
                .placeNearTarget();

        scene.idle(80);
        BlockPos rootSensorPos = util.grid().at(2, 3, 2);
        Selection rootSensor = util.select().position(rootSensorPos);
        scene.world().showSection(rootSensor, Direction.DOWN);

        scene.overlay().showText(80)
                .text("A Sonar Sensor must be placed directly in front of the bearing")
                .pointAt(rootSensor.getCenter())
                .attachKeyFrame()
                .placeNearTarget();

        scene.idle(90);
        Selection lowerSensors = util.select().position(1, 3, 2).add(util.select().position(3, 3, 2));
        scene.world().showSection(lowerSensors, Direction.DOWN);

        scene.overlay().showText(80)
                .text("Connected Sonar Sensors become part of the sonar contraption")
                .pointAt(lowerSensors.getCenter())
                .attachKeyFrame()
                .placeNearTarget();

        scene.idle(90);
        Selection upperSensors = util.select().fromTo(1, 4, 2, 3, 4, 2);
        scene.world().showSection(upperSensors, Direction.DOWN);

        scene.overlay().showText(90)
                .text("More assembled sensors increase the sonar's detection range")
                .pointAt(upperSensors.getCenter())
                .attachKeyFrame()
                .placeNearTarget();

        scene.idle(100);
        scene.overlay().showText(80)
                .text("Right-click the Sonar Bearing to assemble the sensor array")
                .pointAt(bearing.getCenter())
                .attachKeyFrame()
                .placeNearTarget();

        scene.idle(90);
        scene.overlay().showControls(bearing.getCenter(), Pointing.DOWN, 40).rightClick();

        scene.idle(20);
        scene.world().hideSection(rootSensor, Direction.UP);
        scene.world().hideSection(lowerSensors, Direction.UP);
        scene.world().hideSection(upperSensors, Direction.UP);

        scene.idle(20);

        Selection sonarArray = util.select().fromTo(1, 3, 2, 3, 4, 2);
        scene.world().showIndependentSectionImmediately(sonarArray);

        scene.idle(30);
        scene.overlay().showText(80)
                .text("The sonar array remains stationary after assembly")
                .pointAt(lowerSensors.getCenter())
                .attachKeyFrame()
                .placeNearTarget();

        scene.idle(90);
        scene.overlay().showText(90)
                .text("Instead, the sonar performs an electronic sweep around the array")
                .pointAt(bearing.getCenter())
                .attachKeyFrame()
                .placeNearTarget();

        scene.idle(100);
        scene.overlay().showText(100)
                .text("Solid blocks and fluids carry the sonar signal, while too much open air blocks detection")
                .pointAt(lowerSensors.getCenter())
                .attachKeyFrame()
                .placeNearTarget();

        scene.idle(110);
        createScene.world().setKineticSpeed(util.select().fromTo(0, 1, 2, 2, 1, 2), 32);
        scene.overlay().showText(90)
                .text("Provide rotational power to the bearing for the sonar to operate")
                .pointAt(util.grid().at(1, 1, 2).getCenter())
                .attachKeyFrame()
                .placeNearTarget();

        scene.idle(100);
        scene.markAsFinished();
    }

    public static void jammerSetup(SceneBuilder scene, SceneBuildingUtil util){}
}
