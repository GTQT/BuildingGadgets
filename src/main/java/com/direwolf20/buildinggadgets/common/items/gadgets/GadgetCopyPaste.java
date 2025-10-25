package com.direwolf20.buildinggadgets.common.items.gadgets;

import com.direwolf20.buildinggadgets.client.events.EventTooltip;
import com.direwolf20.buildinggadgets.client.gui.GuiProxy;
import com.direwolf20.buildinggadgets.BuildingGadgets;
import com.direwolf20.buildinggadgets.common.blocks.ConstructionBlock;
import com.direwolf20.buildinggadgets.common.blocks.ConstructionBlockTileEntity;
import com.direwolf20.buildinggadgets.common.blocks.EffectBlock;
import com.direwolf20.buildinggadgets.common.config.SyncedConfig;
import com.direwolf20.buildinggadgets.common.entities.BlockBuildEntity;
import com.direwolf20.buildinggadgets.common.items.ITemplate;
import com.direwolf20.buildinggadgets.common.items.ModItems;
import com.direwolf20.buildinggadgets.common.network.PacketBlockMap;
import com.direwolf20.buildinggadgets.common.network.PacketHandler;
import com.direwolf20.buildinggadgets.common.network.PacketRotateMirror;
import com.direwolf20.buildinggadgets.common.tools.*;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 复制粘贴工具类
 * 允许玩家复制建筑区域并将其粘贴到其他位置
 */
public class GadgetCopyPaste extends GadgetGeneric implements ITemplate {

    /**
     * 工具模式枚举
     */
    public enum ToolMode {
        Copy, // 复制模式
        Paste; // 粘贴模式

        private static ToolMode[] vals = values();

        /**
         * 切换到下一个模式
         */
        public ToolMode next() {
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    /**
     * 构造函数
     */
    public GadgetCopyPaste() {
        super("copypastetool");
        setMaxDamage(SyncedConfig.durabilityCopyPaste);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        // 如果使用FE能量系统，耐久度为0，否则使用配置的耐久度
        return SyncedConfig.poweredByFE ? 0 : SyncedConfig.durabilityCopyPaste;
    }

    @Override
    public int getEnergyCost(ItemStack tool) {
        return SyncedConfig.energyCostCopyPaste;
    }

    @Override
    public int getDamageCost(ItemStack tool) {
        return SyncedConfig.damageCostCopyPaste;
    }

    /**
     * 设置锚点位置（粘贴的起始点）
     */
    private static void setAnchor(ItemStack stack, BlockPos anchorPos) {
        GadgetUtils.writePOSToNBT(stack, anchorPos, "anchor");
    }

    /**
     * 设置X轴偏移量
     */
    public static void setX(ItemStack stack, int horz) {
        GadgetUtils.writeIntToNBT(stack, horz, "X");
    }

    /**
     * 设置Y轴偏移量
     */
    public static void setY(ItemStack stack, int vert) {
        GadgetUtils.writeIntToNBT(stack, vert, "Y");
    }

    /**
     * 设置Z轴偏移量
     */
    public static void setZ(ItemStack stack, int depth) {
        GadgetUtils.writeIntToNBT(stack, depth, "Z");
    }

    /**
     * 获取X轴偏移量
     */
    public static int getX(ItemStack stack) {
        return GadgetUtils.getIntFromNBT(stack, "X");
    }

    /**
     * 获取Y轴偏移量
     */
    public static int getY(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) return 1;
        if (!tagCompound.hasKey("Y")) return 1;
        Integer tagInt = tagCompound.getInteger("Y");
        return tagInt;
    }

    /**
     * 获取Z轴偏移量
     */
    public static int getZ(ItemStack stack) {
        return GadgetUtils.getIntFromNBT(stack, "Z");
    }

    /**
     * 获取锚点位置
     */
    public static BlockPos getAnchor(ItemStack stack) {
        return GadgetUtils.getPOSFromNBT(stack, "anchor");
    }

    @Override
    public WorldSave getWorldSave(World world) {
        return WorldSave.getWorldSave(world);
    }

