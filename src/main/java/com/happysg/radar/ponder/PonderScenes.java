package com.happysg.radar.ponder;

import com.happysg.radar.registry.ModBlocks;


import com.simibubi.create.foundation.ponder.*;
import com.simibubi.create.foundation.ponder.element.WorldSectionElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PonderScenes {
    public static void radarContraption(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("radar_contraption", "Creating a Radar!");
        scene.rotateCameraY(180);
        scene.configureBasePlate(0, 0, 5);
        scene.world.showSection(util.select.layer(0), Direction.DOWN);
        scene.world.showSection(util.select.layer(1), Direction.DOWN);
        scene.idle(40);
        BlockPos bearing = util.grid.at(2, 2, 2);
        scene.world.showSection(util.select.position(bearing), Direction.DOWN);
        Vec3 bearingSide = util.vector.blockSurface(bearing, Direction.EAST);

        scene.overlay.showText(40)
                .pointAt(bearingSide)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Place Radar Bearing");
        scene.idle(60);

        BlockPos receiverPos = util.grid.at(2, 3, 2);
        ElementLink<WorldSectionElement> receiver =
                scene.world.showIndependentSection(util.select.position(receiverPos), Direction.DOWN);
        Vec3 receiverSide = util.vector.blockSurface(receiverPos, Direction.EAST);

        scene.overlay.showText(40)
                .pointAt(receiverSide)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Place Radar Receiver");
        scene.idle(40);

        BlockPos dish1 = util.grid.at(3, 3, 2);
        BlockPos dish2 = util.grid.at(1, 3, 2);
        ElementLink<WorldSectionElement> simple_dishes =
                scene.world.showIndependentSection(util.select.position(dish1).add(util.select.position(dish2)), Direction.DOWN);
        Vec3 dishSide = util.vector.blockSurface(dish1, Direction.EAST);
        scene.overlay.showText(40)
                .pointAt(dishSide)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Add Radar Plates");
        scene.idle(50);


        scene.world.replaceBlocks(util.select.position(dish1), ModBlocks.RADAR_DISH_BLOCK.get().defaultBlockState(), true);
        scene.world.replaceBlocks(util.select.position(dish2), ModBlocks.RADAR_DISH_BLOCK.get().defaultBlockState(), true);
        scene.overlay.showText(40)
                .pointAt(dishSide)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Radar Dishes can be used interchangeably with plates");
        scene.idle(50);

        ElementLink<WorldSectionElement> large_dishes =
                scene.world.showIndependentSection(util.select.layer(4), Direction.DOWN);
        scene.overlay.showText(40)
                .pointAt(dishSide.add(0, 1, 0))
                .placeNearTarget()
                .attachKeyFrame()
                .text("Additional dishes/plates extend range");
        scene.idle(40);


        scene.overlay.showText(40)
                .pointAt(bearingSide)
                .placeNearTarget()
                .attachKeyFrame()
                .text("Power Radar Bearing");

        scene.idle(10);
//        scene.world.rotateBearing(bearing, 360, 200);
        scene.world.rotateSection(receiver, 0, 360, 0, 200);
        scene.world.rotateSection(simple_dishes, 0, 360, 0, 200);
        scene.world.rotateSection(large_dishes, 0, 360, 0, 200);
//        scene.world.setKineticSpeed(util.select.layer(1), 32);
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void networkSetup(SceneBuilder scene, SceneBuildingUtil util){
        scene.title("network_setup", "Creating A Radar Network");
        scene.configureBasePlate(0, 0, 10);
        scene.world.showSection(util.select.layer(0), Direction.DOWN);
        scene.idle(20);
        Selection networkcontroller = util.select.position(4,1,4);
        scene.world.showSection(networkcontroller,Direction.DOWN);
        scene.idle(30);
        scene.overlay.showText(100)
                .text("")
                .pointAt(networkcontroller.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(120);
        scene.overlay.showText(100)
                .text("")
                .pointAt(networkcontroller.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);
        scene.rotateCameraY(90);
        scene.idle(40);



        scene.world.showSection(util.select.fromTo(0,1,6,0,3,8),Direction.DOWN);
        Selection monlink = util.select.position(0,2,5);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.INPUT, networkcontroller, new AABB(new BlockPos(4,1,4)), 60);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.OUTPUT, monlink, new AABB(new BlockPos(0,2,5)).contract(0, 0, -.5), 60);
        scene.overlay.showText(40)
                .text("")
                .pointAt(monlink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(5);
        scene.world.showSection(monlink,Direction.SOUTH);
        scene.idle(50);
        scene.world.hideSection((util.select.fromTo(0,1,6,0,3,8)),Direction.UP);
        scene.world.hideSection(monlink,Direction.UP);

        scene.idle(40);
        scene.rotateCameraY(90);
        scene.world.showSection(util.select.fromTo(4,1,0,4,1,3),Direction.DOWN );
        scene.world.showSection(util.select.column(3,2 ),Direction.DOWN);
        scene.world.showSection(util.select.column(4,2),Direction.DOWN);
        scene.world.showSection(util.select.fromTo(5,3,2,5,4,2),Direction.DOWN);
        Selection radarlink = util.select.position(5,2,2);
        Selection gunlink = util.select.position(8,2,5);

        scene.world.showSection(util.select.fromTo(9,9,9,9,0, 0 ),Direction.DOWN);
        scene.idle(30);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.INPUT, networkcontroller, new AABB(new BlockPos(4,1,4)), 60);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.OUTPUT, radarlink, new AABB(new BlockPos(5,2,2)).contract(.5, 0, 0), 60);
        scene.overlay.showText(40)
                .text("")
                .pointAt(radarlink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(10);
        scene.world.showSection(radarlink,Direction.WEST);
        scene.idle(50);
        scene.rotateCameraY(90);
        scene.idle(40);
        scene.overlay.showText(40)
                .text("")
                .pointAt(gunlink.getCenter())
                .attachKeyFrame()
                .placeNearTarget();
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.INPUT, networkcontroller, new AABB(new BlockPos(4,1,4)), 60);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.OUTPUT, gunlink, new AABB(new BlockPos(8,2,5)).contract(-.5, 0, 0), 60);
        scene.idle(20);
        scene.world.showSection(gunlink,Direction.EAST);






    }
    public static void weaponSimpleWeaponSetup(SceneBuilder scene, SceneBuildingUtil util){
        scene.title("weapon_setup", "Creating A Radar Network");
        scene.configureBasePlate(0, 0, 5);
        scene.world.showSection(util.select.layer(0), Direction.DOWN);
        scene.idle(10);
        scene.world.showSection(util.select.layer(1), Direction.DOWN);
        scene.idle(20);
        Selection yawController = util.select.position(2, 2, 2);
        scene.world.showSection(yawController, Direction.DOWN);

        Vec3 yawControllerSide = util.vector.blockSurface(new BlockPos(2, 2, 2), Direction.EAST);
        Selection cannonMount = util.select.position(2, 3, 2);
        Selection networkController = util.select.position(4,1,4);
        BlockPos networkControllerPos = util.grid.at(4,1,4);
        scene.idle(40);
        scene.world.showSection(cannonMount, Direction.DOWN);
        scene.idle(5);
        scene.overlay.showText(40)
                .text("Yaw Controller is placed under the turret mount")
                .pointAt(yawControllerSide)
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(60);


        BlockPos link = util.grid.at(1, 2, 2);
        scene.world.showSection(util.select.position(link), Direction.EAST);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.INPUT, cannonMount, new AABB(new BlockPos(2,3,2)), 60);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.OUTPUT, link, new AABB(link).contract(-.5f, 0, 0), 60);
        scene.overlay.showText(60)
                .text("Link using Data Links")
                .pointAt(link.getCenter())
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget();
        scene.idle(50);
        Selection pitchController = util.select.position(2,3,1);
        scene.idle(60);

        scene.world.showSection(pitchController,Direction.SOUTH);
        Vec3 pitchControllerSide = util.vector.blockSurface(new BlockPos(2,3,1), Direction.EAST);
        BlockPos link2 = util.grid.at(1, 3, 1);
        scene.world.showSection(util.select.position(link2), Direction.EAST);
        scene.overlay.showText(60)
                .text("Repeat for pitch Controller")
                .pointAt(pitchControllerSide)
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(60);
        scene.rotateCameraY(90);
        scene.idle(60);


        Selection firingcontrol =util.select.position(3,3,2);
        Selection link3 = util.select.position(3,3,3);
        scene.world.showSection(firingcontrol,Direction.WEST);
        scene.overlay.showText(60)
                .text("The fire control block emits redstone when the cannon is in the correct position")
                .pointAt(firingcontrol.getCenter())
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget();
        scene.idle(50);
        scene.rotateCameraY(90);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.INPUT, cannonMount, new AABB(new BlockPos(2,3,2)), 60);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.OUTPUT, link3, new AABB(new BlockPos (3,3,3)).contract(0, 0, .5f), 60);
        scene.idle(20);
        scene.world.showSection(link3, Direction.SOUTH);
        scene.idle(50);
        scene.rotateCameraY(-90);
        Selection link4 = util.select.position(2,4,1);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.OUTPUT, link4, new AABB(new BlockPos(2,4,1)).contract(0, .5, 0), 60);
        scene.overlay.chaseBoundingBoxOutline(PonderPalette.INPUT, networkController, new AABB(networkControllerPos).contract(0, .5, 0), 60);
        scene.idle(20);
        scene.world.showSection(link4,Direction.DOWN);
        scene.overlay.showText(60)
                .text("")
                .pointAt(networkControllerPos.getCenter())
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget();
        scene.idle(70);
        scene.overlay.showText(60)
                .text("")
                .pointAt(networkControllerPos.getCenter())
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget();
    }
}
