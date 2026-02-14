package com.talhanation.recruits.client.gui.worldmap;

import com.talhanation.recruits.Main;
import com.talhanation.recruits.world.RecruitsGroup;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class ActiveActionsPanel extends AbstractWidget {
    private final WorldMapScreen worldMapScreen;
    private final Player player;
    private final List<GroupActionEntry> activeActions = new ArrayList<>();

    private int scrollOffset = 0;
    private final int entryHeight = 24;
    private final int padding = 4;
    private final int cancelButtonSize = 16;

    private static final int PANEL_WIDTH = 200;
    private static final int MAX_VISIBLE_ENTRIES = 20;

    public ActiveActionsPanel(WorldMapScreen worldMapScreen, int x, int y, int width, int height, Player player) {
        super(x, y, width, height, Component.empty());
        this.worldMapScreen = worldMapScreen;
        this.player = player;
    }

    public void updateActions(List<RecruitsGroup> groups, List<AbstractRecruitEntity> allRecruits) {
        activeActions.clear();

        if (groups == null || groups.isEmpty()) return;

        for (RecruitsGroup group : groups) {
            // Get recruits for this group
            List<AbstractRecruitEntity> groupRecruits = allRecruits.stream()
                .filter(r -> r.getGroup() != null && r.getGroup().equals(group.getUUID()))
                .toList();

            if (groupRecruits.isEmpty()) continue;

            // Count how many recruits are doing each action (use majority vote)
            int movingCount = 0;
            int backToPositionCount = 0;
            int followingCount = 0;
            net.minecraft.core.BlockPos firstMovePos = null;
            net.minecraft.world.phys.Vec3 firstHoldPos = null;

            for (AbstractRecruitEntity recruit : groupRecruits) {
                // Moving to position
                if (recruit.getShouldMovePos() && recruit.getMovePos() != null) {
                    movingCount++;
                    if (firstMovePos == null) {
                        firstMovePos = recruit.getMovePos();
                    }
                }
                // Back to Position - only count if actively moving (distance > 2 blocks)
                else if (recruit.getIsBackToPosition() && recruit.getHoldPos() != null) {
                    net.minecraft.world.phys.Vec3 holdPos = recruit.getHoldPos();
                    double distance = recruit.distanceToSqr(holdPos.x, holdPos.y, holdPos.z);

                    // Only count as active if distance is greater than 4 (2 blocks squared)
                    if (distance > 4.0) {
                        backToPositionCount++;
                        if (firstHoldPos == null) {
                            firstHoldPos = recruit.getHoldPos();
                        }
                    }
                }
                // Following
                else if (recruit.getShouldFollow() && recruit.isFollowing()) {
                    followingCount++;
                }
            }

            // Use majority vote - show action if more than 30% of group is doing it
            int threshold = (int)(groupRecruits.size() * 0.3);

            // Priority: Moving > Back to Position > Following
            if (movingCount > threshold && firstMovePos != null) {
                String actionText = "Moving to position";
                activeActions.add(new GroupActionEntry(group, ActionType.MOVING, actionText, firstMovePos));
                // Don't call allowGroupTargetMarker here - it would override manual cancellations
                worldMapScreen.ensureTargetMarkerForGroup(group.getUUID(), firstMovePos);
            }
            else if (backToPositionCount > threshold && firstHoldPos != null) {
                net.minecraft.core.BlockPos holdBlockPos = new net.minecraft.core.BlockPos(
                    (int) firstHoldPos.x,
                    (int) firstHoldPos.y,
                    (int) firstHoldPos.z
                );
                String actionText = "Back to position";
                activeActions.add(new GroupActionEntry(group, ActionType.BACK_TO_POSITION, actionText, holdBlockPos));
                // Don't call allowGroupTargetMarker here - it would override manual cancellations
                worldMapScreen.ensureTargetMarkerForGroup(group.getUUID(), holdBlockPos);
            }
            else if (followingCount > threshold) {
                String actionText = "Following";
                activeActions.add(new GroupActionEntry(group, ActionType.FOLLOWING, actionText, null));
            }
        }
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (activeActions.isEmpty()) return;

        int panelHeight = Math.min(activeActions.size() * entryHeight, height);

        // Background
        guiGraphics.fill(getX(), getY(), getX() + width, getY() + panelHeight, 0xCC000000);
        guiGraphics.renderOutline(getX(), getY(), width, panelHeight, 0xFF555555);

        // Render entries
        int yPos = getY() + padding;
        int visibleCount = 0;

        for (int i = scrollOffset; i < activeActions.size() && visibleCount < MAX_VISIBLE_ENTRIES; i++) {
            GroupActionEntry entry = activeActions.get(i);

            if (yPos + entryHeight > getY() + height) break;

            renderEntry(guiGraphics, entry, yPos, mouseX, mouseY);
            yPos += entryHeight;
            visibleCount++;
        }
    }

    private void renderEntry(GuiGraphics guiGraphics, GroupActionEntry entry, int yPos, int mouseX, int mouseY) {
        int xPos = getX() + padding;

        // Entry background
        boolean hoveredCancel = isMouseOverCancelButton(mouseX, mouseY, yPos);
        int bgColor = hoveredCancel ? 0x55444444 : 0x33222222;
        guiGraphics.fill(getX() + 2, yPos, getX() + width - 2, yPos + entryHeight - 2, bgColor);

        // Group name
        String groupName = entry.group.getName();
        guiGraphics.drawString(worldMapScreen.getMinecraft().font, groupName, xPos, yPos + 2, 0xFFFFAA, false);

        // Action text
        guiGraphics.drawString(worldMapScreen.getMinecraft().font, entry.actionText, xPos, yPos + 12, 0xCCCCCC, false);

        // Cancel button (X)
        int cancelX = getX() + width - cancelButtonSize - padding;
        int cancelY = yPos + (entryHeight - cancelButtonSize) / 2;

        int cancelBg = hoveredCancel ? 0xFFFF4444 : 0xFF883333;
        guiGraphics.fill(cancelX, cancelY, cancelX + cancelButtonSize, cancelY + cancelButtonSize, cancelBg);

        // Draw X
        int xSize = 3;
        int centerX = cancelX + cancelButtonSize / 2;
        int centerY = cancelY + cancelButtonSize / 2;

        // Diagonal lines for X
        for (int i = -xSize; i <= xSize; i++) {
            guiGraphics.fill(centerX + i, centerY + i, centerX + i + 1, centerY + i + 1, 0xFFFFFFFF);
            guiGraphics.fill(centerX + i, centerY - i, centerX + i + 1, centerY - i + 1, 0xFFFFFFFF);
        }
    }

    private boolean isMouseOverCancelButton(int mouseX, int mouseY, int entryYPos) {
        int cancelX = getX() + width - cancelButtonSize - padding;
        int cancelY = entryYPos + (entryHeight - cancelButtonSize) / 2;

        return mouseX >= cancelX && mouseX <= cancelX + cancelButtonSize &&
               mouseY >= cancelY && mouseY <= cancelY + cancelButtonSize;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || activeActions.isEmpty()) return false;

        int yPos = getY() + padding;
        int visibleCount = 0;

        for (int i = scrollOffset; i < activeActions.size() && visibleCount < MAX_VISIBLE_ENTRIES; i++) {
            GroupActionEntry entry = activeActions.get(i);

            if (yPos + entryHeight > getY() + height) break;

            if (isMouseOverCancelButton((int)mouseX, (int)mouseY, yPos)) {
                cancelAction(entry);
                return true;
            }

            yPos += entryHeight;
            visibleCount++;
        }

        return false;
    }

    private void cancelAction(GroupActionEntry entry) {
        // Cancel action = Hold your position (state 2)
        UUID playerUUID = player.getUUID();
        int formation = com.talhanation.recruits.client.ClientManager.formationSelection;

        // Remove target marker FIRST for immediate visual feedback
        worldMapScreen.removeGroupTargetPosition(entry.group.getUUID());

        // Remove from active actions list immediately
        activeActions.remove(entry);

        // Send cancel command to server
        Main.SIMPLE_CHANNEL.sendToServer(
            new com.talhanation.recruits.network.MessageMovement(
                playerUUID,
                2, // Hold your position
                entry.group.getUUID(),
                formation
            )
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (activeActions.size() <= MAX_VISIBLE_ENTRIES) return false;

        if (mouseX >= getX() && mouseX <= getX() + width &&
            mouseY >= getY() && mouseY <= getY() + height) {

            int maxScroll = Math.max(0, activeActions.size() - MAX_VISIBLE_ENTRIES);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)delta));
            return true;
        }

        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Component.literal("Active group actions"));
    }

    private static class GroupActionEntry {
        final RecruitsGroup group;
        final ActionType type;
        final String actionText;
        final net.minecraft.core.BlockPos targetPos; // Can be null for actions without a position

        GroupActionEntry(RecruitsGroup group, ActionType type, String actionText, net.minecraft.core.BlockPos targetPos) {
            this.group = group;
            this.type = type;
            this.actionText = actionText;
            this.targetPos = targetPos;
        }
    }

    private enum ActionType {
        MOVING,
        BACK_TO_POSITION,
        FOLLOWING
    }
}




