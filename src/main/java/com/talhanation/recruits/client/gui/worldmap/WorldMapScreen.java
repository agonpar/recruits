package com.talhanation.recruits.client.gui.worldmap;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.compat.SmallShips;
import com.talhanation.recruits.network.MessageDoPayment;
import com.talhanation.recruits.network.MessageUpdateClaim;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsFaction;
import com.talhanation.recruits.world.RecruitsGroup;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import com.talhanation.recruits.world.RecruitsRoute;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.talhanation.recruits.client.ClientManager.ownFaction;


public class WorldMapScreen extends Screen {
    private static final ResourceLocation MAP_ICONS = new ResourceLocation("textures/map/map_icons.png");
    private final ChunkTileManager tileManager;
    private final Player player;
    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 10.0;
    private static final double DEFAULT_SCALE = 2.0;
    private static final double SCALE_STEP = 0.1;
    private static final int CHUNK_HIGHLIGHT_COLOR = 0x40FFFFFF;
    private static final int CHUNK_SELECTION_COLOR = 0xFFFFFFFF;
    private static final int DARK_GRAY_BG = 0xFF101010;
    private double offsetX = 0, offsetZ = 0;
    public static double scale = DEFAULT_SCALE;
    public double lastMouseX, lastMouseY;
    private boolean isDragging = false;
    private ChunkPos hoveredChunk = null;
    ChunkPos selectedChunk = null;
    private int clickedBlockX = 0, clickedBlockZ = 0;
    private int hoverBlockX = 0, hoverBlockZ = 0;
    private WorldMapContextMenu contextMenu;
    RecruitsClaim selectedClaim = null;
    private ClaimInfoMenu claimInfoMenu;
    public RecruitsRoute selectedRoute;
    private GroupIconButton groupsButton;
    private GroupContextMenu groupContextMenu;

    // Store group icon positions for hover tooltips
    private static class GroupIconInfo {
        int x, y, size;
        String name;
        int memberCount;
        RecruitsGroup group;

