package com.direwolf20.buildinggadgets.common.processTileEntity.utils;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public class BlockAnalysisResult {
    private String type;
    private NBTTagCompound nbtData;
    private BlockPos relativePos;

    // 私有构造函数，强制使用 Builder
    private BlockAnalysisResult() {}

    public String getType() { return type; }
    public NBTTagCompound getNbtData() { return nbtData; }
    public BlockPos getRelativePos() { return relativePos; }

    public boolean isSupported() {
        return type != null && !type.isEmpty();
    }

    /**
     * Builder 类
     */
    public static class Builder {
        private final BlockAnalysisResult result = new BlockAnalysisResult();

        public Builder setType(String type) {
            result.type = type;
            return this;
        }

        public Builder setNbtData(NBTTagCompound nbtData) {
            result.nbtData = nbtData;
            return this;
        }

        public Builder setRelativePos(BlockPos relativePos) {
            result.relativePos = relativePos;
            return this;
        }

        public BlockAnalysisResult build() {
            return result;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // 空结果
    public static BlockAnalysisResult empty() {
        return new BlockAnalysisResult();
    }
}