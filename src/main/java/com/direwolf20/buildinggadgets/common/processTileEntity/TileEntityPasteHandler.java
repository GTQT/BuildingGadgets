package com.direwolf20.buildinggadgets.common.processTileEntity;

import com.direwolf20.buildinggadgets.common.processTileEntity.utils.ITileEntityProcessor;
import com.direwolf20.buildinggadgets.common.tools.GadgetUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TileEntityPasteHandler {
    public static boolean pasteTEData(World world, BlockPos startPos, NBTTagList teDataList) {
        boolean success = false;

        for (int i = 0; i < teDataList.tagCount(); i++) {
            NBTTagCompound teTag = teDataList.getCompoundTagAt(i);
            String type = teTag.getString("type");
            int relPos = teTag.getInteger("pos");
            BlockPos targetPos = GadgetUtils.relIntToPos(startPos, relPos);

            // 根据类型找到处理器
            ITileEntityProcessor processor = TileEntityProcessorRegistry.getProcessor(type);
            if (processor != null && teTag.hasKey("data")) {
                NBTTagCompound data = teTag.getCompoundTag("data");
                if (processor.fromNBT(world, targetPos, data)) {
                    success = true;
                }
            }
        }

        return success;
    }
}