        GroupIconInfo(int x, int y, int size, String name, int memberCount, RecruitsGroup group) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.name = name;
            this.memberCount = memberCount;
            this.group = group;
        }

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x - size/2 && mouseX <= x + size/2 &&
                   mouseY >= y - size/2 && mouseY <= y + size/2;
        }
    }
    private List<GroupIconInfo> groupIcons = new ArrayList<>();
    private Set<UUID> selectedGroups = new HashSet<>();
    private boolean claimMode = false; // Claim mode disabled by default
    private ClaimModeButton claimModeButton;

    // Right-click drag selection
    private boolean isDraggingRightClick = false;
    private double rightClickStartX, rightClickStartY;
    private ClearSelectionButton clearSelectionButton;
    private ActiveActionsPanel activeActionsPanel;

    // Movement position selection mode
    private boolean selectingMovementPosition = false;
    private int pendingMovementCommand = -1; // -1 = none, 6 = move, 7 = forward, 8 = backward
    private Set<UUID> pendingMovementGroups = new HashSet<>();

    // Store last movement direction per group (for Forward/Backward commands)
    // Key: GroupUUID, Value: [dirX, dirZ] normalized direction vector
    private static final java.util.Map<UUID, double[]> lastMovementDirections = new java.util.HashMap<>();

    // Store target positions for groups (to show X marker and dashed line)
    // Key: GroupUUID, Value: [targetX, targetZ]
    private static final java.util.Map<UUID, int[]> groupTargetPositions = new java.util.HashMap<>();

    // Store groups whose markers have been manually cancelled (to prevent auto-recreation)
    private static final java.util.Set<UUID> cancelledMarkers = new java.util.HashSet<>();

    // Animation offset for dashed lines
    private float dashedLineOffset = 0.0f;

    public WorldMapScreen() {
        super(Component.literal(""));
        this.contextMenu = new WorldMapContextMenu(this);
        this.claimInfoMenu = new ClaimInfoMenu(this);
        this.groupContextMenu = new GroupContextMenu(this);
        this.tileManager = ChunkTileManager.getInstance();
        this.player = Minecraft.getInstance().player;
    }

    public BlockPos getHoveredBlockPos() { return new BlockPos(hoverBlockX, 0, hoverBlockZ); }
    public BlockPos getClickedBlockPos() { return new BlockPos(clickedBlockX, 0, clickedBlockZ); }
    public Player getPlayer() { return player; }
    public boolean isPlayerAdminAndCreative() { return player.hasPermissions(2) && player.isCreative(); }
    public double getScale() { return scale; }
    public void setSelectedChunk(ChunkPos chunk) { this.selectedChunk = chunk; }
    public boolean isClaimMode() { return claimMode; }

    public void startMovementPositionSelection(int movementCommand, Set<UUID> groups) {
        this.selectingMovementPosition = true;
        this.pendingMovementCommand = movementCommand;
        this.pendingMovementGroups = new HashSet<>(groups);
    }

    public boolean hasLastMovementDirection(Set<UUID> groups) {
        if (groups == null || groups.isEmpty()) return false;
        // Check if at least one group has a stored direction
        for (UUID groupUUID : groups) {
            if (lastMovementDirections.containsKey(groupUUID)) {
                return true;
            }
        }
        return false;
    }

    public void removeGroupTargetPosition(UUID groupUUID) {
        groupTargetPositions.remove(groupUUID);
        // Mark as manually cancelled to prevent auto-recreation
        cancelledMarkers.add(groupUUID);
    }

    public void allowGroupTargetMarker(UUID groupUUID) {
        // Remove from cancelled markers to allow new markers
        cancelledMarkers.remove(groupUUID);
    }

    public void ensureTargetMarkerForGroup(UUID groupUUID, net.minecraft.core.BlockPos targetPos) {
        // Don't create markers for groups that have been manually cancelled
        if (cancelledMarkers.contains(groupUUID)) {
            return;
        }

        // Add or update marker position
        if (targetPos != null) {
            int[] existingPos = groupTargetPositions.get(groupUUID);
            int[] newPos = new int[]{targetPos.getX(), targetPos.getZ()};

            // Only update if position changed or doesn't exist
            if (existingPos == null || existingPos[0] != newPos[0] || existingPos[1] != newPos[1]) {
                groupTargetPositions.put(groupUUID, newPos);
            }
        }
    }

    public void executeForwardBackwardMovement(int movementState, Set<UUID> groups) {
        if (groups == null || groups.isEmpty()) return;

        UUID playerUUID = player.getUUID();
        int formation = ClientManager.formationSelection;
        int distance = 10; // 10 blocks forward or backward
        if (movementState == 8) distance = -distance; // Negative for backward

        // Send individual commands for each group using their saved direction
        for (UUID groupUUID : groups) {
            // Remove from cancelled markers (new movement command)
            cancelledMarkers.remove(groupUUID);

            double[] direction = lastMovementDirections.get(groupUUID);
            if (direction != null) {
                // Find group's current position
                for (GroupIconInfo iconInfo : groupIcons) {
                    if (iconInfo.group.getUUID().equals(groupUUID)) {
                        // Convert icon pixel position to world coordinates
                        double groupWorldX = (iconInfo.x - offsetX) / scale;
                        double groupWorldZ = (iconInfo.y - offsetZ) / scale;

                        // Calculate new position
                        int newX = (int)(groupWorldX + direction[0] * distance);
                        int newZ = (int)(groupWorldZ + direction[1] * distance);

                        // Store target position for rendering X marker and dashed line
                        groupTargetPositions.put(groupUUID, new int[]{newX, newZ});

                        // Send command for this group
                        Main.SIMPLE_CHANNEL.sendToServer(
                            new com.talhanation.recruits.network.MessageMovementWithPosition(
                                playerUUID,
                                movementState,
                                groupUUID,
                                formation,
                                newX,
                                newZ
                            )
                        );
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        if (minecraft.level != null && player != null) {
            tileManager.initialize(minecraft.level);
            centerOnPlayer();
        }

        claimInfoMenu.init();

        // Add groups management icon button in top-right corner
        int iconSize = 24;
        int margin = 5;

        groupsButton = new GroupIconButton(
            width - iconSize - margin,
            margin,
            iconSize,
            iconSize,
            button -> openGroupsScreen()
        );

        groupsButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("Manage Groups")
        ));
        addRenderableWidget(groupsButton);

        // Add claim mode toggle button next to groups button
        claimModeButton = new ClaimModeButton(
            width - (iconSize + margin) * 2,
            margin,
            iconSize,
            iconSize,
            claimMode,
            button -> toggleClaimMode()
        );

        claimModeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("Toggle Claim Mode")
        ));
        addRenderableWidget(claimModeButton);

        // Add clear selection button next to claim mode button
        clearSelectionButton = new ClearSelectionButton(
            width - (iconSize + margin) * 3,
            margin,
            iconSize,
            iconSize,
            button -> {
                selectedGroups.clear();
                updateClearSelectionButton();
            }
        );
        clearSelectionButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("Clear Selection")
        ));
        clearSelectionButton.visible = false;
        addRenderableWidget(clearSelectionButton);

        // Add active actions panel below the control buttons
        int panelX = width - 200 - margin;
        int panelY = margin + iconSize + 10; // Below the buttons
        int panelWidth = 200;
        int panelHeight = height - panelY - margin - 30; // Leave space for coordinates at bottom

        activeActionsPanel = new ActiveActionsPanel(this, panelX, panelY, panelWidth, panelHeight, player);
        addRenderableWidget(activeActionsPanel);
    }

    private void toggleClaimMode() {
        claimMode = !claimMode;
        claimModeButton.setClaimModeActive(claimMode);

        // Clear selections when toggling modes
        if (claimMode) {
            // Entering claim mode: clear group selections
            selectedGroups.clear();
        } else {
            // Exiting claim mode: clear chunk selections
            selectedChunk = null;
            hoveredChunk = null;
        }

        updateClearSelectionButton();
    }

    private void openGroupsScreen() {
        if (minecraft != null && player != null) {
            minecraft.setScreen(new com.talhanation.recruits.client.gui.group.RecruitsGroupListScreen(player));
        }
    }

    private void updateClearSelectionButton() {
        if (clearSelectionButton != null) {
            clearSelectionButton.visible = !selectedGroups.isEmpty() && !claimMode;
        }
    }

    public void centerOnPlayer() {
        if (player != null) {
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            double pixelX = chunkX * 16 * scale;
            double pixelZ = chunkZ * 16 * scale;
            offsetX = -pixelX + width / 2.0;
            offsetZ = -pixelZ + height / 2.0;
        }
    }

    private void sendMovementCommandWithPosition(int movementState, Set<UUID> groups, int worldX, int worldZ) {
        if (groups == null || groups.isEmpty()) return;

        UUID playerUUID = player.getUUID();
        int formation = ClientManager.formationSelection;

        // For Move command (state 6), save the direction for each group
        if (movementState == 6) {
            for (UUID groupUUID : groups) {
                // Remove from cancelled markers (new movement command)
                cancelledMarkers.remove(groupUUID);

                // Calculate direction from group's current position to target
                for (GroupIconInfo iconInfo : groupIcons) {
                    if (iconInfo.group.getUUID().equals(groupUUID)) {
                        // Convert icon pixel position to world coordinates
                        double groupWorldX = (iconInfo.x - offsetX) / scale;
                        double groupWorldZ = (iconInfo.y - offsetZ) / scale;

                        // Calculate direction vector
                        double dirX = worldX - groupWorldX;
                        double dirZ = worldZ - groupWorldZ;

                        // Normalize the direction vector
                        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
                        if (length > 0.001) { // Avoid division by zero
                            dirX /= length;
                            dirZ /= length;

                            // Store the normalized direction
                            lastMovementDirections.put(groupUUID, new double[]{dirX, dirZ});
                        }

                        // Store target position for rendering X marker and dashed line
                        groupTargetPositions.put(groupUUID, new int[]{worldX, worldZ});

                        break;
                    }
                }
            }
        }

        // Send Move command to all groups with the selected position
        for (UUID groupUUID : groups) {
            Main.SIMPLE_CHANNEL.sendToServer(
                new com.talhanation.recruits.network.MessageMovementWithPosition(
                    playerUUID,
                    movementState,
                    groupUUID,
                    formation,
                    worldX,
                    worldZ
                )
            );
        }
    }

    public void resetZoom() {
        scale = DEFAULT_SCALE;
        centerOnPlayer();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(guiGraphics);

        guiGraphics.enableScissor(0, 0, width, height);

        renderMapTiles(guiGraphics);

        ClaimRenderer.renderClaimsOverlay(guiGraphics, this.selectedClaim, this.offsetX, this.offsetZ, scale);

        // Only render claim-related elements when in claim mode
        if (claimMode) {
            if (contextMenu.isVisible()) {
                String entryTag = contextMenu.getHoveredEntryTag();
                if (entryTag != null){
                    if(entryTag.contains("bufferzone")) {
                        ClaimRenderer.renderBufferZone(guiGraphics, offsetX, offsetZ, scale);
                    }
                    if(entryTag.contains("area")){
                        ClaimRenderer.renderAreaPreview(guiGraphics, getClaimArea(selectedChunk) , offsetX, offsetZ, scale);
                    }
                    if(entryTag.contains("chunk")){
                        ClaimRenderer.renderAreaPreview(guiGraphics, getClaimableChunks(selectedChunk, 16) , offsetX, offsetZ, scale);
                    }
                }
            }

            if (selectedChunk != null && (selectedClaim == null || contextMenu.isVisible())) {
                renderChunkOutline(guiGraphics, selectedChunk.x, selectedChunk.z, CHUNK_SELECTION_COLOR);
            }

            if (hoveredChunk != null) {
                renderChunkHighlight(guiGraphics, hoveredChunk.x, hoveredChunk.z);
            }
        }

        if (player != null) {
            renderPlayerPosition(guiGraphics);
        }

        renderRecruitGroups(guiGraphics);

        // Render target markers and dashed lines for groups with active movement commands
        renderGroupTargetMarkers(guiGraphics);

        if(selectedRoute != null){
            renderRoute(guiGraphics);
        }

        guiGraphics.disableScissor();

        renderCoordinatesAndZoom(guiGraphics);

        //renderFPS(guiGraphics);

        // Render widgets (buttons)
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Render right-click selection rectangle
        if (isDraggingRightClick && !claimMode) {
            int x1 = (int) Math.min(rightClickStartX, mouseX);
            int y1 = (int) Math.min(rightClickStartY, mouseY);
            int x2 = (int) Math.max(rightClickStartX, mouseX);
            int y2 = (int) Math.max(rightClickStartY, mouseY);

            // Fill with semi-transparent color
            guiGraphics.fill(x1, y1, x2, y2, 0x4000FF00);

            // Border
            guiGraphics.renderOutline(x1, y1, x2 - x1, y2 - y1, 0xFF00FF00);
        }

        // Render movement position selection indicator
        if (selectingMovementPosition) {
            // Draw X at mouse position (green color)
            int xSize = 6; // Smaller X (was 10 for crosshair)
            int xX = mouseX;
            int xY = mouseY;
            int thickness = 2;

            int color = 0xFF00FF00; // Green color

            // Draw X - two diagonal lines
            // Line from top-left to bottom-right
            for (int i = -xSize; i <= xSize; i++) {
                for (int t = 0; t < thickness; t++) {
                    guiGraphics.fill(
                        xX + i, xY + i + t,
                        xX + i + 1, xY + i + t + 1,
                        color
                    );
                }
            }

            // Line from top-right to bottom-left
            for (int i = -xSize; i <= xSize; i++) {
                for (int t = 0; t < thickness; t++) {
                    guiGraphics.fill(
                        xX + i, xY - i + t,
                        xX + i + 1, xY - i + t + 1,
                        color
                    );
                }
            }

            // Draw instruction text
            String instruction = "Click to select movement position (ESC to cancel)";
            int textWidth = font.width(instruction);
            int textX = width / 2 - textWidth / 2;
            int textY = 20;

            guiGraphics.fill(textX - 4, textY - 2, textX + textWidth + 4, textY + 12, 0xAA000000);
            guiGraphics.drawString(font, instruction, textX, textY, 0xFFFFFF00);
        }

        // Render group icon tooltips when hovering (especially when zoom is low and text is hidden)
        if (scale <= 1.5 && !selectingMovementPosition) {
            for (GroupIconInfo iconInfo : groupIcons) {
                if (iconInfo.contains(mouseX, mouseY)) {
                    renderGroupTooltip(guiGraphics, iconInfo, mouseX, mouseY);
                    break; // Only show one tooltip at a time
                }
            }
        }

        if (selectedClaim != null && claimInfoMenu.isVisible()) {
            Point p = getClaimInfoMenuPosition(selectedClaim, claimInfoMenu.width, claimInfoMenu.height
            );
            claimInfoMenu.setPosition(p.x, p.y);
            claimInfoMenu.render(guiGraphics);
        }

        // Render context menus LAST to ensure they're on top of everything including coordinates
        contextMenu.render(guiGraphics, this);
        groupContextMenu.render(guiGraphics, this);
    }

    private void renderGroupTooltip(GuiGraphics guiGraphics, GroupIconInfo iconInfo, int mouseX, int mouseY) {
        // Prepare tooltip text
        String groupName = iconInfo.name;
        String troopCount = "Troops: " + iconInfo.memberCount;

        // Calculate tooltip dimensions
        int nameWidth = font.width(groupName);
        int countWidth = font.width(troopCount);
        int tooltipWidth = Math.max(nameWidth, countWidth) + 8;
        int tooltipHeight = font.lineHeight * 2 + 6;

        // Position tooltip near mouse
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 12;

        // Keep tooltip on screen
        if (tooltipX + tooltipWidth > width) tooltipX = mouseX - tooltipWidth - 12;
        if (tooltipY + tooltipHeight > height) tooltipY = height - tooltipHeight;
        if (tooltipY < 0) tooltipY = 0;

        // Draw tooltip background
        guiGraphics.fill(tooltipX - 3, tooltipY - 3, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF0100010);
        guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipWidth - 1, tooltipY + tooltipHeight - 1, 0x505000FF);
        guiGraphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth - 2, tooltipY + tooltipHeight - 2, 0x5028007F);

        // Draw tooltip text
        guiGraphics.drawString(font, groupName, tooltipX + 2, tooltipY + 2, 0xFFFFAA, false);
        guiGraphics.drawString(font, troopCount, tooltipX + 2, tooltipY + 2 + font.lineHeight + 2, 0xFFFFFF, false);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, width, height, DARK_GRAY_BG);
    }

    private void renderMapTiles(GuiGraphics guiGraphics) {
        double tileSize = ChunkTile.TILE_PIXEL_SIZE;
        double scaledTileSize = tileSize * scale;

        double leftEdge = -offsetX;
        double rightEdge = width - offsetX;
        double topEdge = -offsetZ;
        double bottomEdge = height - offsetZ;

        int startTileX = (int)Math.floor(leftEdge / scaledTileSize - 0.5);
        int endTileX = (int)Math.ceil(rightEdge / scaledTileSize + 0.5);
        int startTileZ = (int)Math.floor(topEdge / scaledTileSize - 0.5);
        int endTileZ = (int)Math.ceil(bottomEdge / scaledTileSize + 0.5);


        for (int tileZ = startTileZ; tileZ <= endTileZ; tileZ++) {
            for (int tileX = startTileX; tileX <= endTileX; tileX++) {
                ChunkTile tile = tileManager.getOrCreateTile(tileX, tileZ);
                ResourceLocation textureId = tile.getTextureId();

                if (textureId == null) continue;


                double tileWorldX = tileX * scaledTileSize + offsetX;
                double tileWorldZ = tileZ * scaledTileSize + offsetZ;

                double drawX = tileWorldX - 0.5;
                double drawZ = tileWorldZ - 0.5;
                double drawSize = scaledTileSize + 1.0;


                int x = (int)Math.floor(drawX);
                int z = (int)Math.floor(drawZ);
                int size = (int)Math.ceil(drawSize);

                if (Math.abs(scale - 1.0) < 0.01) {
                    x = (int)Math.round(tileWorldX);
                    z = (int)Math.round(tileWorldZ);
                    size = (int)Math.round(scaledTileSize);
                }

                RenderSystem.setShaderTexture(0, textureId);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                guiGraphics.blit(textureId, x, z, 0, 0, size, size, size, size);
            }
        }
    }

    public void renderRoute(GuiGraphics guiGraphics){
        if(selectedRoute.getWaypoints().isEmpty()) return;

        List<RecruitsRoute.Waypoint> waypoints = selectedRoute.getWaypoints();
        for(RecruitsRoute.Waypoint waypoint : waypoints){
            if (waypoint == null) return;

            double playerWorldX = waypoint.getPosition().getX();
            double playerWorldZ = waypoint.getPosition().getZ();

            int pixelX = (int)(offsetX + playerWorldX * scale);
            int pixelZ = (int)(offsetZ + playerWorldZ * scale);

            PoseStack pose = guiGraphics.pose();

            pose.pushPose();
            pose.translate(pixelX, pixelZ, 0);
            pose.popPose();

            pose.scale(3.0f, 3.0f, 3.0f);
            int iconIndex = 6;
            float u0 = (iconIndex % 16) / 16f;
            float v0 = (iconIndex / 16) / 16f;
            float u1 = u0 + 1f / 16f;
            float v1 = v0 + 1f / 16f;

            guiGraphics.flush();
            VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.text(MAP_ICONS));
            Matrix4f matrix = pose.last().pose();
            int light = 0xF000F0;
            int color = 0xFFFFFFFF;

            consumer.vertex(matrix, -1f, 1f, 0f)
                    .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                    .uv(u0, v0)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(0, 0, 1)
                    .endVertex();

            consumer.vertex(matrix, 1f, 1f, 0f)
                    .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                    .uv(u1, v0)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(0, 0, 1)
                    .endVertex();

            consumer.vertex(matrix, 1f, -1f, 0f)
                    .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                    .uv(u1, v1)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(0, 0, 1)
                    .endVertex();

            consumer.vertex(matrix, -1f, -1f, 0f)
                    .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                    .uv(u0, v1)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(0, 0, 1)
                    .endVertex();

            pose.popPose();
        }
    }

    public void renderWaypoint(GuiGraphics guiGraphics){

    }

    private void renderRecruitGroups(GuiGraphics guiGraphics) {
        if (player == null || minecraft.level == null) return;

        List<RecruitsGroup> playerGroups = ClientManager.groups;
        if (playerGroups == null || playerGroups.isEmpty()) return;

        // Clear group icons list for this frame
        groupIcons.clear();

        // Get recruits in a very large area (all loaded chunks)
        int searchRadius = 100000;
        net.minecraft.world.phys.AABB searchBox = player.getBoundingBox().inflate(searchRadius);

        List<com.talhanation.recruits.entities.AbstractRecruitEntity> recruits =
            minecraft.level.getEntitiesOfClass(
                com.talhanation.recruits.entities.AbstractRecruitEntity.class,
                searchBox
            );

        // Filter recruits to only show those belonging to the player's faction
        recruits.removeIf(recruit -> !isRecruitVisibleToPlayer(recruit));

        // Group recruits by their group UUID
        Map<UUID, List<com.talhanation.recruits.entities.AbstractRecruitEntity>> recruitsByGroup = new HashMap<>();

        for (com.talhanation.recruits.entities.AbstractRecruitEntity recruit : recruits) {
            UUID groupUUID = recruit.getGroup();
            if (groupUUID != null) {
                recruitsByGroup.computeIfAbsent(groupUUID, k -> new ArrayList<>()).add(recruit);
            }
        }

        // Track which groups we've rendered from loaded entities
        Set<UUID> renderedGroups = new HashSet<>();

        // Render groups that have recruits in loaded chunks
        for (RecruitsGroup group : playerGroups) {
            List<com.talhanation.recruits.entities.AbstractRecruitEntity> groupRecruits =
                recruitsByGroup.get(group.getUUID());

            if (groupRecruits != null && !groupRecruits.isEmpty()) {
                // Cluster recruits by proximity
                List<RecruitCluster> clusters = clusterRecruits(groupRecruits, 24.0);

                // Render an icon for each cluster
                for (RecruitCluster cluster : clusters) {
                    int pixelX = (int)(offsetX + cluster.centerX * scale);
                    int pixelZ = (int)(offsetZ + cluster.centerZ * scale);
                    renderGroupIcon(guiGraphics, pixelX, pixelZ, group, cluster.recruits.size());
                }

                renderedGroups.add(group.getUUID());
            }
        }

        // Render groups from stored position data (for groups in unloaded chunks)
        for (RecruitsGroup group : playerGroups) {
            if (renderedGroups.contains(group.getUUID())) continue;

            com.talhanation.recruits.world.GroupPositionData posData =
                ClientManager.groupPositions.get(group.getUUID());

            if (posData != null && posData.getMemberCount() > 0) {
                int pixelX = (int)(offsetX + posData.getX() * scale);
                int pixelZ = (int)(offsetZ + posData.getZ() * scale);
                renderGroupIcon(guiGraphics, pixelX, pixelZ, group, posData.getMemberCount());
            }
        }

        // Update active actions panel
        if (activeActionsPanel != null) {
            activeActionsPanel.updateActions(playerGroups, recruits);
        }
    }

    private boolean isRecruitVisibleToPlayer(com.talhanation.recruits.entities.AbstractRecruitEntity recruit) {
        if (recruit == null || player == null) return false;

        // Check if the recruit belongs to the player or a faction member
        UUID recruitOwner = recruit.getOwnerUUID();
        if (recruitOwner == null) return false;

        // If player owns this recruit directly, show it
        if (recruitOwner.equals(player.getUUID())) return true;

        // If no faction system, only show own recruits
        if (ownFaction == null) return false;

        // Check if the recruit's owner is in the same faction
        // Get the recruit's team and compare with player's faction
        net.minecraft.world.scores.Team recruitTeam = recruit.getTeam();
        if (recruitTeam == null) return false;

        String recruitTeamName = recruitTeam.getName();
        String playerTeamName = ownFaction.getStringID();

        return recruitTeamName.equals(playerTeamName);
    }

    private List<RecruitCluster> clusterRecruits(List<com.talhanation.recruits.entities.AbstractRecruitEntity> recruits, double maxDistance) {
        List<RecruitCluster> clusters = new ArrayList<>();
        if (recruits.isEmpty()) return clusters;

        // Use squared distance to avoid expensive sqrt calculations
        double maxDistanceSquared = maxDistance * maxDistance;

        // Track which recruits have been assigned to a cluster
        Set<com.talhanation.recruits.entities.AbstractRecruitEntity> assigned = new HashSet<>();

        for (com.talhanation.recruits.entities.AbstractRecruitEntity seed : recruits) {
            if (assigned.contains(seed)) continue;

            // Start a new cluster
            RecruitCluster cluster = new RecruitCluster();
            cluster.recruits.add(seed);
            assigned.add(seed);

            // Use a queue to process cluster members iteratively
            Queue<com.talhanation.recruits.entities.AbstractRecruitEntity> toProcess = new LinkedList<>();
            toProcess.add(seed);

            while (!toProcess.isEmpty()) {
                com.talhanation.recruits.entities.AbstractRecruitEntity current = toProcess.poll();

                for (com.talhanation.recruits.entities.AbstractRecruitEntity candidate : recruits) {
                    if (assigned.contains(candidate)) continue;

                    double dx = candidate.getX() - current.getX();
                    double dz = candidate.getZ() - current.getZ();
                    double distanceSquared = dx * dx + dz * dz;

                    if (distanceSquared <= maxDistanceSquared) {
                        cluster.recruits.add(candidate);
                        assigned.add(candidate);
                        toProcess.add(candidate);
                    }
                }
            }

            // Calculate cluster center
            cluster.calculateCenter();
            clusters.add(cluster);
        }

        return clusters;
    }

    private static class RecruitCluster {
        List<com.talhanation.recruits.entities.AbstractRecruitEntity> recruits = new ArrayList<>();
        double centerX;
        double centerZ;

        void calculateCenter() {
            if (recruits.isEmpty()) return;

            double sumX = 0;
            double sumZ = 0;

            for (com.talhanation.recruits.entities.AbstractRecruitEntity recruit : recruits) {
                sumX += recruit.getX();
                sumZ += recruit.getZ();
            }

            centerX = sumX / recruits.size();
            centerZ = sumZ / recruits.size();
        }
    }

    private void renderGroupIcon(GuiGraphics guiGraphics, int pixelX, int pixelZ, RecruitsGroup group, int memberCount) {
        PoseStack pose = guiGraphics.pose();

        // Get faction color
        int factionColor = 0xFFFFFFFF; // Default white
        if (ownFaction != null) {
            net.minecraft.ChatFormatting chatColor = net.minecraft.ChatFormatting.getById(ownFaction.getTeamColor());
            if (chatColor != null && chatColor.getColor() != null) {
                factionColor = 0xFF000000 | chatColor.getColor();
            }
        }

        pose.pushPose();
        pose.translate(pixelX, pixelZ, 0);

        // Scale based on zoom level - much smaller
        float iconScale = (float)Math.max(0.8, Math.min(1.5, scale * 0.5));
        pose.scale(iconScale, iconScale, iconScale);

        // Get group image
        int imageIndex = group.getImage();
        if (imageIndex >= 0 && imageIndex < RecruitsGroup.IMAGES.size()) {
            ResourceLocation groupTexture = RecruitsGroup.IMAGES.get(imageIndex);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(
                ((factionColor >> 16) & 0xFF) / 255.0f,
                ((factionColor >> 8) & 0xFF) / 255.0f,
                (factionColor & 0xFF) / 255.0f,
                ((factionColor >> 24) & 0xFF) / 255.0f
            );

            // Draw the group icon (16x16 texture)
            guiGraphics.blit(groupTexture, -8, -8, 0, 0, 16, 16, 16, 16);

            // Reset shader color
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            // Draw selection border if this group is selected
            if (selectedGroups.contains(group.getUUID())) {
                RenderSystem.disableBlend();
                guiGraphics.fill(-9, -9, 9, -8, 0xFFFFFF00); // Top
                guiGraphics.fill(-9, 8, 9, 9, 0xFFFFFF00);   // Bottom
                guiGraphics.fill(-9, -9, -8, 9, 0xFFFFFF00); // Left
                guiGraphics.fill(8, -9, 9, 9, 0xFFFFFF00);   // Right
                RenderSystem.enableBlend();
            }
        }

        pose.popPose();

        // Store icon info for hover detection
        int iconSize = (int)(16 * iconScale);
        groupIcons.add(new GroupIconInfo(pixelX, pixelZ, iconSize, group.getName(), memberCount, group));

        // Render member count and group name if zoom is high enough
        if (scale > 1.5) {
            float textScale = (float)Math.min(0.7, scale / 3.0);

            // Render member count
            String countText = String.valueOf(memberCount);
            int countWidth = font.width(countText);

            pose.pushPose();
            pose.translate(pixelX - (countWidth * textScale) / 2.0, pixelZ + 8, 0);
            pose.scale(textScale, textScale, 1.0f);

            // Draw background for better visibility
            guiGraphics.fill(-1, -1, countWidth + 1, font.lineHeight + 1, 0x80000000);
            guiGraphics.drawString(font, countText, 0, 0, 0xFFFFFF, false);

            pose.popPose();

            // Render group name below count
            String groupName = group.getName();
            int nameWidth = font.width(groupName);

            pose.pushPose();
            pose.translate(pixelX - (nameWidth * textScale) / 2.0, pixelZ + 8 + (font.lineHeight + 2) * textScale, 0);
            pose.scale(textScale, textScale, 1.0f);

            // Draw background for better visibility
            guiGraphics.fill(-1, -1, nameWidth + 1, font.lineHeight + 1, 0x80000000);
            guiGraphics.drawString(font, groupName, 0, 0, 0xFFFFAA, false);

            pose.popPose();
        }
    }

    private void renderGroupTargetMarkers(GuiGraphics guiGraphics) {
        if (groupTargetPositions.isEmpty()) return;

        // Update dashed line animation
        dashedLineOffset += 0.5f;
        if (dashedLineOffset > 10.0f) {
            dashedLineOffset = 0.0f;
        }

        // Iterate through groups to find their current positions and render targets
        for (GroupIconInfo iconInfo : groupIcons) {
            UUID groupUUID = iconInfo.group.getUUID();
            int[] target = groupTargetPositions.get(groupUUID);

            if (target != null) {
                // Current position (from icon)
                int currentPixelX = iconInfo.x;
                int currentPixelY = iconInfo.y;

                // Target position
                int targetPixelX = (int)(offsetX + target[0] * scale);
                int targetPixelY = (int)(offsetZ + target[1] * scale);

                // Check if group has reached the target (within 5 blocks)
                double groupWorldX = (currentPixelX - offsetX) / scale;
                double groupWorldZ = (currentPixelY - offsetZ) / scale;
                double distance = Math.sqrt(
                    Math.pow(target[0] - groupWorldX, 2) +
                    Math.pow(target[1] - groupWorldZ, 2)
                );

                if (distance < 5.0) {
                    // Group has reached target, remove marker
                    groupTargetPositions.remove(groupUUID);
                    // Also remove from cancelled markers (they arrived naturally)
                    cancelledMarkers.remove(groupUUID);
                    continue;
                }

                // Draw dashed line from current position to target
                renderDashedLine(guiGraphics, currentPixelX, currentPixelY, targetPixelX, targetPixelY);

                // Draw green X at target position
                renderTargetX(guiGraphics, targetPixelX, targetPixelY);
            }
        }
    }

    private void renderDashedLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2) {
        // Calculate line direction and length
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 1) return;

        // Normalize direction
        dx /= length;
        dy /= length;

        // Draw dashed segments
        double dashLength = 5.0;
        double gapLength = 5.0;
        double totalSegment = dashLength + gapLength;

        int color = 0xFF00FF00; // Green color

        for (double dist = dashedLineOffset; dist < length; dist += totalSegment) {
            double dashEnd = Math.min(dist + dashLength, length);

            int startX = (int)(x1 + dx * dist);
            int startY = (int)(y1 + dy * dist);
            int endX = (int)(x1 + dx * dashEnd);
            int endY = (int)(y1 + dy * dashEnd);

            // Draw thick line (2 pixels)
            guiGraphics.fill(startX, startY, endX + 1, startY + 2, color);
            guiGraphics.fill(startX, startY, startX + 2, endY + 1, color);
        }
    }

    private void renderTargetX(GuiGraphics guiGraphics, int x, int y) {
        int xSize = 6;
        int thickness = 2;
        int color = 0xFF00FF00; // Green color

        // Draw X - two diagonal lines
        // Line from top-left to bottom-right
        for (int i = -xSize; i <= xSize; i++) {
            for (int t = 0; t < thickness; t++) {
                guiGraphics.fill(
                    x + i, y + i + t,
                    x + i + 1, y + i + t + 1,
                    color
                );
            }
        }

        // Line from top-right to bottom-left
        for (int i = -xSize; i <= xSize; i++) {
            for (int t = 0; t < thickness; t++) {
                guiGraphics.fill(
                    x + i, y - i + t,
                    x + i + 1, y - i + t + 1,
                    color
                );
            }
        }
    }

    private static final ItemStack BOAT_STACK = new ItemStack(Items.OAK_BOAT);
    private void renderPlayerPosition(GuiGraphics guiGraphics) {
        if (player == null) return;

        double playerWorldX = player.getX();
        double playerWorldZ = player.getZ();

        int pixelX = (int)(offsetX + playerWorldX * scale);
        int pixelZ = (int)(offsetZ + playerWorldZ * scale);

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();

        pose.translate(pixelX, pixelZ, 0);

        if(player.getVehicle() instanceof Boat){
            renderPlayerBoat(pose, guiGraphics);
        }
        else{
            renderPlayerIcon(pose, guiGraphics);
        }

        pose.popPose();

        renderPlayerNameTag(guiGraphics, pixelX, pixelZ);
    }

    private void renderPlayerBoat(PoseStack pose, GuiGraphics guiGraphics){
        float yaw = player.getYRot() % 360f;
        if (yaw < -180f) yaw += 360f;
        if (yaw >= 180f) yaw -= 360f;

        boolean flipX = yaw > 0;

        pose.pushPose();

        if (flipX) {
            pose.scale(-1f, 1f, 1f);
        }

        pose.scale(1.5f, 1.5f, 1.5f);

        Lighting.setupForFlatItems();

        ItemStack boat = BOAT_STACK;
        if(Main.isSmallShipsLoaded ){
            if(player.getVehicle() != null && SmallShips.isSmallShip(player.getVehicle())){
                 boat = SmallShips.getSmallShipsItem();
            }
        }

        RenderSystem.disableCull();
        guiGraphics.renderItem(boat, -8, -8);
        RenderSystem.enableCull();

        pose.popPose();
    }

    private void renderPlayerIcon(PoseStack pose, GuiGraphics guiGraphics) {
        pose.mulPose(Axis.ZP.rotationDegrees(player.getYRot()));

        pose.scale(5.0f, 5.0f, 5.0f);
        int iconIndex = 0;
        float u0 = (iconIndex % 16) / 16f;
        float v0 = (iconIndex / 16) / 16f;
        float u1 = u0 + 1f / 16f;
        float v1 = v0 + 1f / 16f;

        guiGraphics.flush();
        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.text(MAP_ICONS));
        Matrix4f matrix = pose.last().pose();
        int light = 0xF000F0;
        int color = 0xFFFFFFFF;

        consumer.vertex(matrix, -1f, 1f, 0f)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                .uv(u0, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(0, 0, 1)
                .endVertex();

        consumer.vertex(matrix, 1f, 1f, 0f)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                .uv(u1, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(0, 0, 1)
                .endVertex();

        consumer.vertex(matrix, 1f, -1f, 0f)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                .uv(u1, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(0, 0, 1)
                .endVertex();

        consumer.vertex(matrix, -1f, -1f, 0f)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
                .uv(u0, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(0, 0, 1)
                .endVertex();

    }

    private void renderPlayerNameTag(GuiGraphics guiGraphics, int pixelX, int pixelZ) {
        if (player != null && scale > 1.5) {
            String playerName = player.getName().getString();
            float textScale = (float)Math.min(1.0, scale / 1.25);
            int textWidth = font.width(playerName);
            int textHeight = font.lineHeight;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(pixelX - (textWidth * textScale) / 2.0, pixelZ - (textHeight * textScale) / 2.0 - 10, 0);
            guiGraphics.pose().scale(textScale, textScale, 1.0f);
            guiGraphics.drawString(font, playerName, 0, 0, 0xFFFFFF, false);
            guiGraphics.pose().popPose();
        }
    }

    private void renderChunkHighlight(GuiGraphics guiGraphics, int chunkX, int chunkZ) {
        int pixelX = (int)(offsetX + chunkX * 16 * scale);
        int pixelZ = (int)(offsetZ + chunkZ * 16 * scale);
        int size = (int)(16 * scale);
        guiGraphics.fill(pixelX, pixelZ, pixelX + size, pixelZ + size, CHUNK_HIGHLIGHT_COLOR);
    }

    private void renderChunkOutline(GuiGraphics guiGraphics, int chunkX, int chunkZ, int color) {
        int pixelX = (int)(offsetX + chunkX * 16 * scale);
        int pixelZ = (int)(offsetZ + chunkZ * 16 * scale);
        int size = (int)(16 * scale);

        guiGraphics.hLine(pixelX, pixelX + size, pixelZ, color);
        guiGraphics.hLine(pixelX, pixelX + size, pixelZ + size, color);
        guiGraphics.vLine(pixelX, pixelZ, pixelZ + size, color);
        guiGraphics.vLine(pixelX + size, pixelZ, pixelZ + size, color);
    }

    private void renderCoordinatesAndZoom(GuiGraphics guiGraphics) {
        String coords = String.format("X: %d, Z: %d", hoverBlockX, hoverBlockZ);
        String zoom = String.format("Zoom: %.1fx", scale);
        String combined = coords + " | " + zoom;

        int textWidth = font.width(combined);

        int bgX = width / 2 - textWidth / 2 - 8;
        int bgY = height - 30;
        int bgWidth = textWidth + 16;

        guiGraphics.fill(bgX, bgY, bgX + bgWidth, bgY + 20, 0x80000000);
        guiGraphics.renderOutline(bgX, bgY, bgWidth, 20, 0x40FFFFFF);

        guiGraphics.drawCenteredString(font, combined, width / 2, height - 25, 0xFFFFFF);
    }

    public void centerOnClaim(RecruitsClaim claim) {
        if (claim == null || claim.getCenter() == null) return;

        ChunkPos center = claim.getCenter();
        double pixelX = center.x * 16 * scale;
        double pixelZ = center.z * 16 * scale;

        offsetX = -pixelX + width / 2.0;
        offsetZ = -pixelZ + height / 2.0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle movement position selection mode (only if no menus are visible)
        if (selectingMovementPosition && button == 0 && !groupContextMenu.isVisible() && !contextMenu.isVisible()) {
            // Calculate world position from mouse click
            double worldX = (mouseX - offsetX) / scale;
            double worldZ = (mouseY - offsetZ) / scale;

            // Send movement command with position
            sendMovementCommandWithPosition(pendingMovementCommand, pendingMovementGroups, (int)worldX, (int)worldZ);

            // Exit selection mode
            selectingMovementPosition = false;
            pendingMovementCommand = -1;
            pendingMovementGroups.clear();
            return true;
        }

        // Check Group Context Menu first
        if (groupContextMenu.isVisible() && groupContextMenu.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Check Claim Info Menu
        if (claimInfoMenu.isVisible() && claimInfoMenu.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (contextMenu.isVisible()) {
            if (contextMenu.mouseClicked(mouseX, mouseY, button, this)) {
                return true;
            }
        }

        // CLAIM MODE: Handle chunk and claim interactions
        if (claimMode) {
            if(hoveredChunk != null)  selectedChunk = hoveredChunk;

            RecruitsClaim clickedClaim = ClaimRenderer.getClaimAtPosition(mouseX, mouseY, offsetX, offsetZ, scale);
            if (clickedClaim != null) {
                selectedClaim = clickedClaim;
                claimInfoMenu.openForClaim(selectedClaim, (int) mouseX, (int) mouseY);
            }
            else {
                selectedClaim = null;
                claimInfoMenu.close();
            }

            if (button == 1) { // Right click - open context menu for claiming
                double worldX = (mouseX - offsetX) / scale;
                double worldZ = (mouseY - offsetZ) / scale;
                clickedBlockX = (int) Math.floor(worldX);
                clickedBlockZ = (int) Math.floor(worldZ);

                this.contextMenu = new WorldMapContextMenu(this);
                contextMenu.openAt((int) mouseX, (int) mouseY);
                claimInfoMenu.close();
            }

            if (button == 0) { // Left click - prepare for dragging
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                isDragging = true;
            }
        }
        // TROOP MODE: Handle group interactions
        else {
            // Don't handle group interactions if we're in movement position selection mode
            if (!selectingMovementPosition) {
                if (button == 0) { // Left click
                    // Check if clicking on a group icon
                    boolean clickedOnGroup = false;
                    for (GroupIconInfo iconInfo : groupIcons) {
                        if (iconInfo.contains((int)mouseX, (int)mouseY)) {
                            clickedOnGroup = true;

                            // Check if Ctrl is pressed
                            boolean isCtrlPressed = Screen.hasControlDown();

                            if (isCtrlPressed) {
                                // Toggle selection of this group
                                if (selectedGroups.contains(iconInfo.group.getUUID())) {
                                    selectedGroups.remove(iconInfo.group.getUUID());
                                } else {
                                    selectedGroups.add(iconInfo.group.getUUID());
                                }
                            } else {
                                // Clear previous selection and select only this group
                                selectedGroups.clear();
                                selectedGroups.add(iconInfo.group.getUUID());
                            }

                            break;
                        }
                    }

                    // If didn't click on a group, prepare for dragging
                    if (!clickedOnGroup) {
                        lastMouseX = mouseX;
                        lastMouseY = mouseY;
                        isDragging = true;
                    }

                    updateClearSelectionButton();
                }

                if (button == 1) { // Right click - start drag selection
                    // Close any open menus first
                    contextMenu.close();
                    groupContextMenu.close();
                    claimInfoMenu.close();

                    // Start right-click drag selection
                    rightClickStartX = mouseX;
                    rightClickStartY = mouseY;
                    isDraggingRightClick = true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (contextMenu.isVisible()) {
            return false;
        }

        if (button == 0) {
            isDragging = false;
        }

        if (button == 1 && isDraggingRightClick) {
            isDraggingRightClick = false;

            if (!claimMode) {
                // Calculate selection rectangle
                int x1 = (int) Math.min(rightClickStartX, mouseX);
                int y1 = (int) Math.min(rightClickStartY, mouseY);
                int x2 = (int) Math.max(rightClickStartX, mouseX);
                int y2 = (int) Math.max(rightClickStartY, mouseY);

                // Check if it was a small drag (essentially a click)
                boolean wasClick = Math.abs(mouseX - rightClickStartX) < 5 && Math.abs(mouseY - rightClickStartY) < 5;

                if (wasClick) {
                    // Single click behavior - check if clicking on a group
                    boolean clickedOnGroup = false;
                    for (GroupIconInfo iconInfo : groupIcons) {
                        if (iconInfo.contains((int)mouseX, (int)mouseY)) {
                            clickedOnGroup = true;

                            // If the group is not selected, select it exclusively
                            if (!selectedGroups.contains(iconInfo.group.getUUID())) {
                                selectedGroups.clear();
                                selectedGroups.add(iconInfo.group.getUUID());
                            }

                            // Open group context menu
                            contextMenu.close();
                            groupContextMenu.openAt((int) mouseX, (int) mouseY, selectedGroups);
                            updateClearSelectionButton();
                            return true;
                        }
                    }

                    // Didn't click on a group - open normal context menu
                    if (!clickedOnGroup) {
                        groupContextMenu.close();

                        double worldX = (mouseX - offsetX) / scale;
                        double worldZ = (mouseY - offsetZ) / scale;
                        clickedBlockX = (int) Math.floor(worldX);
                        clickedBlockZ = (int) Math.floor(worldZ);

                        this.contextMenu = new WorldMapContextMenu(this);
                        contextMenu.openAt((int) mouseX, (int) mouseY);
                        claimInfoMenu.close();
                        return true;
                    }
                } else {
                    // Drag selection - select/deselect groups in rectangle
                    boolean isCtrlPressed = Screen.hasControlDown();

                    if (!isCtrlPressed) {
                        selectedGroups.clear();
                    }

                    for (GroupIconInfo iconInfo : groupIcons) {
                        if (iconInfo.x >= x1 && iconInfo.x <= x2 &&
                            iconInfo.y >= y1 && iconInfo.y <= y2) {

                            if (isCtrlPressed && selectedGroups.contains(iconInfo.group.getUUID())) {
                                // Ctrl pressed and already selected - deselect
                                selectedGroups.remove(iconInfo.group.getUUID());
                            } else {
                                // Add to selection
                                selectedGroups.add(iconInfo.group.getUUID());
                            }
                        }
                    }

                    // Open group context menu if there are selected groups
                    if (!selectedGroups.isEmpty()) {
                        contextMenu.close();
                        groupContextMenu.openAt((int) mouseX, (int) mouseY, selectedGroups);
                    }

                    updateClearSelectionButton();
                }
            }
        }

        if (claimInfoMenu.isVisible()) {
            claimInfoMenu.mouseReleased(mouseX, mouseY, button);
        }

        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging) {
            offsetX += mouseX - lastMouseX;
            offsetZ += mouseY - lastMouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;

            if (claimInfoMenu.isVisible()) {
                claimInfoMenu.close();
            }
            return true;
        }

        if (claimInfoMenu.isVisible()) {
            claimInfoMenu.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (claimInfoMenu.isVisible()) {
            claimInfoMenu.close();
        }

        if (contextMenu.isVisible()) {
            contextMenu.close();
        }

        double zoomFactor = 1.0 + (delta > 0 ? SCALE_STEP : -SCALE_STEP);
        double newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * zoomFactor));

        double mouseWorldX = (mouseX - offsetX) / scale;
        double mouseWorldZ = (mouseY - offsetZ) / scale;
        scale = newScale;
        offsetX = mouseX - mouseWorldX * scale;
        offsetZ = mouseY - mouseWorldZ * scale;

        return true;
    }
    public double mouseX, mouseY;
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        int worldX = (int) ((mouseX - offsetX) / scale);
        int worldZ = (int) ((mouseY - offsetZ) / scale);
        hoverBlockX = (int)Math.floor(worldX);
        hoverBlockZ = (int)Math.floor(worldZ);

        // Only update hovered chunk when in claim mode
        if (claimMode) {
            hoveredChunk = new ChunkPos(hoverBlockX >> 4, hoverBlockZ >> 4);
        } else {
            hoveredChunk = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Cancel movement position selection mode
            if (selectingMovementPosition) {
                selectingMovementPosition = false;
                pendingMovementCommand = -1;
                pendingMovementGroups.clear();
                return true;
            }

            if (claimInfoMenu.isVisible()) {
                claimInfoMenu.close();
                return true;
            }

            if (contextMenu.isVisible()) {
                contextMenu.close();
                return true;
            }

            onClose();
            return true;
        }

        // Navigationstasten
        if (!contextMenu.isVisible() && !claimInfoMenu.isVisible()) {
            double moveSpeed = 40.0 / scale;
            switch (keyCode) {
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> offsetZ += moveSpeed;
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> offsetZ -= moveSpeed;
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> offsetX += moveSpeed;
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> offsetX -= moveSpeed;
                case GLFW.GLFW_KEY_EQUAL -> mouseScrolled(width/2.0, height/2.0, 1);
                case GLFW.GLFW_KEY_MINUS -> mouseScrolled(width/2.0, height/2.0, -1);
                case GLFW.GLFW_KEY_C -> centerOnPlayer();
                case GLFW.GLFW_KEY_R -> resetZoom();
            }
        }

        return true;
    }

    @Override
    public void onClose() {
        tileManager.close();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    private long lastFpsTime = 0;
    private int fpsCounter = 0;
    private int currentFps = 0;
    private void renderFPS(GuiGraphics guiGraphics) {
        long currentTime = System.currentTimeMillis();
        fpsCounter++;

        if (currentTime - lastFpsTime >= 1000) {
            currentFps = fpsCounter;
            fpsCounter = 0;
            lastFpsTime = currentTime;
        }

        String fpsText = String.format("FPS: %d", currentFps);
        guiGraphics.drawString(font, fpsText, width - font.width(fpsText) - 15, 5, 0x00FF00);
    }
    public boolean isPlayerFactionLeader() {
        return this.isPlayerFactionLeader(ownFaction);
    }
    public boolean isPlayerFactionLeader(RecruitsFaction faction) {
        if(player == null || faction ==  null) return false;

        return faction.getTeamLeaderUUID().equals(player.getUUID());
    }

    public boolean isPlayerClaimLeader(){
        return this.isPlayerClaimLeader(selectedClaim);
    }
    public boolean isPlayerClaimLeader(RecruitsClaim claim) {
        if(player == null || claim ==  null) return false;

        return claim.getPlayerInfo().getUUID().equals(player.getUUID());
    }
    public List<ChunkPos> getClaimArea(ChunkPos pos){
        List<ChunkPos> area = new ArrayList<>();
        if(pos == null) return area;

        int range = 2;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                area.add(new ChunkPos(pos.x + dx, pos.z + dz));
            }
        }

        return area;
    }
    public void claimArea() {
        if(!canPlayerPay(getClaimCost(ownFaction), player)) return;
        if(!ClientManager.configValueIsClaimingAllowed) return;

        List<ChunkPos> area = getClaimArea(this.selectedChunk);

        RecruitsClaim newClaim = new RecruitsClaim(ownFaction);
        for (ChunkPos pos : area) {
            newClaim.addChunk(pos);
        }

        newClaim.setCenter(selectedChunk);
        newClaim.setPlayer(new RecruitsPlayerInfo(player.getUUID(), player.getName().getString(), ownFaction));

        Main.SIMPLE_CHANNEL.sendToServer(new MessageDoPayment(player.getUUID(), getClaimCost(ownFaction)));

        ClientManager.recruitsClaims.add(newClaim);
        Main.SIMPLE_CHANNEL.sendToServer(new MessageUpdateClaim(newClaim));
    }

    public void claimChunk() {
        if(!canPlayerPay(ClientManager.configValueChunkCost, player)) return;
        if(!ClientManager.configValueIsClaimingAllowed) return;

        RecruitsClaim neighborClaim = getNeighborClaim(selectedChunk);
        if(neighborClaim == null) return;

        String ownerID = ownFaction.getStringID();
        String neighborID = neighborClaim.getOwnerFaction().getStringID();
        if(!Objects.equals(ownerID, neighborID)) return;

        for(RecruitsClaim claim : ClientManager.recruitsClaims){
            if(claim.equals(neighborClaim)){
                neighborClaim.addChunk(selectedChunk);
                this.recalculateCenter(neighborClaim);
                break;
            }
        }
        Main.SIMPLE_CHANNEL.sendToServer(new MessageDoPayment(player.getUUID(), ClientManager.configValueChunkCost));
        Main.SIMPLE_CHANNEL.sendToServer(new MessageUpdateClaim(neighborClaim));
    }

    @Nullable
    public RecruitsClaim getNeighborClaim(ChunkPos chunk) {
        ChunkPos[] neighbors = new ChunkPos[] {
                new ChunkPos(chunk.x + 1, chunk.z),
                new ChunkPos(chunk.x - 1, chunk.z),
                new ChunkPos(chunk.x, chunk.z + 1),
                new ChunkPos(chunk.x, chunk.z - 1)
        };

        for (ChunkPos neighbor : neighbors) {
            for (RecruitsClaim claim : ClientManager.recruitsClaims) {
                if (claim.containsChunk(neighbor)) {
                    return claim;
                }
            }
        }

        return null;
    }

    public void recalculateCenter(RecruitsClaim claim) {
        List<ChunkPos> claimedChunks = claim.getClaimedChunks();
        if (claimedChunks.isEmpty()) return;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (ChunkPos pos : claimedChunks) {
            if (pos.x < minX) minX = pos.x;
            if (pos.x > maxX) maxX = pos.x;
            if (pos.z < minZ) minZ = pos.z;
            if (pos.z > maxZ) maxZ = pos.z;
        }

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        claim.setCenter(new ChunkPos(centerX, centerZ));
    }

    public Rectangle getClaimScreenBounds(RecruitsClaim claim) {
        int minChunkX = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;

        for (ChunkPos pos : claim.getClaimedChunks()) {
            minChunkX = Math.min(minChunkX, pos.x);
            maxChunkX = Math.max(maxChunkX, pos.x);
            minChunkZ = Math.min(minChunkZ, pos.z);
            maxChunkZ = Math.max(maxChunkZ, pos.z);
        }

        // World → Screen
        int x1 = (int) (offsetX + minChunkX * 16 * scale);
        int y1 = (int) (offsetZ + minChunkZ * 16 * scale);
        int x2 = (int) (offsetX + (maxChunkX + 1) * 16 * scale);
        int y2 = (int) (offsetZ + (maxChunkZ + 1) * 16 * scale);

        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    public Point getClaimInfoMenuPosition(RecruitsClaim claim, int menuWidth, int menuHeight) {
        Rectangle bounds = getClaimScreenBounds(claim);

        int margin = 10;

        int x = bounds.x + bounds.width + margin;
        int y = bounds.y + bounds.height / 2 - menuHeight / 2;

        // Bildschirm-Clamping
        if (x + menuWidth > width) {
            x = bounds.x - menuWidth - margin; // links anzeigen
        }
        if (y < 10) y = 10;
        if (y + menuHeight > height - 10) {
            y = height - menuHeight - 10;
        }

        return new Point(x, y);
    }

    public boolean canRemoveChunk(ChunkPos pos, RecruitsClaim claim) {
        if (pos == null || ownFaction == null) return false;
        if (isPlayerTooFar(pos)) return false;

        List<ChunkPos> claimedChunks = claim.getClaimedChunks();
        if (!claimedChunks.contains(pos)) return false;

        int unclaimedNeighborCount = 0;
        ChunkPos[] neighbors = new ChunkPos[] {
                new ChunkPos(pos.x + 1, pos.z),
                new ChunkPos(pos.x - 1, pos.z),
                new ChunkPos(pos.x, pos.z + 1),
                new ChunkPos(pos.x, pos.z - 1)
        };

        for (ChunkPos neighbor : neighbors) {
            if (!claimedChunks.contains(neighbor)) {
                unclaimedNeighborCount++;
            }
        }

        return unclaimedNeighborCount >= 2;
    }

    private boolean isPlayerTooFar(ChunkPos pos) {
        if(pos == null) return true;
        int playerPosX = player.chunkPosition().x;
        int playerPosZ = player.chunkPosition().z;

        int diffX = Math.abs(playerPosX) - Math.abs(pos.x);
        int diffZ = Math.abs(playerPosZ) - Math.abs(pos.z);

        return Math.abs(diffZ) > 4 || Math.abs(diffX) > 4;
    }

    public int getClaimCost(RecruitsFaction ownerTeam) {
        if (!ClientManager.configValueCascadeClaimCost) {
            return ClientManager.configValueClaimCost;
        }

        int amount = 1;
        if(ownerTeam != null){
            for (RecruitsClaim claim : ClientManager.recruitsClaims) {
                if (claim.getOwnerFaction().getStringID().equals(ownerTeam.getStringID())) {
                    amount += 1;
                }
            }
        }

        return amount * ClientManager.configValueClaimCost;
    }

    public boolean canPlayerPay(int cost, Player player){
        return player.isCreative() || cost <= player.getInventory().countItem(ClientManager.currencyItemStack.getItem());
    }

    public static boolean isInBufferZone(ChunkPos chunk, RecruitsFaction ownFaction) {
        if (ownFaction == null) return false;

        for (RecruitsClaim claim : ClientManager.recruitsClaims) {
            if (claim.getOwnerFaction() == null || claim.getOwnerFaction().getStringID().equals(ownFaction.getStringID())) {
                continue;
            }

            for (ChunkPos claimChunk : claim.getClaimedChunks()) {


                int dx = Math.abs(chunk.x - claimChunk.x);
                int dz = Math.abs(chunk.z - claimChunk.z);

                if (dx <= 3 && dz <= 3) {
                    if (!(dx == 0 && dz == 0)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
    public boolean canClaimChunk(ChunkPos pos) {
        if (!ClientManager.configValueIsClaimingAllowed) return false;
        if (pos == null || ClientManager.ownFaction == null) return false;
        if (isPlayerTooFar(pos)) return false;

        for (RecruitsClaim existingClaim : ClientManager.recruitsClaims) {
            if (existingClaim.containsChunk(pos)) {
                return false;
            }
        }

        RecruitsClaim neighborClaim = getNeighborClaim(pos);
        if (neighborClaim == null) return false;

        if(neighborClaim.getClaimedChunks().size() >= RecruitsClaim.MAX_SIZE) return false;

        return !isInBufferZone(pos, ClientManager.ownFaction);
    }
    public boolean canClaimArea(List<ChunkPos> areaChunks) {
        if (selectedChunk == null || areaChunks == null || areaChunks.isEmpty() || ClientManager.ownFaction == null) return false;
        if (isPlayerTooFar(selectedChunk)) return false;

        //if(ownFaction.claimAmount >= ownFaction.getMaxClaims()) return false;

        for (ChunkPos chunk : areaChunks) {
            for (RecruitsClaim existingClaim : ClientManager.recruitsClaims) {
                if (existingClaim.containsChunk(chunk)) {
                    return false;
                }
            }

            if (isInBufferZone(chunk, ClientManager.ownFaction)) {
                return false;
            }
        }

        return true;
    }

    public List<ChunkPos> getClaimableChunks(ChunkPos center, int radius) {
        List<ChunkPos> claimableChunks = new ArrayList<>();

        if (center == null || ClientManager.ownFaction == null) return claimableChunks;

        int minX = center.x - radius;
        int maxX = center.x + radius;
        int minZ = center.z - radius;
        int maxZ = center.z + radius;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos chunk = new ChunkPos(x, z);

                if (canClaimChunkRaw(chunk)) {
                    claimableChunks.add(chunk);
                }
            }
        }

        return claimableChunks;
    }

    public boolean canClaimChunkRaw(ChunkPos pos) {
        for (RecruitsClaim existingClaim : ClientManager.recruitsClaims) {
            if (existingClaim.containsChunk(pos)) {
                return false;
            }
        }

        RecruitsClaim neighborClaim = getNeighborClaim(pos);
        if (neighborClaim == null) return false;

        return !isInBufferZone(pos, ClientManager.ownFaction);
    }

    public boolean canAddRoute() {
        return this.selectedRoute != null;
    }

    public void addRoute() {
        RecruitsRoute newRoute = new RecruitsRoute("New Route");


    }

    // Custom button class for group icon
    private static class GroupIconButton extends net.minecraft.client.gui.components.Button {
        private static final ResourceLocation SWORD_ICON = RecruitsGroup.IMAGES.get(0);

        public GroupIconButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Draw button background
            int backgroundColor = this.isHovered() ? 0xFF555555 : 0xFF333333;
            int borderColor = this.isHovered() ? 0xFFFFFFFF : 0xFF999999;

            // Background
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);

            // Border
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor); // Top
            guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor); // Bottom
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor); // Left
            guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor); // Right

            // Draw sword icon centered
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            int iconSize = 16;
            int iconX = getX() + (width - iconSize) / 2;
            int iconY = getY() + (height - iconSize) / 2;

            guiGraphics.blit(SWORD_ICON, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        }
    }

    // Custom button class for claim mode toggle
    private static class ClaimModeButton extends net.minecraft.client.gui.components.Button {
        private static final ItemStack EMERALD_ICON = new ItemStack(Items.EMERALD);
        private boolean claimModeActive;

        public ClaimModeButton(int x, int y, int width, int height, boolean initialState, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.claimModeActive = initialState;
        }

        public void setClaimModeActive(boolean active) {
            this.claimModeActive = active;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Draw button background - highlight if active
            int backgroundColor = claimModeActive ? 0xFF4444AA : (this.isHovered() ? 0xFF555555 : 0xFF333333);
            int borderColor = claimModeActive ? 0xFF6666FF : (this.isHovered() ? 0xFFFFFFFF : 0xFF999999);

            // Background
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);

            // Border
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor); // Top
            guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor); // Bottom
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor); // Left
            guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor); // Right

            // Draw emerald icon centered
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            int iconSize = 16;
            int iconX = getX() + (width - iconSize) / 2;
            int iconY = getY() + (height - iconSize) / 2;

            // Render emerald item
            guiGraphics.renderFakeItem(EMERALD_ICON, iconX, iconY);
        }
    }

    // Custom button class for clear selection
    private static class ClearSelectionButton extends net.minecraft.client.gui.components.Button {

        public ClearSelectionButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Draw button background
            int backgroundColor = this.isHovered() ? 0xFF555555 : 0xFF333333;
            int borderColor = this.isHovered() ? 0xFFFFFFFF : 0xFF999999;

            // Background
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);

            // Border
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor); // Top
            guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor); // Bottom
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor); // Left
            guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor); // Right

            // Draw X icon centered
            int centerX = getX() + width / 2;
            int centerY = getY() + height / 2;
            int size = 6; // Half-size of the X
            int thickness = 2;

            int color = this.isHovered() ? 0xFFFF4444 : 0xFFFFFFFF;

            // Draw X - two diagonal lines
            // Line from top-left to bottom-right
            for (int i = -size; i <= size; i++) {
                for (int t = 0; t < thickness; t++) {
                    guiGraphics.fill(
                        centerX + i, centerY + i + t,
                        centerX + i + 1, centerY + i + t + 1,
                        color
                    );
                }
            }

            // Line from top-right to bottom-left
            for (int i = -size; i <= size; i++) {
                for (int t = 0; t < thickness; t++) {
                    guiGraphics.fill(
                        centerX + i, centerY - i + t,
                        centerX + i + 1, centerY - i + t + 1,
                        color
                    );
                }
            }
        }
    }


}


