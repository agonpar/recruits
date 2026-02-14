package com.talhanation.recruits.client.gui.worldmap;

import com.talhanation.recruits.Main;
import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.client.gui.CommandScreen;
import com.talhanation.recruits.network.MessageSetGroupFormation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.UUID;

public class GroupContextMenu {
    private static final Component TEXT_FORMATIONS = Component.translatable("gui.recruits.command.tooltip.formations");
    private static final Component TEXT_MOVEMENTS = Component.translatable("gui.recruits.command.tooltip.movement");
    private static final Component TEXT_ACTIONS = Component.translatable("gui.recruits.command.tooltip.actions");
    private static final Component TEXT_COMBAT_MODE = Component.translatable("gui.recruits.command.tooltip.combat");
    private static final Component TEXT_FORMATION_NONE = Component.translatable("gui.recruits.command.text.formation_none");
    private static final Component TEXT_FORMATION_LINE = Component.translatable("gui.recruits.command.text.formation_lineup");
    private static final Component TEXT_FORMATION_SQUARE = Component.translatable("gui.recruits.command.text.formation_square");
    private static final Component TEXT_FORMATION_TRIANGLE = Component.translatable("gui.recruits.command.text.formation_triangle");
    private static final Component TEXT_FORMATION_HCIRCLE = Component.translatable("gui.recruits.command.text.formation_hollow_circle");
    private static final Component TEXT_FORMATION_HSQUARE = Component.translatable("gui.recruits.command.text.formation_hollow_square");
    private static final Component TEXT_FORMATION_VFORM = Component.translatable("gui.recruits.command.text.formation_v");
    private static final Component TEXT_FORMATION_CIRCLE = Component.translatable("gui.recruits.command.text.formation_circle");
    private static final Component TEXT_FORMATION_MOVEMENT = Component.translatable("gui.recruits.command.text.formation_movement");

    // Movement commands
    private static final Component TEXT_MOVE_FOLLOW = Component.translatable("gui.recruits.command.text.follow");
    private static final Component TEXT_MOVE_WANDER = Component.translatable("gui.recruits.command.text.wander");
    private static final Component TEXT_MOVE_HOLD_POS = Component.translatable("gui.recruits.command.text.holdPos");
    private static final Component TEXT_MOVE_BACK_TO_POS = Component.translatable("gui.recruits.command.text.backToPos");
    private static final Component TEXT_MOVE_HOLD_MY_POS = Component.translatable("gui.recruits.command.text.holdMyPos");
    private static final Component TEXT_MOVE_MOVE = Component.translatable("gui.recruits.command.text.move");
    private static final Component TEXT_MOVE_FORWARD = Component.translatable("gui.recruits.command.text.forward");
    private static final Component TEXT_MOVE_BACKWARD = Component.translatable("gui.recruits.command.text.backward");

    // Action commands
    private static final Component TEXT_ACTION_REST = Component.translatable("gui.recruits.command.text.rest");
    private static final Component TEXT_ACTION_BACK_TO_MOUNT = Component.translatable("gui.recruits.command.text.backToMount");
    private static final Component TEXT_ACTION_DISMOUNT = Component.translatable("gui.recruits.command.text.dismount");

    // Combat mode commands
    private static final Component TEXT_COMBAT_PASSIVE = Component.translatable("gui.recruits.command.text.passive");
    private static final Component TEXT_COMBAT_NEUTRAL = Component.translatable("gui.recruits.command.text.neutral");
    private static final Component TEXT_COMBAT_AGGRESSIVE = Component.translatable("gui.recruits.command.text.aggressive");
    private static final Component TEXT_COMBAT_RAID = Component.translatable("gui.recruits.command.text.raid");

    private final WorldMapScreen worldMapScreen;
    private int x, y;
    private boolean visible = false;
    private final int width = 180;
    private final int entryHeight = 20;
    private final int iconSize = 16;

    private Set<UUID> selectedGroupUUIDs;
    private boolean showingFormationSubmenu = false;
    private boolean showingMovementSubmenu = false;
    private boolean showingActionsSubmenu = false;
    private boolean showingCombatModeSubmenu = false;
    private int submenuX, submenuY;

    public GroupContextMenu(WorldMapScreen worldMapScreen) {
        this.worldMapScreen = worldMapScreen;
    }

