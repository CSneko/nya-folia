package net.minecraft.network.syncher;

import com.mojang.logging.LogUtils;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;

public class SynchedEntityData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object2IntMap<Class<? extends Entity>> ENTITY_ID_POOL = new Object2IntOpenHashMap();
    private static final int MAX_ID_VALUE = 254;
    private final Entity entity;
    private final Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById = new Int2ObjectOpenHashMap();
    // private final ReadWriteLock lock = new ReentrantReadWriteLock(); // Spigot - not required
    private boolean isDirty;
    // Paper start - array backed synched entity data
    private static final int DEFAULT_ENTRY_COUNT = 10;
    private static final int GROW_FACTOR = 8;
    private SynchedEntityData.DataItem<?>[] itemsArray = new SynchedEntityData.DataItem<?>[DEFAULT_ENTRY_COUNT];
    // Paper end

    public SynchedEntityData(Entity trackedEntity) {
        this.entity = trackedEntity;
    }

    public static <T> EntityDataAccessor<T> defineId(Class<? extends Entity> entityClass, EntityDataSerializer<T> dataHandler) {
        if (SynchedEntityData.LOGGER.isDebugEnabled()) {
            try {
                Class<?> oclass1 = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());

                if (!oclass1.equals(entityClass)) {
                    SynchedEntityData.LOGGER.debug("defineId called for: {} from {}", new Object[]{entityClass, oclass1, new RuntimeException()});
                }
            } catch (ClassNotFoundException classnotfoundexception) {
                ;
            }
        }

        int i;

        if (SynchedEntityData.ENTITY_ID_POOL.containsKey(entityClass)) {
            i = SynchedEntityData.ENTITY_ID_POOL.getInt(entityClass) + 1;
        } else {
            int j = 0;
            Class oclass2 = entityClass;

            while (oclass2 != Entity.class) {
                oclass2 = oclass2.getSuperclass();
                if (SynchedEntityData.ENTITY_ID_POOL.containsKey(oclass2)) {
                    j = SynchedEntityData.ENTITY_ID_POOL.getInt(oclass2) + 1;
                    break;
                }
            }

            i = j;
        }

        if (i > 254) {
            throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is 254)");
        } else {
            SynchedEntityData.ENTITY_ID_POOL.put(entityClass, i);
            return dataHandler.createAccessor(i);
        }
    }

    public boolean registrationLocked; // Spigot
    public <T> void define(EntityDataAccessor<T> key, T initialValue) {
        if (this.registrationLocked) throw new IllegalStateException("Registering datawatcher object after entity initialization"); // Spigot
        int i = key.getId();

        if (i > 254) {
            throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is 254)");
        } else if (this.itemsById.containsKey(i)) {
            throw new IllegalArgumentException("Duplicate id value for " + i + "!");
        } else if (EntityDataSerializers.getSerializedId(key.getSerializer()) < 0) {
            EntityDataSerializer datawatcherserializer = key.getSerializer();

            throw new IllegalArgumentException("Unregistered serializer " + datawatcherserializer + " for " + i + "!");
        } else {
            this.createDataItem(key, initialValue);
        }
    }

    private <T> void createDataItem(EntityDataAccessor<T> key, T value) {
        SynchedEntityData.DataItem<T> datawatcher_item = new SynchedEntityData.DataItem<>(key, value);

        // this.lock.writeLock().lock(); // Spigot - not required
        this.itemsById.put(key.getId(), datawatcher_item);
        // this.lock.writeLock().unlock(); // Spigot - not required
        // Paper start - array backed synched entity data
        if (this.itemsArray.length <= key.getId()) {
            final int newSize = Math.min(key.getId() + GROW_FACTOR, MAX_ID_VALUE);

            this.itemsArray = java.util.Arrays.copyOf(this.itemsArray, newSize);
        }

        this.itemsArray[key.getId()] = datawatcher_item;
        // Paper end
    }

    public <T> boolean hasItem(EntityDataAccessor<T> key) {
        return this.itemsById.containsKey(key.getId());
    }

    private <T> SynchedEntityData.DataItem<T> getItem(EntityDataAccessor<T> key) {
        // Spigot start
        /*
        this.lock.readLock().lock();

        DataWatcher.Item datawatcher_item;

        try {
            datawatcher_item = (DataWatcher.Item) this.itemsById.get(datawatcherobject.getId());
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting synched entity data");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Synched entity data");

            crashreportsystemdetails.setDetail("Data ID", (Object) datawatcherobject);
            throw new ReportedException(crashreport);
        } finally {
            this.lock.readLock().unlock();
        }

        return datawatcher_item;
        */
        // Paper start - array backed synched entity data
        final int id = key.getId();

        if (id < 0 || id >= this.itemsArray.length) {
            return null;
        }

        return (DataItem<T>) this.itemsArray[id];
        // Paper end
        // Spigot end
    }

    public <T> T get(EntityDataAccessor<T> data) {
        return this.getItem(data).getValue();
    }

    public <T> void set(EntityDataAccessor<T> key, T value) {
        this.set(key, value, false);
    }

    public <T> void set(EntityDataAccessor<T> key, T value, boolean force) {
        SynchedEntityData.DataItem<T> datawatcher_item = this.getItem(key);

        if (force || ObjectUtils.notEqual(value, datawatcher_item.getValue())) {
            datawatcher_item.setValue(value);
            this.entity.onSyncedDataUpdated(key);
            datawatcher_item.setDirty(true);
            this.isDirty = true;
        }

    }

    // CraftBukkit start - add method from above
    public <T> void markDirty(EntityDataAccessor<T> datawatcherobject) {
        this.getItem(datawatcherobject).setDirty(true);
        this.isDirty = true;
    }
    // CraftBukkit end

    public boolean isDirty() {
        return this.isDirty;
    }

    @Nullable
    public List<SynchedEntityData.DataValue<?>> packDirty() {
        List<SynchedEntityData.DataValue<?>> list = null;

        if (this.isDirty) {
            // this.lock.readLock().lock(); // Spigot - not required
            ObjectIterator objectiterator = this.itemsById.values().iterator();

            while (objectiterator.hasNext()) {
                SynchedEntityData.DataItem<?> datawatcher_item = (SynchedEntityData.DataItem) objectiterator.next();

                if (datawatcher_item.isDirty()) {
                    datawatcher_item.setDirty(false);
                    if (list == null) {
                        list = new ArrayList();
                    }

                    list.add(datawatcher_item.value());
                }
            }

            // this.lock.readLock().unlock(); // Spigot - not required
        }

        this.isDirty = false;
        return list;
    }

    @Nullable
    public List<SynchedEntityData.DataValue<?>> getNonDefaultValues() {
        List<SynchedEntityData.DataValue<?>> list = null;

        // this.lock.readLock().lock(); // Spigot - not required
        ObjectIterator objectiterator = this.itemsById.values().iterator();

        while (objectiterator.hasNext()) {
            SynchedEntityData.DataItem<?> datawatcher_item = (SynchedEntityData.DataItem) objectiterator.next();

            if (!datawatcher_item.isSetToDefault()) {
                if (list == null) {
                    list = new ArrayList();
                }

                list.add(datawatcher_item.value());
            }
        }

        // this.lock.readLock().unlock(); // Spigot - not required
        return list;
    }

    public void assignValues(List<SynchedEntityData.DataValue<?>> entries) {
        // this.lock.writeLock().lock(); // Spigot - not required

        try {
            Iterator iterator = entries.iterator();

            while (iterator.hasNext()) {
                SynchedEntityData.DataValue<?> datawatcher_b = (SynchedEntityData.DataValue) iterator.next();
                SynchedEntityData.DataItem<?> datawatcher_item = (SynchedEntityData.DataItem) this.itemsById.get(datawatcher_b.id);

                if (datawatcher_item != null) {
                    this.assignValue(datawatcher_item, datawatcher_b);
                    this.entity.onSyncedDataUpdated(datawatcher_item.getAccessor());
                }
            }
        } finally {
            // this.lock.writeLock().unlock(); // Spigot - not required
        }

        this.entity.onSyncedDataUpdated(entries);
    }

    private <T> void assignValue(SynchedEntityData.DataItem<T> to, SynchedEntityData.DataValue<?> from) {
        if (!Objects.equals(from.serializer(), to.accessor.getSerializer())) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Invalid entity data item type for field %d on entity %s: old=%s(%s), new=%s(%s)", to.accessor.getId(), this.entity, to.value, to.value.getClass(), from.value, from.value.getClass()));
        } else {
            to.setValue((T) from.value); // CraftBukkit - decompile error
        }
    }

    public boolean isEmpty() {
        return this.itemsById.isEmpty();
    }

    // CraftBukkit start
    public void refresh(ServerPlayer to) {
        if (!this.isEmpty()) {
            List<SynchedEntityData.DataValue<?>> list = this.packAll(); // Paper - Update EVERYTHING not just not default

            if (list != null) {
                if (to.getBukkitEntity().canSee(this.entity.getBukkitEntity())) { // Paper
                to.connection.send(new ClientboundSetEntityDataPacket(this.entity.getId(), list));
                } // Paper
            }
        }
    }
    // CraftBukkit end
    // Paper start
    // We need to pack all as we cannot rely on "non default values" or "dirty" ones.
    // Because these values can possibly be desynced on the client.
    @Nullable
    private List<SynchedEntityData.DataValue<?>> packAll() {
        if (this.isEmpty()) {
            return null;
        }

        List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();
        for (DataItem<?> dataItem : this.itemsById.values()) {
            list.add(dataItem.value());
        }

        return list;
    }

    // This method should only be used if the data of an entity could have became desynced
    // due to interactions on the client.
    public void resendPossiblyDesyncedEntity(ServerPlayer player) {
        if (this.entity.tracker == null) {
            return;
        }

        if (player.getBukkitEntity().canSee(entity.getBukkitEntity())) {
            net.minecraft.server.level.ServerEntity serverEntity = this.entity.tracker.serverEntity;

            List<net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>> list = new ArrayList<>();
            serverEntity.sendPairingData(player, list::add);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(list));
        }
    }

    // This method allows you to specifically resend certain data accessor keys to the client
    public void resendPossiblyDesyncedDataValues(List<EntityDataAccessor<?>> keys, ServerPlayer to) {
        if (!to.getBukkitEntity().canSee(this.entity.getBukkitEntity())) {
            return;
        }
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(keys.size());
        for (EntityDataAccessor<?> key : keys) {
            SynchedEntityData.DataItem<?> synchedValue = this.getItem(key);
            values.add(synchedValue.value());
        }

        to.connection.send(new ClientboundSetEntityDataPacket(this.entity.getId(), values));
    }
    // Paper end

    public static class DataItem<T> {

        final EntityDataAccessor<T> accessor;
        T value;
        private final T initialValue;
        private boolean dirty;

        public DataItem(EntityDataAccessor<T> data, T value) {
            this.accessor = data;
            this.initialValue = value;
            this.value = value;
        }

        public EntityDataAccessor<T> getAccessor() {
            return this.accessor;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public T getValue() {
            return this.value;
        }

        public boolean isDirty() {
            return this.dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean isSetToDefault() {
            return this.initialValue.equals(this.value);
        }

        public SynchedEntityData.DataValue<T> value() {
            return SynchedEntityData.DataValue.create(this.accessor, this.value);
        }
    }

    public static record DataValue<T>(int id, EntityDataSerializer<T> serializer, T value) { // CraftBukkit - decompile error

        public static <T> SynchedEntityData.DataValue<T> create(EntityDataAccessor<T> data, T value) {
            EntityDataSerializer<T> datawatcherserializer = data.getSerializer();

            return new SynchedEntityData.DataValue<>(data.getId(), datawatcherserializer, datawatcherserializer.copy(value));
        }

        public void write(FriendlyByteBuf buf) {
            int i = EntityDataSerializers.getSerializedId(this.serializer);

            if (i < 0) {
                throw new EncoderException("Unknown serializer type " + this.serializer);
            } else {
                buf.writeByte(this.id);
                buf.writeVarInt(i);
                this.serializer.write(buf, this.value);
            }
        }

        public static SynchedEntityData.DataValue<?> read(FriendlyByteBuf buf, int id) {
            int j = buf.readVarInt();
            EntityDataSerializer<?> datawatcherserializer = EntityDataSerializers.getSerializer(j);

            if (datawatcherserializer == null) {
                throw new DecoderException("Unknown serializer type " + j);
            } else {
                return read(buf, id, datawatcherserializer);
            }
        }

        private static <T> SynchedEntityData.DataValue<T> read(FriendlyByteBuf buf, int id, EntityDataSerializer<T> handler) {
            return new SynchedEntityData.DataValue<>(id, handler, handler.read(buf));
        }
    }
}
