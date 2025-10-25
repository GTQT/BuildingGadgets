package com.direwolf20.buildinggadgets.common.items.gadgets;

import com.direwolf20.buildinggadgets.client.gui.GuiProxy;
import com.direwolf20.buildinggadgets.BuildingGadgets;
import com.direwolf20.buildinggadgets.common.blocks.ConstructionBlockTileEntity;
import com.direwolf20.buildinggadgets.common.blocks.ModBlocks;
import com.direwolf20.buildinggadgets.common.building.Region;
import com.direwolf20.buildinggadgets.common.building.placement.ConnectedSurface;
import com.direwolf20.buildinggadgets.common.config.SyncedConfig;
import com.direwolf20.buildinggadgets.common.entities.BlockBuildEntity;
import com.direwolf20.buildinggadgets.common.tools.BlockPosState;
import com.direwolf20.buildinggadgets.common.tools.GadgetUtils;
import com.direwolf20.buildinggadgets.common.tools.VectorTools;
import com.direwolf20.buildinggadgets.common.tools.WorldSave;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 破坏工具类
 * 允许玩家快速破坏特定区域的方块
 */
public class GadgetDestruction extends GadgetGeneric {

    /**
     * 构造函数
     */
    public GadgetDestruction() {
        super("destructiontool");
        setMaxDamage(SyncedConfig.durabilityDestruction);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        // 如果使用FE能量系统，耐久度为0，否则使用配置的耐久度
        return SyncedConfig.poweredByFE ? 0 : SyncedConfig.durabilityDestruction;
    }

    @Override
    public int getEnergyMax() {
        return SyncedConfig.energyMaxDestruction;
    }

    @Override
    public int getEnergyCost(ItemStack tool) {
        // 能量消耗乘以成本倍数
        return SyncedConfig.energyCostDestruction * getCostMultiplier(tool);
    }

    @Override
    public int getDamageCost(ItemStack tool) {
        // 耐久消耗乘以成本倍数
        return SyncedConfig.damageCostDestruction * getCostMultiplier(tool);
    }

    /**
     * 获取成本倍数（非模糊模式消耗更多）
     */
    private int getCostMultiplier(ItemStack tool) {
        return (int) (SyncedConfig.nonFuzzyEnabledDestruction && !getFuzzy(tool) ? SyncedConfig.nonFuzzyMultiplierDestruction : 1);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> list, ITooltipFlag b) {
        super.addInformation(stack, world, list, b);
        // 添加破坏警告
        list.add(TextFormatting.RED + I18n.format("tooltip.gadget.destroywarning"));
        // 显示覆盖层设置
        list.add(TextFormatting.AQUA + I18n.format("tooltip.gadget.destroyshowoverlay") + ": " + getOverlay(stack));
        // 显示连接区域设置
        list.add(TextFormatting.YELLOW + I18n.format("tooltip.gadget.connected_area") + ": " + getConnectedArea(stack));
        // 如果启用非模糊模式，显示模糊设置
        if (SyncedConfig.nonFuzzyEnabledDestruction)
            list.add(TextFormatting.GOLD + I18n.format("tooltip.gadget.fuzzy") + ": " + getFuzzy(stack));

        addInformationRayTraceFluid(list, stack);
        addEnergyInformation(list,stack);
    }