    @Override
    @Nullable
    public String getUUID(ItemStack stack) {
        // 为每个复制操作生成唯一标识符
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            return null;
        }
        String uuid = tagCompound.getString("UUID");
        if (uuid.isEmpty()) {
            if (getStartPos(stack) == null && getEndPos(stack) == null) {
                return null;
            }
            UUID uid = UUID.randomUUID();
            tagCompound.setString("UUID", uid.toString());
            stack.setTagCompound(tagCompound);
            uuid = uid.toString();
        }
        return uuid;
    }

    /**
     * 获取工具所有者（未使用）
     */
    public static String getOwner(ItemStack stack) {
        return GadgetUtils.getStackTag(stack).getString("owner");
    }

    /**
     * 设置工具所有者（未使用）
     */
    public static void setOwner(ItemStack stack, String owner) {
        NBTTagCompound tagCompound = GadgetUtils.getStackTag(stack);
        tagCompound.setString("owner", owner);
        stack.setTagCompound(tagCompound);
    }

    /**
     * 设置最后一次构建的位置和维度
     */
    private static void setLastBuild(ItemStack stack, BlockPos anchorPos, Integer dim) {
        GadgetUtils.writePOSToNBT(stack, anchorPos, "lastBuild", dim);
    }

    /**
     * 获取最后一次构建的位置
     */
    private static BlockPos getLastBuild(ItemStack stack) {
        return GadgetUtils.getPOSFromNBT(stack, "lastBuild");
    }

    /**
     * 获取最后一次构建的维度
     */
    private static Integer getLastBuildDim(ItemStack stack) {
        return GadgetUtils.getDIMFromNBT(stack, "lastBuild");
    }

    /**
     * 从NBT数据获取方块映射列表
     */
    public static List<BlockMap> getBlockMapList(@Nullable NBTTagCompound tagCompound) {
        return getBlockMapList(tagCompound, GadgetUtils.getPOSFromNBT(tagCompound, "startPos"));
    }

    /**
     * 从NBT数据获取方块映射列表（指定起始位置）
     */
    private static List<BlockMap> getBlockMapList(@Nullable NBTTagCompound tagCompound, BlockPos startBlock) {
        List<BlockMap> blockMap = new ArrayList<BlockMap>();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }

        // 读取方块状态映射
        NBTTagList MapIntStateTag = (NBTTagList) tagCompound.getTag("mapIntState");
        if (MapIntStateTag == null) {
            MapIntStateTag = new NBTTagList();
        }
        BlockMapIntState MapIntState = new BlockMapIntState();
        MapIntState.getIntStateMapFromNBT(MapIntStateTag);

        // 读取位置和状态数组
        int[] posIntArray = tagCompound.getIntArray("posIntArray");
        int[] stateIntArray = tagCompound.getIntArray("stateIntArray");

