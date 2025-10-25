package com.direwolf20.buildinggadgets.common.processTileEntity;

import com.direwolf20.buildinggadgets.common.processTileEntity.tileEntity.gregtech.te.GTPipeBaseHandler;
import com.direwolf20.buildinggadgets.common.processTileEntity.utils.ITileEntityProcessor;
import gregtech.api.metatileentity.MetaTileEntity;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.direwolf20.buildinggadgets.common.processTileEntity.utils.modUtils.isAELoaded;
import static com.direwolf20.buildinggadgets.common.processTileEntity.utils.modUtils.isGregTechLoaded;

public class TileEntityProcessorRegistry {
    private static final Map<String, ITileEntityProcessor> PROCESSORS = new HashMap<>();
    private static final List<ITileEntityProcessor> PROCESSOR_LIST = new ArrayList<>();

    /**
     * 注册处理器
     */
    public static void registerProcessor(ITileEntityProcessor processor) {
        PROCESSORS.put(processor.getType(), processor);
        PROCESSOR_LIST.add(processor);
    }

    /**
     * 根据类型获取处理器
     */
    public static ITileEntityProcessor getProcessor(String type) {
        return PROCESSORS.get(type);
    }

    /**
     * 根据TileEntity查找支持的处理器
     */
    public static ITileEntityProcessor findProcessor(TileEntity te) {
        for (ITileEntityProcessor processor : PROCESSOR_LIST) {
            if (processor.supports(te)) {
                return processor;
            }
        }
        return null;
    }

    /**
     * 根据MetaTileEntity查找支持的处理器
     */
    public static ITileEntityProcessor findProcessor(MetaTileEntity mte) {
        for (ITileEntityProcessor processor : PROCESSOR_LIST) {
            if (processor.supports(mte)) {
                return processor;
            }
        }
        return null;
    }

    /**
     * 初始化注册所有处理器
     */
    public static void init() {
        if(isGregTechLoaded()){
            registerProcessor(new GTPipeBaseHandler());
        }
        if(isAELoaded()){

        }
    }
}