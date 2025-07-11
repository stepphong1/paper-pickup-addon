package com.example.paperaddon;

import com.example.paperaddon.modules.PaperPickup;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main addon class for the Paper Pickup addon
 * Automatically picks up paper items within a configurable radius
 */
public class PaperAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("PaperAddon");
    public static final Category CATEGORY = new Category("Paper Pickup", Items.PAPER.getDefaultStack());

    @Override
    public void onInitialize() {
        LOG.info("Initializing Paper Pickup Addon for Minecraft 1.21.4");
        
        // Register the paper pickup module
        Modules.get().add(new PaperPickup());
        
        LOG.info("Paper Pickup Addon initialized successfully");
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.paperaddon";
    }

    @Override
    public String getWebsite() {
        return "https://github.com/example/paper-addon";
    }

    @Override
    public String getName() {
        return "Paper Pickup";
    }

    @Override
    public String getAuthor() {
        return "PaperAddon";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
