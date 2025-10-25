package com.direwolf20.buildinggadgets.common.processTileEntity;

import com.direwolf20.buildinggadgets.common.processTileEntity.utils.BlockAnalysisResult;
import com.direwolf20.buildinggadgets.common.processTileEntity.utils.ITileEntityProcessor;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static com.direwolf20.buildinggadgets.common.processTileEntity.utils.modUtils.isGregTechLoaded;

public class TileEntityCopyHandler {

    public static BlockAnalysisResult copyTEData(World world, BlockPos tempPos, EntityPlayer player) {

        //处理MTE 这是GT专属的
        if (isGregTechLoaded()) {
            MetaTileEntity metaTileEntity = GTUtility.getMetaTileEntity(world, tempPos);
            if (metaTileEntity != null) {
                ITileEntityProcessor processor = TileEntityProcessorRegistry.findProcessor(metaTileEntity);

                if (processor != null) {
                    return processor.toNBT(world, tempPos, metaTileEntity);
                }
            }
        }

        // 处理TE 这是普通的
        TileEntity tileEntity = world.getTileEntity(tempPos);
        if (tileEntity != null) {
            ITileEntityProcessor processor = TileEntityProcessorRegistry.findProcessor(tileEntity);
            if (processor != null) {
                return processor.toNBT(world, tempPos, tileEntity);
            }
        }

        return BlockAnalysisResult.empty();
    }

}
