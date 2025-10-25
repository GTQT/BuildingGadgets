package com.direwolf20.buildinggadgets.common.items.gadgets;

import com.direwolf20.buildinggadgets.common.blocks.ModBlocks;
import com.direwolf20.buildinggadgets.common.config.SyncedConfig;
import com.direwolf20.buildinggadgets.common.entities.BlockBuildEntity;
import com.direwolf20.buildinggadgets.common.items.FakeBuilderWorld;
import com.direwolf20.buildinggadgets.common.items.ModItems;
import com.direwolf20.buildinggadgets.common.tools.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.direwolf20.buildinggadgets.common.tools.GadgetUtils.*;

/**
 * 建筑工具类
 * 允许玩家快速建造各种形状的结构
 */
public class GadgetBuilding extends GadgetGeneric {

    // 虚拟世界，用于计算方块状态和连接
    private static final FakeBuilderWorld fakeWorld = new FakeBuilderWorld();

    /**
     * 构造函数
     */
    public GadgetBuilding() {
        super("buildingtool");
        setMaxDamage(SyncedConfig.durabilityBuilder);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        // 如果使用FE能量系统，耐久度为0，否则使用配置的耐久度
        return SyncedConfig.poweredByFE ? 0 : SyncedConfig.durabilityBuilder;
    }

    @Override
    public int getEnergyCost(ItemStack tool) {
        return SyncedConfig.energyCostBuilder;
    }

    @Override
    public int getDamageCost(ItemStack tool) {
        return SyncedConfig.damageCostBuilder;
    }

    /**
     * 设置工具模式
     */
    private static void setToolMode(ItemStack tool, BuildingModes mode) {
        // 将工具模式以字符串形式存储在NBT中
        NBTTagCompound tagCompound = NBTTool.getOrNewTag(tool);
        tagCompound.setString("mode", mode.getRegistryName());
    }

    /**
     * 获取工具模式
     */
    public static BuildingModes getToolMode(ItemStack tool) {
        NBTTagCompound tagCompound = NBTTool.getOrNewTag(tool);
        return BuildingModes.byName(tagCompound.getString("mode"));
    }

    /**
     * 检查是否应该在顶部放置方块
     */
    public static boolean shouldPlaceAtop(ItemStack stack) {
        return !NBTTool.getOrNewTag(stack).getBoolean("start_inside");
    }

