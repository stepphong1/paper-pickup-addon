package com.example.paperaddon.utils;

import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Utility class for item-related operations
 */
public class ItemUtils {
    
    /**
     * Check if an item entity is currently in lava
     */
    public static boolean isInLava(ItemEntity item) {
        if (item == null || item.getWorld() == null) return false;
        
        World world = item.getWorld();
        BlockPos itemPos = item.getBlockPos();
        
        // Check the block the item is in
        if (world.getBlockState(itemPos).getBlock() == Blocks.LAVA) {
            return true;
        }
        
        // Check the block below (in case item is floating above lava)
        if (world.getBlockState(itemPos.down()).getBlock() == Blocks.LAVA) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if an item entity is in a safe location to pickup
     */
    public static boolean isSafeLocation(ItemEntity item) {
        if (item == null || item.getWorld() == null) return false;
        
        World world = item.getWorld();
        BlockPos itemPos = item.getBlockPos();
        
        // Check for lava
        if (isInLava(item)) return false;
        
        // Check for cactus
        if (world.getBlockState(itemPos).getBlock() == Blocks.CACTUS) {
            return false;
        }
        
        // Check for fire
        if (world.getBlockState(itemPos).getBlock() == Blocks.FIRE) {
            return false;
        }
        
        // Check for void (below y=0)
        if (itemPos.getY() < 0) return false;
        
        return true;
    }
    
    /**
     * Get the distance from a player position to an item
     */
    public static double getDistanceToItem(ItemEntity item, double playerX, double playerY, double playerZ) {
        if (item == null) return Double.MAX_VALUE;
        
        double dx = item.getX() - playerX;
        double dy = item.getY() - playerY;
        double dz = item.getZ() - playerZ;
        
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Check if an item is within a cubic area
     */
    public static boolean isWithinCube(ItemEntity item, double centerX, double centerY, double centerZ, double radius) {
        if (item == null) return false;
        
        double dx = Math.abs(item.getX() - centerX);
        double dy = Math.abs(item.getY() - centerY);
        double dz = Math.abs(item.getZ() - centerZ);
        
        return dx <= radius && dy <= radius && dz <= radius;
    }
    
    /**
     * Check if an item is within a spherical area
     */
    public static boolean isWithinSphere(ItemEntity item, double centerX, double centerY, double centerZ, double radius) {
        if (item == null) return false;
        
        return getDistanceToItem(item, centerX, centerY, centerZ) <= radius;
    }
}
