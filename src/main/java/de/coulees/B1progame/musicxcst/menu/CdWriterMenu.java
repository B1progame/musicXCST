package de.coulees.B1progame.musicxcst.menu;

import de.coulees.B1progame.musicxcst.init.ModBlocks;
import de.coulees.B1progame.musicxcst.init.ModItems;
import de.coulees.B1progame.musicxcst.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CdWriterMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    private static final int PLAYER_INVENTORY_START = 2;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Container cdSlots;
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private boolean converting;

    public CdWriterMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, new SimpleContainer(2), ContainerLevelAccess.NULL, pos);
    }

    public CdWriterMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos pos) {
        this(containerId, playerInventory, new SimpleContainer(2), access, pos);
    }

    private CdWriterMenu(int containerId, Inventory playerInventory, Container cdSlots, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenuTypes.CD_WRITER, containerId);
        checkContainerSize(cdSlots, 2);
        this.cdSlots = cdSlots;
        this.access = access;
        this.pos = pos.immutable();

        addSlot(new Slot(cdSlots, INPUT_SLOT, 200, 11) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !converting && stack.getItem() == ModItems.BLUEPRINT_CD;
            }

            @Override
            public boolean mayPickup(Player player) {
                return !converting;
            }

            @Override
            public boolean isActive() {
                return !converting;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        addSlot(new Slot(cdSlots, OUTPUT_SLOT, 243, 57) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return !converting && hasItem();
            }

            @Override
            public boolean isActive() {
                return !converting || hasItem();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + column, 108 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 108 + column * 18, 142));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    public ItemStack inputStack() {
        return cdSlots.getItem(INPUT_SLOT);
    }

    public boolean hasOutput() {
        return !cdSlots.getItem(OUTPUT_SLOT).isEmpty();
    }

    public boolean isConverting() {
        return converting;
    }

    public void setConverting(boolean converting) {
        this.converting = converting;
        broadcastChanges();
    }

    public void moveInputToOutput() {
        ItemStack converted = cdSlots.removeItemNoUpdate(INPUT_SLOT);
        cdSlots.setItem(OUTPUT_SLOT, converted);
        cdSlots.setChanged();
        broadcastChanges();
    }

    public void inputChanged() {
        cdSlots.setChanged();
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index == OUTPUT_SLOT) {
                if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index == INPUT_SLOT) {
                if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.getItem() == ModItems.BLUEPRINT_CD && !hasOutput() && !converting) {
                if (!moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= PLAYER_INVENTORY_START && index < PLAYER_INVENTORY_END) {
                if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= HOTBAR_START && index < HOTBAR_END && !moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CD_WRITER);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, cdSlots);
    }
}
