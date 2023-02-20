package io.github.jwharm.javagi.util;

import java.lang.foreign.*;

import io.github.jwharm.javagi.annotations.CustomType;
import org.gnome.gio.ListModel;
import org.gnome.glib.Type;
import org.gnome.gobject.GObject;
import org.gnome.gobject.GObjects;
import org.gnome.gobject.InterfaceInfo;

import io.github.jwharm.javagi.interop.Interop;

/**
 * An implementation of the {@link ListModel} that returns the index of
 * a list item instead of an actual item. The index can be used with a
 * {@link java.util.List}, to work with Java objects in combination with
 * a {@link ListModel}.
 */
@CustomType(name="ListIndexModel")
public class ListIndexModel extends GObject implements ListModel {

    /**
     * Construct a ListIndexModel for the provided memory address.
     * @param address the memory address of the instance in native memory
     */
    public ListIndexModel(Addressable address) {
        super(address);
    }

    /**
     * Get the {@link MemoryLayout} of the instance struct
     * @return the memory layout
     */
    public static MemoryLayout getMemoryLayout() {
        return MemoryLayout.structLayout(
                GObject.getMemoryLayout().withName("parent_instance"),
                Interop.valueLayout.C_INT.withName("size")
        ).withName("ListIndexModel");
    }

    private static Type type;

    /**
     * Get the gtype of {@link ListIndexModel}, or register it as a new gtype
     * if it was not registered yet.
     * @return the {@link Type} that has been registered for {@link ListIndexModel}
     */
    public static Type getType() {
        if (type == null) {
            type = Types.register(ListIndexModel.class);

            // Implement the ListModel interface
            InterfaceInfo interfaceInfo = InterfaceInfo.allocate();
            interfaceInfo.writeInterfaceInit((iface, data) -> {
                ListModelInterface lmi = new ListModelInterface(iface.handle());
                lmi.overrideGetItemType(ListModel::getItemType);
                lmi.overrideGetNItems(ListModel::getNItems);
                lmi.overrideGetItem(ListModel::getItem);
            });
            GObjects.typeAddInterfaceStatic(type, ListModel.getType(), interfaceInfo);
        }
        return type;
    }

    /**
     * Instantiate a new ListIndexModel with the provided size.
     * @param size the initial size of the list model
     */
    public ListIndexModel(int size) {
        super(getType(), null);
        setSize(size);
    }

    /**
     * Set the size field to the provided value, and emit the "items-changed" signal.
     * @param size the new listmodel size
     */
    public void setSize(int size) {
        int oldSize = getNItems();
        getMemoryLayout()
                .varHandle(MemoryLayout.PathElement.groupElement("size"))
                .set(MemorySegment.ofAddress((MemoryAddress) handle(), getMemoryLayout().byteSize(), MemorySession.openImplicit()), size);
        itemsChanged(0, oldSize, size);
    }

    /**
     * Returns the gtype of {@link ListIndexItem}
     * @return always returns the value of {@link ListIndexItem#getType()}
     */
    @Override
    public Type getItemType() {
        return ListIndexItem.getType();
    }

    /**
     * Returns the size of the list model
     * @return the value of the size field
     */
    @Override
    public int getNItems() {
        return (int) getMemoryLayout()
                .varHandle(MemoryLayout.PathElement.groupElement("size"))
                .get(MemorySegment.ofAddress((MemoryAddress) handle(), getMemoryLayout().byteSize(), MemorySession.openImplicit()));
    }

    /**
     * Returns a {@link ListIndexItem} with the requested position as its value
     * @param position the position of the item to fetch
     * @return a {@link ListIndexItem} with the requested position as its value
     */
    @Override
    public GObject getItem(int position) {
        if (position < 0 || position >= getNItems()) return null;
        return new ListIndexItem(position);
    }
}
