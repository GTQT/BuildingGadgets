package com.direwolf20.buildinggadgets.common.processTileEntity.tileEntity.gregtech.te;

import com.direwolf20.buildinggadgets.common.processTileEntity.utils.ITileEntityProcessor;
import com.direwolf20.buildinggadgets.common.processTileEntity.utils.BlockAnalysisResult;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.pipenet.tile.TileEntityPipeBase;
import gregtech.common.pipelike.cable.tile.TileEntityCable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Field;

public class GTPipeBaseHandler implements ITileEntityProcessor {
    @Override
    public String getType() {
        return "gregtech:pipe_base";
    }

    @Override
    public boolean supports(TileEntity te) {
        return te instanceof TileEntityCable;
    }

    @Override
    public boolean supports(MetaTileEntity mte) {
        return false; // 电缆不是MTE
    }
    @Override
    public BlockAnalysisResult toNBT(World world, BlockPos pos, TileEntity te) {
        TileEntityCable cable = (TileEntityCable) te;
        try {
            NBTTagCompound cableNBT = new NBTTagCompound();


            // 保存连接信息
            NBTTagCompound connectionsNBT = new NBTTagCompound();

            int connections = getConnections(world, pos);
            connectionsNBT.setInteger("connections", connections);
            cableNBT.setTag("pipe_base", connectionsNBT);

            return BlockAnalysisResult.builder()
                    .setType(getType())
                    .setNbtData(cableNBT)
                    .setRelativePos(pos)
                    .build();

        } catch (Exception e) {

            return BlockAnalysisResult.empty();
        }
    }

    @Override
    public BlockAnalysisResult toNBT(World world, BlockPos pos, MetaTileEntity mte) {
        return BlockAnalysisResult.empty(); // 不支持MTE
    }

    @Override
    public boolean fromNBT(World world, BlockPos pos, NBTTagCompound nbt) {
        // 粘贴阶段的实现
        try {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityCable) {
                TileEntityCable cable = (TileEntityCable) te;


                // 恢复连接信息
                if (nbt.hasKey("connections")) {
                    NBTTagCompound pipeBase = nbt.getCompoundTag("pipe_base");
                    int connections = pipeBase.getInteger("connections");
                    setConnections(world, pos, connections);
                }

                cable.markDirty();
                return true;
            }
        } catch (Exception e) {

        }
        return false;
    }
    public boolean setConnections(World world, BlockPos pos, int connections) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityPipeBase pipe) {
            return setPipeConnections(pipe, connections);
        }
        return false;
    }

    public int getConnections(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityPipeBase  pipe) {
            return getPipeConnections(pipe);
        }
        return 0;
    }

    /**
     * 通用的连接状态设置方法
     */
    private static boolean setPipeConnections(TileEntityPipeBase pipe, int connections) {
        try {
            Field connectionsField = TileEntityPipeBase.class.getDeclaredField("connections");
            connectionsField.setAccessible(true);
            connectionsField.setInt(pipe, connections);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通用的连接状态获取方法
     */
    private static int getPipeConnections(TileEntityPipeBase pipe) {
        try {
            Field connectionsField = TileEntityPipeBase.class.getDeclaredField("connections");
            connectionsField.setAccessible(true);
            return connectionsField.getInt(pipe);
        } catch (Exception e) {
            return 0;
        }
    }
}