    /**
     * 切换放置模式（顶部 vs 内部）
     */
    public static void togglePlaceAtop(EntityPlayer player, ItemStack stack) {
        NBTTool.getOrNewTag(stack).setBoolean("start_inside", shouldPlaceAtop(stack));
        String prefix = "message.gadget.building.placement";
        player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation(prefix, new TextComponentTranslation(prefix + (shouldPlaceAtop(stack) ? ".atop" : ".inside"))).getUnformattedComponentText()), true);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> list, ITooltipFlag b) {
        // 添加工具信息到提示框
        super.addInformation(stack, world, list, b);
        // 显示当前选中的方块
        list.add(TextFormatting.DARK_GREEN + I18n.format("tooltip.gadget.block") + ": " + getToolBlock(stack).getBlock().getLocalizedName());
        BuildingModes mode = getToolMode(stack);
        // 显示当前模式和连接状态
        list.add(TextFormatting.AQUA + I18n.format("tooltip.gadget.mode") + ": " + (mode == BuildingModes.Surface && getConnectedArea(stack) ? I18n.format("tooltip.gadget.connected") + " " : "") + mode);
        // 显示范围（BuildToMe模式除外）
        if (getToolMode(stack) != BuildingModes.BuildToMe)
            list.add(TextFormatting.LIGHT_PURPLE + I18n.format("tooltip.gadget.range") + ": " + getToolRange(stack));

        // 表面模式的模糊设置
        if (getToolMode(stack) == BuildingModes.Surface)
            list.add(TextFormatting.GOLD + I18n.format("tooltip.gadget.fuzzy") + ": " + getFuzzy(stack));

        addInformationRayTraceFluid(list, stack);
        // 显示放置模式
        list.add(TextFormatting.YELLOW + I18n.format("tooltip.gadget.building.place_atop") + ": " + shouldPlaceAtop(stack));
        addEnergyInformation(list, stack);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        // 当物品使用时，如果潜行则选择点击的方块，否则进行建造 - 这是在非方块上右键使用工具时调用的
        ItemStack itemstack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        if (!world.isRemote) {
            // 服务器端逻辑
            if (player.isSneaking()) {
                selectBlock(itemstack, player); // 潜行时选择方块
            } else {
                build(player, itemstack); // 非潜行时进行建造
            }
        } else if (!player.isSneaking()) {
            // 客户端逻辑：更新库存缓存
            ToolRenders.updateInventoryCache();
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, itemstack);
    }

    /**
     * 设置工具模式（通过径向菜单）
     */
    public void setMode(ItemStack heldItem, int modeInt) {
        BuildingModes mode = BuildingModes.values()[modeInt];
        setToolMode(heldItem, mode);
    }

    /**
     * 改变工具范围
     */
    public void rangeChange(EntityPlayer player, ItemStack heldItem) {
        // 当范围改变热键按下时调用
        int range = getToolRange(heldItem);
        int changeAmount = (getToolMode(heldItem) != BuildingModes.Surface || (range % 2 == 0)) ? 1 : 2;

        // 根据是否潜行决定增加或减少范围
        if (player.isSneaking())
            range = (range == 1) ? SyncedConfig.maxRange : range - changeAmount;
        else
            range = (range >= SyncedConfig.maxRange) ? 1 : range + changeAmount;

        setToolRange(heldItem, range);
        player.sendStatusMessage(new TextComponentString(TextFormatting.DARK_AQUA + new TextComponentTranslation("message.gadget.toolrange").getUnformattedComponentText() + ": " + range), true);
    }

    /**
     * 执行建造操作
     */
    private boolean build(EntityPlayer player, ItemStack stack) {
        // 建造视觉渲染中显示的方块
        World world = player.world;
        List<BlockPos> coords = getAnchor(stack);

        if (coords.size() == 0) {
            // 如果没有锚点，在当前位置建造
            RayTraceResult lookingAt = VectorTools.getLookingAt(player, stack);
            if (lookingAt == null) {
                // 如果没有看向任何东西，退出
                return false;
            }
            BlockPos startBlock = lookingAt.getBlockPos();
            EnumFacing sideHit = lookingAt.sideHit;
            // 收集放置位置
            coords = BuildingModes.collectPlacementPos(world, player, startBlock, sideHit, stack, startBlock);
        } else {
            // 如果有锚点，清除它（即使建造失败）
            setAnchor(stack, new ArrayList<BlockPos>());
        }

        List<BlockPos> undoCoords = new ArrayList<BlockPos>();
        Set<BlockPos> coordinates = new HashSet<BlockPos>(coords);

        ItemStack heldItem = getGadget(player);
        if (heldItem.isEmpty())
            return false;

        IBlockState blockState = getToolBlock(heldItem);

        // 如果没有选择方块则不尝试建造 - 通常只在新工具上发生
        if (blockState != Blocks.AIR.getDefaultState()) {
            IBlockState state = Blocks.AIR.getDefaultState(); // 为虚拟世界初始化新的状态变量
            // 初始化虚拟世界的方块
            fakeWorld.setWorldAndState(player.world, blockState, coordinates);
            for (BlockPos coordinate : coords) {
                if (fakeWorld.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
                    try {
                        // 获取虚拟世界中的方块状态（这让栅栏等可以正确连接）
                        state = blockState.getActualState(fakeWorld, coordinate);
                    } catch (Exception var8) {
                        // 忽略异常
                    }
                }

                // 在真实世界中放置方块
                if (placeBlock(world, player, coordinate, state)) {
                    undoCoords.add(coordinate); // 如果成功放置方块，将位置添加到撤销列表
                }
            }
            GadgetUtils.clearCachedRemoteInventory();

            // 如果撤销列表有任何数据，将其添加到工具的NBT中
            if (undoCoords.size() > 0) {
                UndoState undoState = new UndoState(player.dimension, undoCoords);
                pushUndoList(heldItem, undoState);
            }
        }

        // 按距离排序坐标
        Sorter.Blocks.byDistance(coords, player);
        return true;
    }

    /**
     * 撤销建造操作
     */
    public boolean undoBuild(EntityPlayer player) {
        ItemStack heldItem = getGadget(player);
        if (heldItem.isEmpty())
            return false;

        // 从工具获取撤销列表，如果为空则退出
        UndoState undoState = popUndoList(heldItem);
        if (undoState == null) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("message.gadget.nothingtoundo").getUnformattedComponentText()), true);
            return false;
        }

        World world = player.world;
        if (!world.isRemote) {
            IBlockState currentBlock = Blocks.AIR.getDefaultState();
            List<BlockPos> undoCoords = undoState.coordinates; // 获取要撤销的坐标
            int dimension = undoState.dimension; // 获取要撤销的维度
            List<BlockPos> failedRemovals = new ArrayList<BlockPos>(); // 构建失败移除的列表

            // 设置带精准采集的工具版本，以便返回石头而不是圆石等
            ItemStack silkTool = heldItem.copy();
            silkTool.addEnchantment(Enchantments.SILK_TOUCH, 1);

            for (BlockPos coord : undoCoords) {
                currentBlock = world.getBlockState(coord);
                double distance = coord.getDistance(player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ());
                boolean sameDim = (player.dimension == dimension);

                // 检查方块破坏事件是否被取消
                boolean cancelled = !GadgetGeneric.EmitEvent.breakBlock(world, coord, currentBlock, player);

                // 不允许在方块仍在放置中或距离太远时撤销
                if (distance < 64 && sameDim && currentBlock != ModBlocks.effectBlock.getDefaultState() && !cancelled) {
                    if (currentBlock != Blocks.AIR.getDefaultState()) {
                        // 非创造模式下采集方块
                        if( !player.capabilities.isCreativeMode )
                            currentBlock.getBlock().harvestBlock(world, player, coord, currentBlock, world.getTileEntity(coord), silkTool);
                        // 生成方块建造实体来移除方块
                        world.spawnEntity(new BlockBuildEntity(world, coord, player, currentBlock, 2, getToolActualBlock(heldItem), false));
                    }
                } else {
                    // 如果在错误的维度或距离太远，撤销失败
                    player.sendStatusMessage(new TextComponentString(TextFormatting.RED + new TextComponentTranslation("message.gadget.undofailed").getUnformattedComponentText()), true);
                    failedRemovals.add(coord);
                }
            }

            GadgetUtils.clearCachedRemoteInventory();

            // 将任何失败的撤销块添加到撤销堆栈中
            if (failedRemovals.size() != 0) {
                UndoState failedState = new UndoState(player.dimension, failedRemovals);
                pushUndoList(heldItem, failedState);
            }
        }
        return true;
    }

    /**
     * 放置单个方块
     */
    private boolean placeBlock(World world, EntityPlayer player, BlockPos pos, IBlockState setBlock) {
        if (!player.isAllowEdit())
            return false;

        // 检查位置是否在世界高度范围内
        if( world.isOutsideBuildHeight(pos) )
            return false;

        ItemStack heldItem = getGadget(player);
        if (heldItem.isEmpty())
            return false;

        boolean useConstructionPaste = false;

        ItemStack itemStack;
        // 如果可以丝滑采集，使用丝滑采集的掉落物
        if (setBlock.getBlock().canSilkHarvest(world, pos, setBlock, player)) {
            itemStack = InventoryManipulation.getSilkTouchDrop(setBlock);
        } else {
            itemStack = setBlock.getBlock().getPickBlock(setBlock, null, world, pos, player);
        }

        // 如果物品为空，再次尝试获取拾取方块
        if (itemStack.getItem().equals(Items.AIR)) {
            itemStack = setBlock.getBlock().getPickBlock(setBlock, null, world, pos, player);
        }

        // 计算需要的物品数量
        NonNullList<ItemStack> drops = NonNullList.create();
        setBlock.getBlock().getDrops(drops, world, pos, setBlock, 0);
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
            return false;
        }

        // 检查方块放置事件
        BlockSnapshot blockSnapshot = BlockSnapshot.getBlockSnapshot(world, pos);
        if (ForgeEventFactory.onPlayerBlockPlace(player, blockSnapshot, EnumFacing.UP, EnumHand.MAIN_HAND).isCanceled()) {
            return false;
        }

        // 检查物品是否足够，如果不够则使用建筑浆糊
        ItemStack constructionPaste = new ItemStack(ModItems.constructionPaste);
        if (InventoryManipulation.countItem(itemStack, player, world) < neededItems) {
            if (InventoryManipulation.countPaste(player) < neededItems) {
                return false;
            }
            itemStack = constructionPaste.copy();
            useConstructionPaste = true;
        }

        if (!this.canUse(heldItem, player))
            return false;

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
            world.spawnEntity(new BlockBuildEntity(world, pos, player, setBlock, 1, getToolActualBlock(heldItem), useConstructionPaste));
            return true;
        }
        return false;
    }

    /**
     * 获取玩家手中的建筑工具
     */
    public static ItemStack getGadget(EntityPlayer player) {
        ItemStack stack = GadgetGeneric.getGadget(player);
        if (!(stack.getItem() instanceof GadgetBuilding))
            return ItemStack.EMPTY;

        return stack;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 20;
    }
}