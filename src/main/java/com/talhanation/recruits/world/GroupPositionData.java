package com.talhanation.recruits.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Stores position data for a recruit group for map rendering
 */
public class GroupPositionData {
    private UUID groupUUID;
    private double x;
    private double z;
    private int memberCount;

    public GroupPositionData(UUID groupUUID, double x, double z, int memberCount) {
        this.groupUUID = groupUUID;
        this.x = x;
        this.z = z;
        this.memberCount = memberCount;
    }

    public UUID getGroupUUID() {
        return groupUUID;
    }

    public double getX() {
        return x;
    }

    public double getZ() {
        return z;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("groupUUID", groupUUID);
        tag.putDouble("x", x);
        tag.putDouble("z", z);
        tag.putInt("memberCount", memberCount);
        return tag;
    }

    public static GroupPositionData fromNBT(CompoundTag tag) {
        return new GroupPositionData(
            tag.getUUID("groupUUID"),
            tag.getDouble("x"),
            tag.getDouble("z"),
            tag.getInt("memberCount")
        );
    }
}

