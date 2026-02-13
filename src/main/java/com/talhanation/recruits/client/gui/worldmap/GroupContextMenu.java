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
    private static final Component TEXT_FORMATION_NONE = Component.translatable("gui.recruits.command.text.formation_none");
    private static final Component TEXT_FORMATION_LINE = Component.translatable("gui.recruits.command.text.formation_lineup");
    private static final Component TEXT_FORMATION_SQUARE = Component.translatable("gui.recruits.command.text.formation_square");
    private static final Component TEXT_FORMATION_TRIANGLE = Component.translatable("gui.recruits.command.text.formation_triangle");
    private static final Component TEXT_FORMATION_HCIRCLE = Component.translatable("gui.recruits.command.text.formation_hollow_circle");
    private static final Component TEXT_FORMATION_HSQUARE = Component.translatable("gui.recruits.command.text.formation_hollow_square");
    private static final Component TEXT_FORMATION_VFORM = Component.translatable("gui.recruits.command.text.formation_v");
    private static final Component TEXT_FORMATION_CIRCLE = Component.translatable("gui.recruits.command.text.formation_circle");
    private static final Component TEXT_FORMATION_MOVEMENT = Component.translatable("gui.recruits.command.text.formation_movement");

    private final WorldMapScreen worldMapScreen;
    private int x, y;
    private boolean visible = false;
    private final int width = 180;
    private final int entryHeight = 20;
    private final int iconSize = 16;

    private Set<UUID> selectedGroupUUIDs;
    private boolean showingFormationSubmenu = false;
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
    }

    public void close() {
        this.visible = false;
        this.showingFormationSubmenu = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics guiGraphics, WorldMapScreen screen) {
        if (!visible) return;

        // Push pose to render on top of everything
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400); // High Z value to ensure it's on top

        // Main menu
        int menuHeight = entryHeight; // Only "Formations" option for now
        guiGraphics.fill(x, y, x + width, y + menuHeight, 0xFF1A1A1A);
        guiGraphics.renderOutline(x, y, width, menuHeight, 0xFF555555);

        // Formation entry
        boolean hoveredFormation = isMouseOverEntry(x, y);

        // Check if mouse is over the submenu area
        boolean hoveredSubmenu = false;
        if (showingFormationSubmenu) {
            CommandScreen.Formation[] formations = CommandScreen.Formation.values();
            int submenuHeight = formations.length * entryHeight;
            int submenuWidth = 150;
            hoveredSubmenu = isMouseOverSubmenuArea(submenuX, submenuY, submenuWidth, submenuHeight);
        }

        int bgColor = hoveredFormation ? 0xFF333333 : 0xFF1A1A1A;
        guiGraphics.fill(x, y, x + width, y + entryHeight, bgColor);

        int textColor = hoveredFormation ? 0xFFFFFF : 0xCCCCCC;
        guiGraphics.drawString(screen.getMinecraft().font, TEXT_FORMATIONS.getString(), x + 8, y + 6, textColor);

        // Arrow indicator for submenu
        guiGraphics.drawString(screen.getMinecraft().font, ">", x + width - 16, y + 6, textColor);

        // Formation submenu - show when hovering over main entry OR over submenu itself
        if (hoveredFormation || hoveredSubmenu) {
            showingFormationSubmenu = true;
            submenuX = x + width;
            submenuY = y;
            renderFormationSubmenu(guiGraphics, screen);
        } else {
            showingFormationSubmenu = false;
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