    public void openAt(int x, int y, Set<UUID> selectedGroups) {
        this.x = Math.max(10, Math.min(x, worldMapScreen.width - width - 10));
        this.y = Math.max(10, Math.min(y, worldMapScreen.height - 100));
        this.selectedGroupUUIDs = selectedGroups;
        this.visible = true;
        this.showingFormationSubmenu = false;
        this.showingMovementSubmenu = false;
        this.showingActionsSubmenu = false;
        this.showingCombatModeSubmenu = false;
    }

    public void close() {
        this.visible = false;
        this.showingFormationSubmenu = false;
        this.showingMovementSubmenu = false;
        this.showingActionsSubmenu = false;
        this.showingCombatModeSubmenu = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics guiGraphics, WorldMapScreen screen) {
        if (!visible) return;

        // Push pose to render on top of everything
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400); // High Z value to ensure it's on top

        // Main menu with four entries
        int menuHeight = entryHeight * 4; // Formations, Movements, Actions and Combat Mode
        guiGraphics.fill(x, y, x + width, y + menuHeight, 0xFF1A1A1A);
        guiGraphics.renderOutline(x, y, width, menuHeight, 0xFF555555);

        // Formation entry
        boolean hoveredFormation = isMouseOverEntry(x, y);
        int bgColorFormation = hoveredFormation ? 0xFF333333 : 0xFF1A1A1A;
        guiGraphics.fill(x, y, x + width, y + entryHeight, bgColorFormation);

        int textColorFormation = hoveredFormation ? 0xFFFFFF : 0xCCCCCC;
        guiGraphics.drawString(screen.getMinecraft().font, TEXT_FORMATIONS.getString(), x + 8, y + 6, textColorFormation);
        guiGraphics.drawString(screen.getMinecraft().font, ">", x + width - 16, y + 6, textColorFormation);

        // Movement entry
        boolean hoveredMovement = isMouseOverEntry(x, y + entryHeight);
        int bgColorMovement = hoveredMovement ? 0xFF333333 : 0xFF1A1A1A;
        guiGraphics.fill(x, y + entryHeight, x + width, y + entryHeight * 2, bgColorMovement);

        int textColorMovement = hoveredMovement ? 0xFFFFFF : 0xCCCCCC;
        guiGraphics.drawString(screen.getMinecraft().font, TEXT_MOVEMENTS.getString(), x + 8, y + entryHeight + 6, textColorMovement);
        guiGraphics.drawString(screen.getMinecraft().font, ">", x + width - 16, y + entryHeight + 6, textColorMovement);

        // Actions entry
        boolean hoveredActions = isMouseOverEntry(x, y + entryHeight * 2);
        int bgColorActions = hoveredActions ? 0xFF333333 : 0xFF1A1A1A;
        guiGraphics.fill(x, y + entryHeight * 2, x + width, y + entryHeight * 3, bgColorActions);

        int textColorActions = hoveredActions ? 0xFFFFFF : 0xCCCCCC;
        guiGraphics.drawString(screen.getMinecraft().font, TEXT_ACTIONS.getString(), x + 8, y + entryHeight * 2 + 6, textColorActions);
        guiGraphics.drawString(screen.getMinecraft().font, ">", x + width - 16, y + entryHeight * 2 + 6, textColorActions);

        // Combat Mode entry
        boolean hoveredCombatMode = isMouseOverEntry(x, y + entryHeight * 3);
        int bgColorCombatMode = hoveredCombatMode ? 0xFF333333 : 0xFF1A1A1A;
        guiGraphics.fill(x, y + entryHeight * 3, x + width, y + entryHeight * 4, bgColorCombatMode);

        int textColorCombatMode = hoveredCombatMode ? 0xFFFFFF : 0xCCCCCC;
        guiGraphics.drawString(screen.getMinecraft().font, TEXT_COMBAT_MODE.getString(), x + 8, y + entryHeight * 3 + 6, textColorCombatMode);
        guiGraphics.drawString(screen.getMinecraft().font, ">", x + width - 16, y + entryHeight * 3 + 6, textColorCombatMode);

        // Check if mouse is over any submenu area
        boolean hoveredFormationSubmenu = false;
        boolean hoveredMovementSubmenu = false;
        boolean hoveredActionsSubmenu = false;
        boolean hoveredCombatModeSubmenu = false;

