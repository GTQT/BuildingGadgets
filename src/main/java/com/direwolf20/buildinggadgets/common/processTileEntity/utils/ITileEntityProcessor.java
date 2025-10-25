package com.direwolf20.buildinggadgets.common.processTileEntity.utils;

import gregtech.api.metatileentity.MetaTileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface ITileEntityProcessor {
    /**
     * 返回处理器支持的类型标识符
     */
    String getType();

    /**
     * 检查是否支持处理该TileEntity
     */
    boolean supports(TileEntity te);

    /**
     * 检查是否支持处理该MetaTileEntity
     */
    boolean supports(MetaTileEntity mte);

    /**
     * 从TileEntity读取数据到NBT
     */
    BlockAnalysisResult toNBT(World world, BlockPos pos, TileEntity te);

    /**
     * 从MetaTileEntity读取数据到NBT
     */
    BlockAnalysisResult toNBT(World world, BlockPos pos, MetaTileEntity mte);

    /**
     * 从NBT恢复数据到TileEntity
     */
    boolean fromNBT(World world, BlockPos pos, NBTTagCompound nbt);
}