    /**
     * 获取工具的唯一标识符
     */
    @Nullable
    public static String getUUID(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }
        String uuid = tagCompound.getString("UUID");
        if (uuid.isEmpty()) {
            UUID uid = UUID.randomUUID();
            tagCompound.setString("UUID", uid.toString());
            stack.setTagCompound(tagCompound);
            uuid = uid.toString();
        }
        return uuid;
    }

    /**
     * 设置锚点位置
     */
    public static void setAnchor(ItemStack stack, BlockPos pos) {
        GadgetUtils.writePOSToNBT(stack, pos, "anchor");
    }

    /**
     * 获取锚点位置
     */
    public static BlockPos getAnchor(ItemStack stack) {
        return GadgetUtils.getPOSFromNBT(stack, "anchor");
    }

    /**
     * 设置锚点朝向
     */
    public static void setAnchorSide(ItemStack stack, EnumFacing side) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }
        if (side == null) {
            if (tagCompound.getTag("anchorSide") != null) {
                tagCompound.removeTag("anchorSide");
                stack.setTagCompound(tagCompound);
            }
            return;
        }
        tagCompound.setString("anchorSide", side.getName());
        stack.setTagCompound(tagCompound);
    }

    /**
     * 获取锚点朝向
     */
    public static EnumFacing getAnchorSide(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            return null;
        }
        String facing = tagCompound.getString("anchorSide");
        if (facing.isEmpty()) return null;
        return EnumFacing.byName(facing);
    }

    /**
     * 设置工具数值（如深度、左右范围等）
     */
    public static void setToolValue(ItemStack stack, int value, String valueName) {
        // 将工具的数值以整数形式存储在NBT中
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }
        tagCompound.setInteger(valueName, value);
        stack.setTagCompound(tagCompound);
    }

    /**
     * 获取工具数值
     */
    public static int getToolValue(ItemStack stack, String valueName) {
        // 从NBT中获取工具的数值
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }
        return tagCompound.getInteger(valueName);
    }

    /**
     * 获取是否显示覆盖层
     */
    public static boolean getOverlay(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
            tagCompound.setBoolean("overlay", true);
            tagCompound.setBoolean("fuzzy", true);
            stack.setTagCompound(tagCompound);
            return true;
        }
        if (tagCompound.hasKey("overlay")) {
            return tagCompound.getBoolean("overlay");
        }
        tagCompound.setBoolean("overlay", true);
        stack.setTagCompound(tagCompound);
        return true;
    }

    /**
     * 设置是否显示覆盖层
     */
    private static void setOverlay(ItemStack stack, boolean showOverlay) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }
        tagCompound.setBoolean("overlay", showOverlay);
        stack.setTagCompound(tagCompound);
    }

    /**
     * 切换覆盖层显示
     */
    public void switchOverlay(EntityPlayer player, ItemStack stack) {
        boolean overlay = !getOverlay(stack);
        setOverlay(stack, overlay);
        player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("tooltip.gadget.destroyshowoverlay").getUnformattedComponentText() + ": " + overlay), true);
    }

    /**
     * 分配方向（用于确定破坏区域的各个面）
     */
    private static List<EnumFacing> assignDirections(EnumFacing side, EntityPlayer player) {
        List<EnumFacing> dirs = new ArrayList<>();
        EnumFacing depth = side.getOpposite(); // 深度方向
        boolean vertical = side.getAxis() == Axis.Y; // 是否垂直面
        EnumFacing up = vertical ? player.getHorizontalFacing() : EnumFacing.UP; // 上方方向
        EnumFacing left = vertical ? up.rotateY() : side.rotateYCCW(); // 左方方向
        EnumFacing right = left.getOpposite(); // 右方方向
        if (side == EnumFacing.DOWN)
            up = up.getOpposite();

        EnumFacing down = up.getOpposite(); // 下方方向
        dirs.add(left);
        dirs.add(right);
        dirs.add(up);
        dirs.add(down);
        dirs.add(depth);
        return dirs;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        if (!world.isRemote) {
            // 服务器端逻辑
            if (!player.isSneaking()) {
                RayTraceResult lookingAt = VectorTools.getLookingAt(player, stack);
                // 如果没有看向任何东西且没有锚点，退出
                if (lookingAt == null && getAnchor(stack) == null) {
                    return new ActionResult<>(EnumActionResult.FAIL, stack);
                }
                BlockPos startBlock = (getAnchor(stack) == null) ? lookingAt.getBlockPos() : getAnchor(stack);
                EnumFacing sideHit = (getAnchorSide(stack) == null) ? lookingAt.sideHit : getAnchorSide(stack);
                // 清除区域
                clearArea(world, startBlock, sideHit, player, stack);
                // 如果有锚点，清除锚点
                if (getAnchor(stack) != null) {
                    setAnchor(stack, null);
                    setAnchorSide(stack, null);
                    player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("message.gadget.anchorremove").getUnformattedComponentText()), true);
                }
            }
        } else {
            // 客户端逻辑
            if (player.isSneaking()) {
                // 潜行时打开GUI
                player.openGui(BuildingGadgets.instance, GuiProxy.DestructionID, world, hand.ordinal(), 0, 0);
                return new ActionResult<>(EnumActionResult.SUCCESS, stack);
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
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
            setAnchorSide(stack, lookingAt.sideHit);
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("message.gadget.anchorrender").getUnformattedComponentText()), true);
        } else {
            // 移除锚点
            setAnchor(stack, null);
            setAnchorSide(stack, null);
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("message.gadget.anchorremove").getUnformattedComponentText()), true);
        }
    }

    /**
     * 获取破坏区域
     */
    public static Set<BlockPos> getArea(World world, BlockPos pos, EnumFacing incomingSide, EntityPlayer player, ItemStack stack) {
        int depth = getToolValue(stack, "depth");
        if (depth == 0)
            return Collections.emptySet();

        BlockPos startPos = (getAnchor(stack) == null) ? pos : getAnchor(stack);
        EnumFacing side = (getAnchorSide(stack) == null) ? incomingSide : getAnchorSide(stack);

        // 构建区域
        List<EnumFacing> directions = assignDirections(side, player);
        String[] directionNames = new String[] {"right", "left", "up", "down", "depth"};
        Region selectionRegion = new Region(startPos);
        for (int i = 0; i < directionNames.length; i++)
            selectionRegion = selectionRegion.union(new Region(startPos.offset(directions.get(i), getToolValue(stack, directionNames[i]) - (i == 4 ? 1 : 0))));

        // 检查是否为模糊模式
        boolean fuzzy = ! SyncedConfig.nonFuzzyEnabledDestruction || GadgetGeneric.getFuzzy(stack);
        IBlockState stateTarget = fuzzy ? null : world.getBlockState(pos);

        // 根据是否为连接区域模式返回不同的结果
        if (GadgetGeneric.getConnectedArea(stack)) {
            return ConnectedSurface.create(
                    world,
                    selectionRegion,
                    searchPos -> searchPos,
                    startPos,
                    null,
                    (s, p) -> validBlock(world, p, player, s, fuzzy)
            ).stream().collect(Collectors.toSet());

        } else {
            return selectionRegion.stream().filter(
                    e -> validBlock(world, e, player, stateTarget, fuzzy)
            ).collect(Collectors.toSet());
        }
    }

    /**
     * 检查方块是否可以被破坏
     */
    private static boolean validBlock(World world, BlockPos voidPos, EntityPlayer player, @Nullable IBlockState stateTarget, boolean fuzzy) {
        IBlockState currentBlock = world.getBlockState(voidPos);
        // 非模糊模式下检查方块类型是否匹配
        if (! fuzzy && currentBlock != stateTarget) return false;
        TileEntity te = world.getTileEntity(voidPos);
        // 跳过空气方块
        if (currentBlock.getBlock().isAir(currentBlock, world, voidPos)) return false;
        // 跳过效果方块
        if (currentBlock.equals(ModBlocks.effectBlock.getDefaultState())) return false;
        // 跳过非建筑方块TileEntity
        if ((te != null) && !(te instanceof ConstructionBlockTileEntity)) return false;
        // 跳过不可破坏的方块
        if (currentBlock.getBlockHardness(world, voidPos) < 0) return false;

        ItemStack tool = getGadget(player);
        if (tool.isEmpty()) return false;

        if (!player.isAllowEdit()) {
            return false;
        }

        return world.isBlockModifiable(player, voidPos);
    }

    /**
     * 清除区域内的方块
     */
    private void clearArea(World world, BlockPos pos, EnumFacing side, EntityPlayer player, ItemStack stack) {
        Set<BlockPos> voidPosArray = getArea(world, pos, side, player, stack);
        List<BlockPosState> blockList = new ArrayList<>();

        for (BlockPos voidPos : voidPosArray) {
            boolean isPaste;

            IBlockState blockState = world.getBlockState(voidPos);
            IBlockState pasteState = Blocks.AIR.getDefaultState();
            // 跳过空气方块
            if( blockState == Blocks.AIR.getDefaultState() )
                continue;

            // 检查是否为建筑浆糊方块
            if (blockState.getBlock() == ModBlocks.constructionBlock) {
                TileEntity te = world.getTileEntity(voidPos);
                if (te instanceof ConstructionBlockTileEntity)
                    pasteState = ((ConstructionBlockTileEntity) te).getActualBlockState();
            }

            isPaste = pasteState != Blocks.AIR.getDefaultState() && pasteState != null;
            // 破坏方块
            if (!destroyBlock(world, voidPos, player))
                continue;

            // 记录方块位置和状态用于撤销
            blockList.add(new BlockPosState(voidPos, isPaste ? pasteState : blockState, isPaste));
        }

        // 如果有方块被破坏，存储撤销信息
        if (blockList.size() > 0)
            storeUndo(world, blockList, stack, player);
    }

    /**
     * 存储撤销信息
     */
    private static void storeUndo(World world, List<BlockPosState> blockList, ItemStack stack, EntityPlayer player) {
        WorldSave worldSave = WorldSave.getWorldSaveDestruction(world);

        NBTTagCompound tagCompound = new NBTTagCompound();
        NBTTagList list = new NBTTagList();

        // 将每个方块状态添加到列表中
        blockList.forEach( e -> list.appendTag( e.toCompound() ) );

        tagCompound.setTag("mapping", list);
        tagCompound.setInteger("dimension", player.dimension);

        // 保存到世界数据
        worldSave.addToMap(getUUID(stack), tagCompound);
        worldSave.markForSaving();
    }

    /**
     * 撤销破坏操作
     */
    public void undo(EntityPlayer player, ItemStack stack) {
        World world = player.world;
        WorldSave worldSave = WorldSave.getWorldSaveDestruction(world);

        // 获取保存的撤销数据
        NBTTagCompound saveCompound = worldSave.getCompoundFromUUID(getUUID(stack));
        if (saveCompound == null)
            return;

        // 检查维度是否匹配
        int dimension = saveCompound.getInteger("dimension");
        if( dimension != player.dimension )
            return;

        NBTTagList list = saveCompound.getTagList("mapping", Constants.NBT.TAG_COMPOUND);
        if( list.tagCount() == 0 )
            return;

        boolean success = false;
        for( int i = 0; i < list.tagCount(); i ++ ) {
            NBTTagCompound compound = list.getCompoundTagAt( i );
            BlockPosState posState = BlockPosState.fromCompound(compound);

            if (posState == null)
                return;

            // 检查目标位置是否为空（没有方块）
            IBlockState state = world.getBlockState(posState.getPos());
            if (!state.getBlock().isAir(state, world, posState.getPos()) && !state.getMaterial().isLiquid())
                return;

            // 检查方块放置事件
            BlockSnapshot blockSnapshot = BlockSnapshot.getBlockSnapshot(world, posState.getPos());
            if( !GadgetGeneric.EmitEvent.placeBlock(player, blockSnapshot, EnumFacing.UP, EnumHand.MAIN_HAND) )
                continue;

            // 生成方块建造实体来恢复方块
            world.spawnEntity(new BlockBuildEntity(world, posState.getPos(), player, posState.getState(), 1, posState.getState(), posState.isPaste()));
            success = true;
        }

        // 如果撤销成功，清除保存的数据
        if (success) {
            NBTTagCompound newTag = new NBTTagCompound();
            worldSave.addToMap(getUUID(stack), newTag);
            worldSave.markForSaving();
        }
    }

    /**
     * 破坏单个方块
     */
    private boolean destroyBlock(World world, BlockPos voidPos, EntityPlayer player) {
        ItemStack tool = getGadget(player);
        if (tool.isEmpty())
            return false;

        // 检查工具是否可用
        if( !this.canUse(tool, player) )
            return false;

        // 检查方块破坏事件
        if( !GadgetGeneric.EmitEvent.breakBlock(world, voidPos, world.getBlockState(voidPos), player) )
            return false;

        // 应用耐久/能量消耗
        this.applyDamage(tool, player);

        // 生成方块建造实体来移除方块
        world.spawnEntity(new BlockBuildEntity(world, voidPos, player, world.getBlockState(voidPos), 2, Blocks.AIR.getDefaultState(), false));
        return true;
    }

    /**
     * 获取玩家手中的破坏工具
     */
    public static ItemStack getGadget(EntityPlayer player) {
        ItemStack stack = GadgetGeneric.getGadget(player);
        if (!(stack.getItem() instanceof GadgetDestruction))
            return ItemStack.EMPTY;

        return stack;
    }
}