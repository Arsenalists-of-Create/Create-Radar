package com.happysg.radar.config.client;

import net.createmod.catnip.config.ConfigBase;

public class RadarClientConfig extends ConfigBase {

    @Override
    public String getName() {
        return "Radar Client";
    }

    public ConfigInt groundRadarColor = i(0x00ff00, 0, "groundRadarColor", "This is the color of the ground radar on the monitor");
    public ConfigInt gridBoxScale = i(100, 1, "gridBoxScale", "This is the scale of the grid boxes on the monitor in blocks");
    public ConfigInt hostileColor = i(0xff0000, 0, "hostileColor", "This is the color of hostile entities on the monitor");
    public ConfigInt friendlyColor = i(0x00ff00, 0, "friendlyColor", "This is the color of friendly entities on the monitor");
    public ConfigInt playerColor = i(0x0000ff, 0, "playerColor", "This is the color of players on the monitor");
    public ConfigInt projectileColor = i(0xffff00, 0, "projectileColor", "This is the color of projectiles on the monitor");
    public ConfigInt contraptionColor = i(0xffffff, 0, "contraptionColor", "This is the color of contraptions on the monitor");
    public ConfigInt SableColor = i(0xffff00, 0, "SableColor", "This is the color of Sable ships on the monitor");
    public ConfigInt itemcolor = i(0xffa500,0,"itemcolor", "This is the color of dropped items on the monitor");
    public ConfigInt neutralEntityColor = i(0xffffff, 0, "neutralEntityColor", "This is the color of neutral entities on the monitor");
    public ConfigGroup monitorConfig = group(3,"monitorConfig","monitorConfig");
    public ConfigBool disableMonitorRendering = b(false,"disableMonitorRendering","If true, the monitor will always display blank. this may improve performance");
    public ConfigBool useGuiByDefault = b(true,"useGuiByDefault", "If any interaction with the monitor should open up the GUI. if false, uses pre 0.4 behavior");
    public ConfigFloat monitorTextScale = f(0.5f,0,"monitorLabelScale", "The scale of ship names and player usernames as they appear in the monitor GUI");
    public ConfigBool renderSableSilhouettes = b(true, "renderSableSilhouettes", "Render Sable sublevel hull silhouettes on radar monitors when available");
    public ConfigInt sableSilhouetteMaxRenderDistance = i(2048, 1, "sableSilhouetteMaxRenderDistance", "Maximum target distance from a monitor center for drawing Sable silhouettes");
    public ConfigInt sableSilhouetteMaxRenderedSegments = i(3000, 4, "sableSilhouetteMaxRenderedSegments", "Maximum Sable silhouette line segments rendered per radar contact");
    public ConfigFloat sableSilhouetteCellSize = f(0.35f, 0.1f, "sableSilhouetteCellSize", "Projected grid cell size for Sable radar silhouettes. Lower values make diagonals smoother but cost more render work");
    public ConfigBool sableSilhouetteDebugOverlay = b(false, "sableSilhouetteDebugOverlay", "Render Sable radar silhouettes in a distinct debug color");
    public ConfigBool sableSilhouetteDebugFallbackRectangle = b(false, "sableSilhouetteDebugFallbackRectangle", "Render a simple debug rectangle for Sable contacts whose silhouette geometry is missing");

}
