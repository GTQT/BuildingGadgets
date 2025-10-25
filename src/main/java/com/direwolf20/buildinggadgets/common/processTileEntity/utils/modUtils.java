package com.direwolf20.buildinggadgets.common.processTileEntity.utils;

import net.minecraftforge.fml.common.Loader;

public class modUtils {

    public static boolean isGregTechLoaded() {
        return Loader.isModLoaded("gregtech");
    }

    public static boolean isAELoaded() {
        return Loader.isModLoaded("appliedenergistics2");
    }

}