        if (showingFormationSubmenu) {
            CommandScreen.Formation[] formations = CommandScreen.Formation.values();
            int submenuHeight = formations.length * entryHeight;
            int submenuWidth = 150;
            hoveredFormationSubmenu = isMouseOverSubmenuArea(submenuX, submenuY, submenuWidth, submenuHeight);
        }

        if (showingMovementSubmenu) {
            int submenuWidth = 150;
            int submenuHeight = 8 * entryHeight; // 8 movement options
            hoveredMovementSubmenu = isMouseOverSubmenuArea(submenuX, submenuY, submenuWidth, submenuHeight);
        }

        if (showingActionsSubmenu) {
            int submenuWidth = 150;
            int submenuHeight = 3 * entryHeight; // 3 action options
            hoveredActionsSubmenu = isMouseOverSubmenuArea(submenuX, submenuY, submenuWidth, submenuHeight);
        }

        if (showingCombatModeSubmenu) {
            int submenuWidth = 150;
            int submenuHeight = 4 * entryHeight; // 4 combat mode options
            hoveredCombatModeSubmenu = isMouseOverSubmenuArea(submenuX, submenuY, submenuWidth, submenuHeight);
        }

        // Formation submenu - show when hovering over Formations or over submenu itself
        if (hoveredFormation || hoveredFormationSubmenu) {
            showingMovementSubmenu = false;
            showingActionsSubmenu = false;
            showingFormationSubmenu = true;
            submenuX = x + width;
            submenuY = y;
            renderFormationSubmenu(guiGraphics, screen);
        }
        // Movement submenu - show when hovering over Movements or over submenu itself
        else if (hoveredMovement || hoveredMovementSubmenu) {
            showingFormationSubmenu = false;
            showingActionsSubmenu = false;
            showingMovementSubmenu = true;
            submenuX = x + width;
            submenuY = y + entryHeight;
            renderMovementSubmenu(guiGraphics, screen);
        }
        // Actions submenu - show when hovering over Actions or over submenu itself
        else if (hoveredActions || hoveredActionsSubmenu) {
            showingFormationSubmenu = false;
            showingMovementSubmenu = false;
            showingCombatModeSubmenu = false;
            showingActionsSubmenu = true;
            submenuX = x + width;
            submenuY = y + entryHeight * 2;
            renderActionsSubmenu(guiGraphics, screen);
        }
        // Combat Mode submenu - show when hovering over Combat Mode or over submenu itself
        else if (hoveredCombatMode || hoveredCombatModeSubmenu) {
            showingFormationSubmenu = false;
            showingMovementSubmenu = false;
            showingActionsSubmenu = false;
            showingCombatModeSubmenu = true;
            submenuX = x + width;
            submenuY = y + entryHeight * 3;
            renderCombatModeSubmenu(guiGraphics, screen);
        } else {
            showingFormationSubmenu = false;
            showingMovementSubmenu = false;
            showingActionsSubmenu = false;
            showingCombatModeSubmenu = false;
        }

