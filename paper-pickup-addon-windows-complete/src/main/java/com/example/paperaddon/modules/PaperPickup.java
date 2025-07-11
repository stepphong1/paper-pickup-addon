package com.example.paperaddon.modules;

import com.example.paperaddon.PaperAddon;
import com.example.paperaddon.utils.ItemUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Module that automatically picks up paper items within a specified radius
 * Includes safety checks and configurable options
 */
public class PaperPickup extends Module {
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    
    // General settings
    private final Setting<Double> pickupRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("pickup-radius")
        .description("Radius in blocks to search for paper items")
        .defaultValue(6.0)
        .min(1.0)
        .max(16.0)
        .sliderMax(10.0)
        .build()
    );
    
    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Delay between pickup attempts in ticks")
        .defaultValue(5)
        .min(1)
        .max(20)
        .build()
    );
    
    private final Setting<Boolean> moveToItems = sgGeneral.add(new BoolSetting.Builder()
        .name("move-to-items")
        .description("Automatically move towards paper items")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Double> moveSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("move-speed")
        .description("Speed multiplier when moving to items")
        .defaultValue(1.0)
        .min(0.1)
        .max(2.0)
        .visible(moveToItems::get)
        .build()
    );
    
    // Safety settings
    private final Setting<Boolean> avoidFalling = sgSafety.add(new BoolSetting.Builder()
        .name("avoid-falling")
        .description("Prevent falling when moving to items")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> avoidLava = sgSafety.add(new BoolSetting.Builder()
        .name("avoid-lava")
        .description("Don't pickup items in lava")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Integer> maxItemsPerTick = sgSafety.add(new IntSetting.Builder()
        .name("max-items-per-tick")
        .description("Maximum items to process per tick")
        .defaultValue(3)
        .min(1)
        .max(10)
        .build()
    );
    
    // Filter settings
    private final Setting<Boolean> onlyPaper = sgFilter.add(new BoolSetting.Builder()
        .name("only-paper")
        .description("Only pickup paper items")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Integer> minStackSize = sgFilter.add(new IntSetting.Builder()
        .name("min-stack-size")
        .description("Minimum stack size to pickup")
        .defaultValue(1)
        .min(1)
        .max(64)
        .build()
    );
    
    // Internal state
    private final ConcurrentHashMap<ItemEntity, Long> targetedItems = new ConcurrentHashMap<>();
    private final AtomicInteger tickCounter = new AtomicInteger(0);
    private Vec3d lastPlayerPos = Vec3d.ZERO;
    private long lastPickupTime = 0;
    
    public PaperPickup() {
        super(PaperAddon.CATEGORY, "paper-pickup", "Automatically picks up paper items within range");
    }
    
    @Override
    public void onActivate() {
        targetedItems.clear();
        tickCounter.set(0);
        lastPickupTime = 0;
        
        if (mc.player != null) {
            lastPlayerPos = mc.player.getPos();
        }
        
        info("Paper pickup activated - radius: %.1f blocks", pickupRadius.get());
    }
    
    @Override
    public void onDeactivate() {
        targetedItems.clear();
        info("Paper pickup deactivated");
    }
    
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        
        // Respect tick delay
        if (tickCounter.incrementAndGet() < tickDelay.get()) return;
        tickCounter.set(0);
        
        try {
            // Clean up old targeted items
            cleanupTargetedItems();
            
            // Find paper items in range
            List<ItemEntity> paperItems = findPaperItems();
            
            if (paperItems.isEmpty()) return;
            
            // Process items with safety limits
            int processed = 0;
            for (ItemEntity item : paperItems) {
                if (processed >= maxItemsPerTick.get()) break;
                
                if (shouldPickupItem(item)) {
                    processItem(item);
                    processed++;
                }
            }
            
            lastPlayerPos = mc.player.getPos();
            
        } catch (Exception e) {
            error("Error in paper pickup tick: %s", e.getMessage());
        }
    }
    
    /**
     * Find all paper items within the specified radius
     */
    private List<ItemEntity> findPaperItems() {
        if (mc.world == null || mc.player == null) return List.of();
        
        Vec3d playerPos = mc.player.getPos();
        double radius = pickupRadius.get();
        
        Box searchBox = new Box(
            playerPos.x - radius, playerPos.y - radius, playerPos.z - radius,
            playerPos.x + radius, playerPos.y + radius, playerPos.z + radius
        );
        
        return mc.world.getEntitiesByClass(ItemEntity.class, searchBox, this::isValidPaperItem);
    }
    
    /**
     * Check if an item entity is a valid paper item
     */
    private boolean isValidPaperItem(ItemEntity item) {
        if (item == null || !item.isAlive() || item.getStack().isEmpty()) return false;
        
        // Check if it's paper (if filter is enabled)
        if (onlyPaper.get() && !item.getStack().isOf(Items.PAPER)) return false;
        
        // Check minimum stack size
        if (item.getStack().getCount() < minStackSize.get()) return false;
        
        // Check if item is in lava
        if (avoidLava.get() && ItemUtils.isInLava(item)) return false;
        
        // Check distance
        if (mc.player != null) {
            double distance = mc.player.getPos().distanceTo(item.getPos());
            return distance <= pickupRadius.get();
        }
        
        return true;
    }
    
    /**
     * Determine if we should pickup this specific item
     */
    private boolean shouldPickupItem(ItemEntity item) {
        if (item == null || !item.isAlive()) return false;
        
        // Check if already targeted recently
        Long targetTime = targetedItems.get(item);
        if (targetTime != null && System.currentTimeMillis() - targetTime < 1000) {
            return false;
        }
        
        // Check pickup cooldown
        if (System.currentTimeMillis() - lastPickupTime < 100) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Process an individual item for pickup
     */
    private void processItem(ItemEntity item) {
        if (mc.player == null) return;
        
        Vec3d itemPos = item.getPos();
        Vec3d playerPos = mc.player.getPos();
        
        // Mark item as targeted
        targetedItems.put(item, System.currentTimeMillis());
        
        // Calculate distance
        double distance = playerPos.distanceTo(itemPos);
        
        // If close enough, try direct pickup
        if (distance <= 2.0) {
            attemptPickup(item);
            return;
        }
        
        // Move towards item if enabled
        if (moveToItems.get() && distance > 2.0) {
            moveTowardsItem(item);
        }
    }
    
    /**
     * Attempt to pickup an item directly
     */
    private void attemptPickup(ItemEntity item) {
        if (mc.player == null || mc.interactionManager == null) return;
        
        try {
            // The item will be picked up automatically by the game when the player is close enough
            // We just need to ensure the player is in range
            Vec3d itemPos = item.getPos();
            Vec3d playerPos = mc.player.getPos();
            
            if (playerPos.distanceTo(itemPos) <= 2.0) {
                lastPickupTime = System.currentTimeMillis();
                targetedItems.remove(item);
                
                // Log successful pickup attempt
                if (item.getStack().isOf(Items.PAPER)) {
                    info("Picking up %d paper at %.1f, %.1f, %.1f", 
                         item.getStack().getCount(), itemPos.x, itemPos.y, itemPos.z);
                }
            }
        } catch (Exception e) {
            error("Failed to pickup item: %s", e.getMessage());
        }
    }
    
    /**
     * Move the player towards an item
     */
    private void moveTowardsItem(ItemEntity item) {
        if (mc.player == null) return;
        
        Vec3d itemPos = item.getPos();
        Vec3d playerPos = mc.player.getPos();
        
        // Calculate direction vector
        Vec3d direction = itemPos.subtract(playerPos).normalize();
        
        // Apply safety checks
        if (avoidFalling.get() && wouldCauseFalling(direction)) {
            return;
        }
        
        // Apply movement
        double speed = moveSpeed.get() * 0.1;
        Vec3d movement = direction.multiply(speed);
        
        // Apply movement to player
        mc.player.setVelocity(
            mc.player.getVelocity().x + movement.x,
            mc.player.getVelocity().y,
            mc.player.getVelocity().z + movement.z
        );
    }
    
    /**
     * Check if movement would cause the player to fall
     */
    private boolean wouldCauseFalling(Vec3d direction) {
        if (mc.player == null || mc.world == null) return true;
        
        Vec3d playerPos = mc.player.getPos();
        Vec3d newPos = playerPos.add(direction.multiply(0.5));
        
        // Check if there's solid ground below the new position
        return !mc.world.getBlockState(
            new net.minecraft.util.math.BlockPos(
                (int) newPos.x, 
                (int) newPos.y - 1, 
                (int) newPos.z
            )
        ).isSolidBlock(mc.world, new net.minecraft.util.math.BlockPos(
            (int) newPos.x, 
            (int) newPos.y - 1, 
            (int) newPos.z
        ));
    }
    
    /**
     * Clean up old targeted items
     */
    private void cleanupTargetedItems() {
        long currentTime = System.currentTimeMillis();
        targetedItems.entrySet().removeIf(entry -> {
            ItemEntity item = entry.getKey();
            Long targetTime = entry.getValue();
            
            // Remove if item is dead or target time is too old
            return !item.isAlive() || (currentTime - targetTime) > 5000;
        });
    }
}