        // 重建方块映射
        for (int i = 0; i < posIntArray.length; i++) {
            int p = posIntArray[i];
            BlockPos pos = GadgetUtils.relIntToPos(startBlock, p);
            short IntState = (short) stateIntArray[i];
            blockMap.add(new BlockMap(pos, MapIntState.getStateFromSlot(IntState),
                    (byte) ((p & 0xff0000) >> 16), (byte) ((p & 0x00ff00) >> 8), (byte) (p & 0x0000ff)));
        }
        return blockMap;
    }

    /**
     * 获取方块状态和物品堆栈的映射
     */
    public static BlockMapIntState getBlockMapIntState(@Nullable NBTTagCompound tagCompound) {
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }

        // 读取方块状态映射
        NBTTagList MapIntStateTag = (NBTTagList) tagCompound.getTag("mapIntState");
        if (MapIntStateTag == null) {
            MapIntStateTag = new NBTTagList();
        }

        // 读取物品堆栈映射
        NBTTagList MapIntStackTag = (NBTTagList) tagCompound.getTag("mapIntStack");
        if (MapIntStackTag == null) {
            MapIntStackTag = new NBTTagList();
        }

        BlockMapIntState MapIntState = new BlockMapIntState();
        MapIntState.getIntStateMapFromNBT(MapIntStateTag);
        MapIntState.getIntStackMapFromNBT(MapIntStackTag);
        return MapIntState;
    }

    /**
     * 设置工具模式
     */
    private static void setToolMode(ItemStack stack, ToolMode mode) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }
        tagCompound.setString("mode", mode.name());
        stack.setTagCompound(tagCompound);
    }

    /**
     * 获取工具模式
     */
    public static ToolMode getToolMode(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        ToolMode mode = ToolMode.Copy;
        if (tagCompound == null) {
            setToolMode(stack, mode);
            return mode;
        }
        try {
            mode = ToolMode.valueOf(tagCompound.getString("mode"));
        } catch (Exception e) {
            setToolMode(stack, mode);
        }
        return mode;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> list, ITooltipFlag b) {
        super.addInformation(stack, world, list, b);
        // 添加模式信息
        list.add(TextFormatting.AQUA + I18n.format("tooltip.gadget.mode") + ": " + getToolMode(stack));
        addInformationRayTraceFluid(list, stack);
        addEnergyInformation(list, stack);
        EventTooltip.addTemplatePadding(stack, list);
    }

    /**
     * 设置工具模式（通过径向菜单）
     */
    public void setMode(ItemStack heldItem, int modeInt) {
        ToolMode mode = ToolMode.values()[modeInt];
        setToolMode(heldItem, mode);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        BlockPos pos = VectorTools.getPosLookingAt(player, stack);

        if (!world.isRemote) {
            // 服务器端逻辑
            if (pos != null && player.isSneaking() && GadgetUtils.setRemoteInventory(stack, player, world, pos, false) == EnumActionResult.SUCCESS)
                return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);

            if (getToolMode(stack) == ToolMode.Copy) {
                // 复制模式逻辑
                if (pos == null) {
                    return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
                }
                if (player.isSneaking()) {
                    if (getStartPos(stack) != null)
                        copyBlocks(stack, player, world, getStartPos(stack), pos);
                    else
                        setEndPos(stack, pos);
                } else {
                    if (getEndPos(stack) != null)
                        copyBlocks(stack, player, world, pos, getEndPos(stack));
                    else
                        setStartPos(stack, pos);
                }
            } else if (getToolMode(stack) == ToolMode.Paste) {
                // 粘贴模式逻辑
                if (!player.isSneaking()) {
                    if (getAnchor(stack) == null) {
                        if (pos == null) return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
                        buildBlockMap(world, pos, stack, player);
                    } else {
                        BlockPos startPos = getAnchor(stack);
                        buildBlockMap(world, startPos, stack, player);
                    }
                }
            }
        } else {
            // 客户端逻辑
            if (pos != null && player.isSneaking()) {
                if (GadgetUtils.getRemoteInventory(pos, world, player, NetworkIO.Operation.EXTRACT) != null)
                    return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
            }
            if (getToolMode(stack) == ToolMode.Copy) {
                if (pos == null && player.isSneaking())
                    player.openGui(BuildingGadgets.instance, GuiProxy.CopyPasteID, world, hand.ordinal(), 0, 0);
            } else if (player.isSneaking()) {
                player.openGui(BuildingGadgets.instance, GuiProxy.PasteID, world, hand.ordinal(), 0, 0);
            } else {
                ToolRenders.updateInventoryCache();
            }
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /**
     * 旋转或镜像方块
     */
    public static void rotateOrMirrorBlocks(ItemStack stack, EntityPlayer player, PacketRotateMirror.Operation operation) {
        if (!(getToolMode(stack) == ToolMode.Paste)) return;
        if (player.world.isRemote) {
            return;
        }

        GadgetCopyPaste tool = ModItems.gadgetCopyPaste;
        List<BlockMap> blockMapList = new ArrayList<BlockMap>();
        WorldSave worldSave = WorldSave.getWorldSave(player.world);
        NBTTagCompound tagCompound = worldSave.getCompoundFromUUID(tool.getUUID(stack));
        BlockPos startPos = tool.getStartPos(stack);
        if (startPos == null) return;

        blockMapList = getBlockMapList(tagCompound);
        List<Integer> posIntArrayList = new ArrayList<Integer>();
        List<Integer> stateIntArrayList = new ArrayList<Integer>();
        BlockMapIntState blockMapIntState = new BlockMapIntState();

        // 对每个方块进行旋转或镜像变换
        for (BlockMap blockMap : blockMapList) {
            BlockPos tempPos = blockMap.pos;

            int px = (tempPos.getX() - startPos.getX());
            int pz = (tempPos.getZ() - startPos.getZ());
            int nx, nz;

            // 应用旋转或镜像变换
            IBlockState alteredState = GadgetUtils.rotateOrMirrorBlock(player, operation, blockMap.state);
            if (operation == PacketRotateMirror.Operation.MIRROR) {
                // 镜像变换
                if (player.getHorizontalFacing().getAxis() == Axis.X) {
                    nx = px;
                    nz = -pz;
                } else {
                    nx = -px;
                    nz = pz;
                }
            } else {
                // 旋转变换
                nx = -pz;
                nz = px;
            }

            BlockPos newPos = new BlockPos(startPos.getX() + nx, tempPos.getY(), startPos.getZ() + nz);
            posIntArrayList.add(GadgetUtils.relPosToInt(startPos, newPos));
            blockMapIntState.addToMap(alteredState);
            stateIntArrayList.add((int) blockMapIntState.findSlot(alteredState));
            UniqueItem uniqueItem = BlockMapIntState.blockStateToUniqueItem(alteredState, player, tempPos);
            blockMapIntState.addToStackMap(uniqueItem, alteredState);
        }

        // 更新NBT数据
        int[] posIntArray = posIntArrayList.stream().mapToInt(i -> i).toArray();
        int[] stateIntArray = stateIntArrayList.stream().mapToInt(i -> i).toArray();
        tagCompound.setTag("mapIntState", blockMapIntState.putIntStateMapIntoNBT());
        tagCompound.setTag("mapIntStack", blockMapIntState.putIntStackMapIntoNBT());
        tagCompound.setIntArray("posIntArray", posIntArray);
        tagCompound.setIntArray("stateIntArray", stateIntArray);
        tool.incrementCopyCounter(stack);
        tagCompound.setInteger("copycounter", tool.getCopyCounter(stack));

        // 保存并同步到客户端
        worldSave.addToMap(tool.getUUID(stack), tagCompound);
        worldSave.markForSaving();
        PacketHandler.INSTANCE.sendTo(new PacketBlockMap(tagCompound), (EntityPlayerMP) player);
        player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA
                + new TextComponentTranslation("message.gadget." + (player.isSneaking() ? "mirrored" : "rotated")).getUnformattedComponentText()), true);
    }

    /**
     * 复制方块区域
     */
    public static void copyBlocks(ItemStack stack, EntityPlayer player, World world, BlockPos startPos, BlockPos endPos) {
        if (startPos != null && endPos != null) {
            GadgetCopyPaste tool = ModItems.gadgetCopyPaste;
            if (findBlocks(world, startPos, endPos, stack, player, tool)) {
                tool.setStartPos(stack, startPos);
                tool.setEndPos(stack, endPos);
            }
        }
    }

    /**
     * 查找并复制区域内的方块
     */
    private static boolean findBlocks(World world, BlockPos start, BlockPos end, ItemStack stack, EntityPlayer player, GadgetCopyPaste tool) {
        setLastBuild(stack, null, 0);
        int foundTE = 0; // 找到的TileEntity数量

        // 计算区域边界
        int startX = start.getX();
        int startY = start.getY();
        int startZ = start.getZ();

        int endX = end.getX();
        int endY = end.getY();
        int endZ = end.getZ();

        // 检查区域是否过大
        if (Math.abs(startX - endX) >= 125 || Math.abs(startY - endY) >= 125 || Math.abs(startZ - endZ) >= 125) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED + new TextComponentTranslation("message.gadget.toobigarea").getUnformattedComponentText()), true);
            return false;
        }

        // 确定区域的起始和结束坐标
        int iStartX = startX < endX ? startX : endX;
        int iStartY = startY < endY ? startY : endY;
        int iStartZ = startZ < endZ ? startZ : endZ;
        int iEndX = startX < endX ? endX : startX;
        int iEndY = startY < endY ? endY : startY;
        int iEndZ = startZ < endZ ? endZ : startZ;

        WorldSave worldSave = WorldSave.getWorldSave(world);
        NBTTagCompound tagCompound = new NBTTagCompound();
        List<Integer> posIntArrayList = new ArrayList<Integer>();
        List<Integer> stateIntArrayList = new ArrayList<Integer>();
        BlockMapIntState blockMapIntState = new BlockMapIntState();
        Multiset<UniqueItem> itemCountMap = HashMultiset.create();

        int blockCount = 0;

        // 遍历区域内的所有方块
        for (int x = iStartX; x <= iEndX; x++) {
            for (int y = iStartY; y <= iEndY; y++) {
                for (int z = iStartZ; z <= iEndZ; z++) {
                    BlockPos tempPos = new BlockPos(x, y, z);
                    IBlockState tempState = world.getBlockState(tempPos);

                    // 检查方块是否可复制
                    if (!(tempState.getBlock() instanceof EffectBlock) &&
                            tempState != Blocks.AIR.getDefaultState() &&
                            (world.getTileEntity(tempPos) == null || world.getTileEntity(tempPos) instanceof ConstructionBlockTileEntity) &&
                            !tempState.getMaterial().isLiquid() &&
                            !SyncedConfig.blockBlacklist.contains(tempState.getBlock())) {

                        TileEntity te = world.getTileEntity(tempPos);
                        IBlockState assignState = InventoryManipulation.getSpecificStates(tempState, world, player, tempPos, stack);
                        IBlockState actualState = assignState.getActualState(world, tempPos);

                        if (te instanceof ConstructionBlockTileEntity) {
                            actualState = ((ConstructionBlockTileEntity) te).getActualBlockState();
                        }

                        if (actualState != null) {
                            UniqueItem uniqueItem = BlockMapIntState.blockStateToUniqueItem(actualState, player, tempPos);
                            if (uniqueItem.item != Items.AIR) {
                                // 记录方块位置和状态
                                posIntArrayList.add(GadgetUtils.relPosToInt(start, tempPos));
                                blockMapIntState.addToMap(actualState);
                                stateIntArrayList.add((int) blockMapIntState.findSlot(actualState));

                                blockMapIntState.addToStackMap(uniqueItem, actualState);
                                blockCount++;

                                // 检查方块数量限制
                                if (blockCount > 32768) {
                                    player.sendStatusMessage(new TextComponentString(TextFormatting.RED + new TextComponentTranslation("message.gadget.toomanyblocks").getUnformattedComponentText()), true);
                                    return false;
                                }

                                // 计算需要的物品数量
                                NonNullList<ItemStack> drops = NonNullList.create();
                                if (actualState != null)
                                    actualState.getBlock().getDrops(drops, world, new BlockPos(0, 0, 0), actualState, 0);

                                int neededItems = 0;
                                for (ItemStack drop : drops) {
                                    if (drop.getItem().equals(uniqueItem.item)) {
                                        neededItems++;
                                    }
                                }
                                if (neededItems == 0) {
                                    neededItems = 1;
                                }
                                itemCountMap.add(uniqueItem, neededItems);
                            }
                        }
                    } else if ((world.getTileEntity(tempPos) != null) && !(world.getTileEntity(tempPos) instanceof ConstructionBlockTileEntity)) {
                        // 统计不支持复制的TileEntity数量
                        foundTE++;
                    }
                }
            }
        }

        // 保存数据到NBT
        tool.setItemCountMap(stack, itemCountMap);
        tagCompound.setTag("mapIntState", blockMapIntState.putIntStateMapIntoNBT());
        tagCompound.setTag("mapIntStack", blockMapIntState.putIntStackMapIntoNBT());
        int[] posIntArray = posIntArrayList.stream().mapToInt(i -> i).toArray();
        int[] stateIntArray = stateIntArrayList.stream().mapToInt(i -> i).toArray();
        tagCompound.setIntArray("posIntArray", posIntArray);
        tagCompound.setIntArray("stateIntArray", stateIntArray);

        tagCompound.setTag("startPos", NBTUtil.createPosTag(start));
        tagCompound.setTag("endPos", NBTUtil.createPosTag(end));
        tagCompound.setInteger("dim", player.dimension);
        tagCompound.setString("UUID", tool.getUUID(stack));
        tagCompound.setString("owner", player.getName());
        tool.incrementCopyCounter(stack);
        tagCompound.setInteger("copycounter", tool.getCopyCounter(stack));

        // 保存到世界数据并同步到客户端
        worldSave.addToMap(tool.getUUID(stack), tagCompound);
        worldSave.markForSaving();
        PacketHandler.INSTANCE.sendTo(new PacketBlockMap(tagCompound), (EntityPlayerMP) player);

        // 发送操作结果消息
        if (foundTE > 0) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.YELLOW + new TextComponentTranslation("message.gadget.TEinCopy").getUnformattedComponentText() + ": " + foundTE), true);
        } else {
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("message.gadget.copied").getUnformattedComponentText()), true);
        }
        return true;
    }

    /**
     * 构建并放置方块映射
     */
    private void buildBlockMap(World world, BlockPos startPos, ItemStack stack, EntityPlayer player) {
        BlockPos anchorPos = getAnchor(stack);
        BlockPos pos = anchorPos == null ? startPos : anchorPos;
        NBTTagCompound tagCompound = WorldSave.getWorldSave(world).getCompoundFromUUID(getUUID(stack));

        // 应用偏移量
        pos = pos.up(GadgetCopyPaste.getY(stack));
        pos = pos.east(GadgetCopyPaste.getX(stack));
        pos = pos.south(GadgetCopyPaste.getZ(stack));

        // 获取方块映射列表并放置方块
        List<BlockMap> blockMapList = getBlockMapList(tagCompound, pos);
        setLastBuild(stack, pos, player.dimension);

        for (BlockMap blockMap : blockMapList)
            placeBlock(world, blockMap.pos, player, blockMap.state, getBlockMapIntState(tagCompound).getIntStackMap());

        GadgetUtils.clearCachedRemoteInventory();
        setAnchor(stack, null);
    }

    /**
     * 放置单个方块
     */
    private void placeBlock(World world, BlockPos pos, EntityPlayer player, IBlockState state, Map<IBlockState, UniqueItem> IntStackMap) {
        // 检查位置是否有效
        if( world.isOutsideBuildHeight(pos) )
            return;

        IBlockState testState = world.getBlockState(pos);
        // 检查是否可以覆盖方块
        if ((SyncedConfig.canOverwriteBlocks && !testState.getBlock().isReplaceable(world, pos)) ||
                (!SyncedConfig.canOverwriteBlocks && testState.getBlock().isAir(testState, world, pos)))
            return;

        if (pos.getY() < 0 || state.equals(Blocks.AIR.getDefaultState()) || !player.isAllowEdit())
            return;

        ItemStack heldItem = getGadget(player);
        if (heldItem.isEmpty())
            return;

        if (ModItems.gadgetCopyPaste.getStartPos(heldItem) == null || ModItems.gadgetCopyPaste.getEndPos(heldItem) == null)
            return;

        // 获取对应的物品
        UniqueItem uniqueItem = IntStackMap.get(state);
        if (uniqueItem == null) return;
        ItemStack itemStack = new ItemStack(uniqueItem.item, 1, uniqueItem.meta);

        // 计算需要的物品数量
        NonNullList<ItemStack> drops = NonNullList.create();
        state.getBlock().getDrops(drops, world, pos, state, 0);
        int neededItems = 0;
        for (ItemStack drop : drops) {
            if (drop.getItem().equals(itemStack.getItem())) {
                neededItems++;
            }
        }
        if (neededItems == 0) {
            neededItems = 1;
        }

        if (!world.isBlockModifiable(player, pos)) {
            return;
        }

        // 创建方块快照并检查事件
        BlockSnapshot blockSnapshot = BlockSnapshot.getBlockSnapshot(world, pos);
        if( !GadgetGeneric.EmitEvent.placeBlock(player, blockSnapshot, EnumFacing.UP, EnumHand.MAIN_HAND) )
            return;

        // 检查物品是否足够，如果不够则使用建筑浆糊
        ItemStack constructionPaste = new ItemStack(ModItems.constructionPaste);
        boolean useConstructionPaste = false;
        if (InventoryManipulation.countItem(itemStack, player, world) < neededItems) {
            if (InventoryManipulation.countPaste(player) < neededItems) {
                return;
            }
            itemStack = constructionPaste.copy();
            useConstructionPaste = true;
        }

        if (!this.canUse(heldItem, player))
            return;

        // 使用物品
        boolean useItemSuccess;
        if (useConstructionPaste) {
            useItemSuccess = InventoryManipulation.usePaste(player, 1);
        } else {
            useItemSuccess = InventoryManipulation.useItem(itemStack, player, neededItems, world);
        }

        // 如果物品使用成功，生成方块建造实体
        if (useItemSuccess) {
            this.applyDamage(heldItem, player);
            world.spawnEntity(new BlockBuildEntity(world, pos, player, state, 1, state, useConstructionPaste));
        }
    }

    /**
     * 设置或移除锚点
     */
    public static void anchorBlocks(EntityPlayer player, ItemStack stack) {
        BlockPos currentAnchor = getAnchor(stack);
        if (currentAnchor == null) {
            // 设置新锚点
            RayTraceResult lookingAt = VectorTools.getLookingAt(player, stack);
            if (lookingAt == null) {
                return;
            }
            currentAnchor = lookingAt.getBlockPos();
            setAnchor(stack, currentAnchor);
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("message.gadget.anchorrender").getUnformattedComponentText()), true);
        } else {
            // 移除锚点
            setAnchor(stack, null);
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("message.gadget.anchorremove").getUnformattedComponentText()), true);
        }
    }

    /**
     * 撤销最后一次构建
     */
    public void undoBuild(EntityPlayer player, ItemStack heldItem) {
        NBTTagCompound tagCompound = WorldSave.getWorldSave(player.world).getCompoundFromUUID(ModItems.gadgetCopyPaste.getUUID(heldItem));
        World world = player.world;
        if (world.isRemote) {
            return;
        }

        BlockPos startPos = getLastBuild(heldItem);
        if (startPos == null)
            return;

        Integer dimension = getLastBuildDim(heldItem);
        // 创建带精准采集的工具以获取正确的掉落物
        ItemStack silkTool = heldItem.copy();
        silkTool.addEnchantment(Enchantments.SILK_TOUCH, 1);

        List<BlockMap> blockMapList = getBlockMapList(tagCompound, startPos);
        boolean success = true;

        // 撤销每个方块的放置
        for (BlockMap blockMap : blockMapList) {
            double distance = blockMap.pos.getDistance(player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ());
            boolean sameDim = (player.dimension == dimension);
            IBlockState currentBlock = world.getBlockState(blockMap.pos);

            boolean cancelled = !GadgetGeneric.EmitEvent.breakBlock(world, blockMap.pos, currentBlock, player);

            // 检查距离和维度，然后拆除方块
            if (distance < 256 && !cancelled && sameDim) {
                if (currentBlock.getBlock() == blockMap.state.getBlock() || currentBlock.getBlock() instanceof ConstructionBlock) {
                    if (currentBlock.getBlockHardness(world, blockMap.pos) >= 0) {
                        if( !player.capabilities.isCreativeMode )
                            currentBlock.getBlock().harvestBlock(world, player, blockMap.pos, currentBlock, world.getTileEntity(blockMap.pos), silkTool);
                        world.spawnEntity(new BlockBuildEntity(world, blockMap.pos, player, currentBlock, 2, currentBlock, false));
                    }
                }
            } else {
                player.sendStatusMessage(new TextComponentString(TextFormatting.RED + new TextComponentTranslation("message.gadget.undofailed").getUnformattedComponentText()), true);
                success = false;
            }
        }

        if (success) setLastBuild(heldItem, null, 0);
    }

    /**
     * 获取玩家手中的复制粘贴工具
     */
    public static ItemStack getGadget(EntityPlayer player) {
        ItemStack stack = GadgetGeneric.getGadget(player);
        if (!(stack.getItem() instanceof GadgetCopyPaste))
            return ItemStack.EMPTY;

        return stack;
    }
}