        // Pop pose
        guiGraphics.pose().popPose();
    }

    private void renderFormationSubmenu(GuiGraphics guiGraphics, WorldMapScreen screen) {
        CommandScreen.Formation[] formations = CommandScreen.Formation.values();
        int submenuHeight = formations.length * entryHeight;
        int submenuWidth = 150;

        // Adjust horizontal position if it goes off screen
        if (submenuX + submenuWidth > screen.width) {
            submenuX = x - submenuWidth;
        }

        // Adjust vertical position if it goes off screen
        if (submenuY + submenuHeight > screen.height) {
            submenuY = screen.height - submenuHeight - 10;
        }

        // Ensure submenu doesn't go above top of screen
        if (submenuY < 10) {
            submenuY = 10;
        }

        guiGraphics.fill(submenuX, submenuY, submenuX + submenuWidth, submenuY + submenuHeight, 0xFF1A1A1A);
        guiGraphics.renderOutline(submenuX, submenuY, submenuWidth, submenuHeight, 0xFF555555);

        int entryY = submenuY;
        for (CommandScreen.Formation formation : formations) {
            boolean hovered = isMouseOverSubmenuEntry(submenuX, entryY, submenuWidth);

            int bgColor = hovered ? 0xFF333333 : 0xFF1A1A1A;
            guiGraphics.fill(submenuX, entryY, submenuX + submenuWidth, entryY + entryHeight, bgColor);

            // Render formation icon
            ResourceLocation iconTexture = getFormationIcon(formation);
            if (iconTexture != null) {
                guiGraphics.blit(iconTexture, submenuX + 4, entryY + 2, 0, 0, iconSize, iconSize, iconSize, iconSize);
            }

            // Render formation name
            String formationName = getFormationName(formation);
            int textColor = hovered ? 0xFFFFFF : 0xCCCCCC;

            // Check if this is the current formation
            if (ClientManager.formationSelection == formation.getIndex()) {
                textColor = 0xFFFFAA; // Highlight current formation
            }

            guiGraphics.drawString(screen.getMinecraft().font, formationName, submenuX + iconSize + 12, entryY + 6, textColor);

            entryY += entryHeight;
        }
    }

    private void renderMovementSubmenu(GuiGraphics guiGraphics, WorldMapScreen screen) {
        // Movement commands: Follow, Wander, Hold Position, Back to Position, Hold My Position, Move, Forward, Backward
        String[] movementOptions = {
            TEXT_MOVE_FOLLOW.getString(),
            TEXT_MOVE_WANDER.getString(),
            TEXT_MOVE_HOLD_POS.getString(),
            TEXT_MOVE_BACK_TO_POS.getString(),
            TEXT_MOVE_HOLD_MY_POS.getString(),
            TEXT_MOVE_MOVE.getString(),
            TEXT_MOVE_FORWARD.getString(),
            TEXT_MOVE_BACKWARD.getString()
        };

        int submenuHeight = movementOptions.length * entryHeight;
        int submenuWidth = 150;

        // Adjust horizontal position if it goes off screen
        if (submenuX + submenuWidth > screen.width) {
            submenuX = x - submenuWidth;
        }

        // Adjust vertical position if it goes off screen
        if (submenuY + submenuHeight > screen.height) {
            submenuY = screen.height - submenuHeight - 10;
        }

        // Ensure submenu doesn't go above top of screen
        if (submenuY < 10) {
            submenuY = 10;
        }

        guiGraphics.fill(submenuX, submenuY, submenuX + submenuWidth, submenuY + submenuHeight, 0xFF1A1A1A);
        guiGraphics.renderOutline(submenuX, submenuY, submenuWidth, submenuHeight, 0xFF555555);

        // Check if groups have last movement direction (for Forward/Backward)
        boolean hasDirection = worldMapScreen.hasLastMovementDirection(selectedGroupUUIDs);

        int entryY = submenuY;
        for (int i = 0; i < movementOptions.length; i++) {
            boolean isForwardOrBackward = (i == 6 || i == 7); // Forward or Backward indices
            boolean isDisabled = isForwardOrBackward && !hasDirection;

            boolean hovered = isMouseOverSubmenuEntry(submenuX, entryY, submenuWidth) && !isDisabled;
            int bgColor = hovered ? 0xFF333333 : 0xFF1A1A1A;
            guiGraphics.fill(submenuX, entryY, submenuX + submenuWidth, entryY + entryHeight, bgColor);

            int textColor;
            if (isDisabled) {
                textColor = 0xFF666666; // Gray out disabled options
            } else {
                textColor = hovered ? 0xFFFFFF : 0xCCCCCC;
            }
            guiGraphics.drawString(screen.getMinecraft().font, movementOptions[i], submenuX + 8, entryY + 6, textColor);

            entryY += entryHeight;
        }
    }

    private void renderActionsSubmenu(GuiGraphics guiGraphics, WorldMapScreen screen) {
        // Action commands: Rest, Back to Mount, Dismount
        String[] actionOptions = {
            TEXT_ACTION_REST.getString(),
            TEXT_ACTION_BACK_TO_MOUNT.getString(),
            TEXT_ACTION_DISMOUNT.getString()
        };

        int submenuHeight = actionOptions.length * entryHeight;
        int submenuWidth = 150;

        // Adjust horizontal position if it goes off screen
        if (submenuX + submenuWidth > screen.width) {
            submenuX = x - submenuWidth;
        }

        // Adjust vertical position if it goes off screen
        if (submenuY + submenuHeight > screen.height) {
            submenuY = screen.height - submenuHeight - 10;
        }

        // Ensure submenu doesn't go above top of screen
        if (submenuY < 10) {
            submenuY = 10;
        }

        guiGraphics.fill(submenuX, submenuY, submenuX + submenuWidth, submenuY + submenuHeight, 0xFF1A1A1A);
        guiGraphics.renderOutline(submenuX, submenuY, submenuWidth, submenuHeight, 0xFF555555);

        int entryY = submenuY;
        for (int i = 0; i < actionOptions.length; i++) {
            boolean hovered = isMouseOverSubmenuEntry(submenuX, entryY, submenuWidth);
            int bgColor = hovered ? 0xFF333333 : 0xFF1A1A1A;
            guiGraphics.fill(submenuX, entryY, submenuX + submenuWidth, entryY + entryHeight, bgColor);

            int textColor = hovered ? 0xFFFFFF : 0xCCCCCC;
            guiGraphics.drawString(screen.getMinecraft().font, actionOptions[i], submenuX + 8, entryY + 6, textColor);

            entryY += entryHeight;
        }
    }

    private void renderCombatModeSubmenu(GuiGraphics guiGraphics, WorldMapScreen screen) {
        // Combat mode commands: Passive, Neutral, Aggressive, Raid
        String[] combatModeOptions = {
            TEXT_COMBAT_PASSIVE.getString(),
            TEXT_COMBAT_NEUTRAL.getString(),
            TEXT_COMBAT_AGGRESSIVE.getString(),
            TEXT_COMBAT_RAID.getString()
        };

        int submenuHeight = combatModeOptions.length * entryHeight;
        int submenuWidth = 150;

        // Adjust horizontal position if it goes off screen
        if (submenuX + submenuWidth > screen.width) {
            submenuX = x - submenuWidth;
        }

        // Adjust vertical position if it goes off screen
        if (submenuY + submenuHeight > screen.height) {
            submenuY = screen.height - submenuHeight - 10;
        }

        // Ensure submenu doesn't go above top of screen
        if (submenuY < 10) {
            submenuY = 10;
        }

        guiGraphics.fill(submenuX, submenuY, submenuX + submenuWidth, submenuY + submenuHeight, 0xFF1A1A1A);
        guiGraphics.renderOutline(submenuX, submenuY, submenuWidth, submenuHeight, 0xFF555555);

        int entryY = submenuY;
        for (int i = 0; i < combatModeOptions.length; i++) {
            boolean hovered = isMouseOverSubmenuEntry(submenuX, entryY, submenuWidth);
            int bgColor = hovered ? 0xFF333333 : 0xFF1A1A1A;
            guiGraphics.fill(submenuX, entryY, submenuX + submenuWidth, entryY + entryHeight, bgColor);

            int textColor = hovered ? 0xFFFFFF : 0xCCCCCC;
            guiGraphics.drawString(screen.getMinecraft().font, combatModeOptions[i], submenuX + 8, entryY + 6, textColor);

            entryY += entryHeight;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || button != 0) return false;

        // Check if clicking on formation submenu
        if (showingFormationSubmenu) {
            CommandScreen.Formation[] formations = CommandScreen.Formation.values();
            int submenuHeight = formations.length * entryHeight;
            int submenuWidth = 150;

            int entryY = submenuY;
            for (CommandScreen.Formation formation : formations) {
                if (mouseX >= submenuX && mouseX <= submenuX + submenuWidth &&
                    mouseY >= entryY && mouseY <= entryY + entryHeight) {
                    // Send formation command to server for each selected group
                    applyFormationToSelectedGroups(formation);
                    close();
                    return true;
                }
                entryY += entryHeight;
            }
        }

        // Check if clicking on movement submenu
        if (showingMovementSubmenu) {
            int submenuWidth = 150;
            int entryY = submenuY;

            // Movement states: 1=Follow, 0=Wander, 2=Hold Position, 3=Back to Position,
            // 4=Hold My Position, 6=Move, 7=Forward, 8=Backward
            int[] movementStates = {1, 0, 2, 3, 4, 6, 7, 8};

            // Check if groups have last movement direction (for Forward/Backward)
            boolean hasDirection = worldMapScreen.hasLastMovementDirection(selectedGroupUUIDs);

            for (int i = 0; i < movementStates.length; i++) {
                if (mouseX >= submenuX && mouseX <= submenuX + submenuWidth &&
                    mouseY >= entryY && mouseY <= entryY + entryHeight) {

                    int movementState = movementStates[i];

                    // Check if this is Forward/Backward and it's disabled
                    boolean isForwardOrBackward = (i == 6 || i == 7);
                    if (isForwardOrBackward && !hasDirection) {
                        // Ignore click on disabled option
                        return false;
                    }

                    // Only Move requires position selection
                    // Forward and Backward calculate position automatically from saved direction
                    if (movementState == 6) {
                        // Move - Activate position selection mode
                        worldMapScreen.startMovementPositionSelection(movementState, selectedGroupUUIDs);
                        close();
                    } else if (movementState == 7 || movementState == 8) {
                        // Forward or Backward - Execute directly with automatic position calculation
                        worldMapScreen.executeForwardBackwardMovement(movementState, selectedGroupUUIDs);
                        close();
                    } else {
                        // Other commands - Send movement command immediately
                        applyMovementToSelectedGroups(movementState);
                        close();
                    }
                    return true;
                }
                entryY += entryHeight;
            }
        }

        // Check if clicking on actions submenu
        if (showingActionsSubmenu) {
            int submenuWidth = 150;
            int entryY = submenuY;

            // Action commands: Rest (0), Back to Mount (1), Dismount (2)
            for (int i = 0; i < 3; i++) {
                if (mouseX >= submenuX && mouseX <= submenuX + submenuWidth &&
                    mouseY >= entryY && mouseY <= entryY + entryHeight) {

                    // Apply action to selected groups
                    applyActionToSelectedGroups(i);
                    close();
                    return true;
                }
                entryY += entryHeight;
            }
        }

        // Check if clicking on combat mode submenu
        if (showingCombatModeSubmenu) {
            int submenuWidth = 150;
            int entryY = submenuY;

            // Combat mode commands: Passive (0), Neutral (1), Aggressive (2), Raid (3)
            for (int i = 0; i < 4; i++) {
                if (mouseX >= submenuX && mouseX <= submenuX + submenuWidth &&
                    mouseY >= entryY && mouseY <= entryY + entryHeight) {

                    // Apply combat mode to selected groups
                    applyCombatModeToSelectedGroups(i);
                    close();
                    return true;
                }
                entryY += entryHeight;
            }
        }

        // Check if clicking on main menu
        if (mouseX >= x && mouseX <= x + width &&
            mouseY >= y && mouseY <= y + entryHeight) {
            // Formation entry clicked - submenu will show automatically
            return true;
        }

        // Click outside menu
        close();
        return false;
    }

    private void applyFormationToSelectedGroups(CommandScreen.Formation formation) {
        if (selectedGroupUUIDs == null || selectedGroupUUIDs.isEmpty()) return;

        UUID playerUUID = worldMapScreen.getPlayer().getUUID();

        // Remove target markers when changing formation (as it will stop movement)
        for (UUID groupUUID : selectedGroupUUIDs) {
            worldMapScreen.removeGroupTargetPosition(groupUUID);
        }

        // Send formation preference to server for each selected group
        // This only saves the preference, it won't move the troops
        for (UUID groupUUID : selectedGroupUUIDs) {
            Main.SIMPLE_CHANNEL.sendToServer(
                new MessageSetGroupFormation(playerUUID, groupUUID, formation.getIndex())
            );
        }

        // Also update the client-side formation selection so it's reflected in the UI
        ClientManager.formationSelection = formation.getIndex();
    }

    private void applyMovementToSelectedGroups(int movementState) {
        if (selectedGroupUUIDs == null || selectedGroupUUIDs.isEmpty()) return;

        UUID playerUUID = worldMapScreen.getPlayer().getUUID();
        int formation = ClientManager.formationSelection;

        // Remove target markers for groups receiving non-movement commands
        // Movement states that cancel ongoing movement: Follow(1), Wander(0), Hold Position(2), etc.
        for (UUID groupUUID : selectedGroupUUIDs) {
            worldMapScreen.removeGroupTargetPosition(groupUUID);
        }

        // Send movement command to server for each selected group
        for (UUID groupUUID : selectedGroupUUIDs) {
            Main.SIMPLE_CHANNEL.sendToServer(
                new com.talhanation.recruits.network.MessageMovement(
                    playerUUID,
                    movementState,
                    groupUUID,
                    formation
                )
            );
        }
    }

    private void applyActionToSelectedGroups(int actionIndex) {
        if (selectedGroupUUIDs == null || selectedGroupUUIDs.isEmpty()) return;

        UUID playerUUID = worldMapScreen.getPlayer().getUUID();

        // Action commands: Rest (0), Back to Mount (1), Dismount (2)
        for (UUID groupUUID : selectedGroupUUIDs) {
            switch (actionIndex) {
                case 0: // Rest
                    Main.SIMPLE_CHANNEL.sendToServer(
                        new com.talhanation.recruits.network.MessageRest(playerUUID, groupUUID, true)
                    );
                    break;
                case 1: // Back to Mount
                    Main.SIMPLE_CHANNEL.sendToServer(
                        new com.talhanation.recruits.network.MessageBackToMountEntity(playerUUID, groupUUID)
                    );
                    break;
                case 2: // Dismount
                    Main.SIMPLE_CHANNEL.sendToServer(
                        new com.talhanation.recruits.network.MessageDismount(playerUUID, groupUUID)
                    );
                    break;
            }
        }
    }

    private void applyCombatModeToSelectedGroups(int combatModeIndex) {
        if (selectedGroupUUIDs == null || selectedGroupUUIDs.isEmpty()) return;

        UUID playerUUID = worldMapScreen.getPlayer().getUUID();

        // Combat mode commands: Passive (3), Neutral (0), Aggressive (1), Raid (2)
        // Map from menu index to aggro state value
        int aggroState;
        switch (combatModeIndex) {
            case 0: // Passive
                aggroState = 3;
                break;
            case 1: // Neutral
                aggroState = 0;
                break;
            case 2: // Aggressive
                aggroState = 1;
                break;
            case 3: // Raid
                aggroState = 2;
                break;
            default:
                return;
        }

        for (UUID groupUUID : selectedGroupUUIDs) {
            Main.SIMPLE_CHANNEL.sendToServer(
                new com.talhanation.recruits.network.MessageAggro(playerUUID, aggroState, groupUUID)
            );
        }
    }

    private boolean isMouseOverEntry(int entryX, int entryY) {
        double mouseX = this.worldMapScreen.mouseX;
        double mouseY = this.worldMapScreen.mouseY;
        return mouseX >= entryX && mouseX <= entryX + width &&
               mouseY >= entryY && mouseY <= entryY + entryHeight;
    }

    private boolean isMouseOverSubmenuEntry(int entryX, int entryY, int entryWidth) {
        double mouseX = this.worldMapScreen.mouseX;
        double mouseY = this.worldMapScreen.mouseY;
        return mouseX >= entryX && mouseX <= entryX + entryWidth &&
               mouseY >= entryY && mouseY <= entryY + entryHeight;
    }

    private boolean isMouseOverSubmenuArea(int x, int y, int width, int height) {
        double mouseX = this.worldMapScreen.mouseX;
        double mouseY = this.worldMapScreen.mouseY;
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= y && mouseY <= y + height;
    }

    private ResourceLocation getFormationIcon(CommandScreen.Formation formation) {
        return switch (formation.getIndex()) {
            case 0 -> new ResourceLocation(Main.MOD_ID, "textures/gui/image/none.png");
            case 1 -> new ResourceLocation(Main.MOD_ID, "textures/gui/image/line.png");
            case 2 -> new ResourceLocation(Main.MOD_ID, "textures/gui/image/square.png");
            case 3 -> new ResourceLocation(Main.MOD_ID, "textures/gui/image/triangle.png");
            case 4 -> new ResourceLocation(Main.MOD_ID, "textures/gui/image/hcircle.png");
            case 5 -> new ResourceLocation(Main.MOD_ID, "textures/gui/image/hsquare.png");
            case 6 -> new ResourceLocation(Main.MOD_ID, "textures/gui/image/vform.png");
            case 7 -> new ResourceLocation(Main.MOD_ID, "textures/gui/image/circle.png");
            case 8 -> new ResourceLocation(Main.MOD_ID, "textures/gui/image/movement.png");
            default -> null;
        };
    }

    private String getFormationName(CommandScreen.Formation formation) {
        return switch (formation) {
            case NONE -> TEXT_FORMATION_NONE.getString();
            case LINE -> TEXT_FORMATION_LINE.getString();
            case SQUARE -> TEXT_FORMATION_SQUARE.getString();
            case TRIANGLE -> TEXT_FORMATION_TRIANGLE.getString();
            case HCIRCLE -> TEXT_FORMATION_HCIRCLE.getString();
            case HSQUARE -> TEXT_FORMATION_HSQUARE.getString();
            case VFORM -> TEXT_FORMATION_VFORM.getString();
            case CIRCLE -> TEXT_FORMATION_CIRCLE.getString();
            case MOVEMENT -> TEXT_FORMATION_MOVEMENT.getString();
        };
    }
}



