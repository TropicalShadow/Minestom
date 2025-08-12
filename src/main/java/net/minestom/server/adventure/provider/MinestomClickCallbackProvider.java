package net.minestom.server.adventure.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.minestom.server.ServerFlag;
import net.minestom.server.Tickable;
import net.minestom.server.adventure.MinestomAdventure;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class MinestomClickCallbackProvider implements ClickCallback.Provider {
    private static final Key ADVENTURE_CLICK_CALLBACK_KEY = Key.key("minestom", "click_callback");
    public static final String ID_KEY = "id";

    public static final AdventureClick ADVENTURE_CLICK_MANAGER = new AdventureClick();


    @Override
    public @NotNull ClickEvent create(@NotNull ClickCallback<Audience> callback, ClickCallback.@NotNull Options options) {
        String callbackID = String.valueOf(ADVENTURE_CLICK_MANAGER.addCallback(callback, options));
        CompoundBinaryTag tag = CompoundBinaryTag.builder().putString(ID_KEY, callbackID).build();

        return ClickEvent.custom(ADVENTURE_CLICK_CALLBACK_KEY, MinestomAdventure.wrapNbt(tag));
    }

    public static final class AdventureClick extends CallbackManager<ClickCallback<Audience>, UUID> {

        private AdventureClick() {
            super(ADVENTURE_CLICK_CALLBACK_KEY::equals);
        }

        public UUID addCallback(final @NotNull ClickCallback<Audience> callback, final ClickCallback.@NotNull Options options) {
            return this.addCallback(UUID.randomUUID(), callback, options);
        }

        @Override
        void doRunCallback(final @NotNull Audience audience, final Key key, final BinaryTag tag) {
            if (!(tag instanceof CompoundBinaryTag compoundBinaryTag)) {
                return;
            }
            String callbackID = compoundBinaryTag.getString(ID_KEY);
            if (callbackID.isEmpty()) {
                return;
            }
            UUID id;
            try {
                id = UUID.fromString(callbackID);
            } catch (IllegalArgumentException e) {
                return; // Invalid UUID format
            }

            this.tryConsumeCallback(id, callback -> callback.accept(audience));
        }
    }

    abstract static class CallbackManager<C, I> implements Tickable {

        private final Predicate<Key> locationPredicate;
        protected final Map<I, StoredCallback<C, I>> callbacks = new HashMap<>();
        private final Queue<StoredCallback<C, I>> queue = new ConcurrentLinkedQueue<>();

        protected CallbackManager(final Predicate<Key> locationPredicate) {
            this.locationPredicate = locationPredicate;
        }

        public I addCallback(final I id, final @NotNull C callback, final ClickCallback.@NotNull Options options) {
            this.queue.add(new StoredCallback<>(callback, options, id));
            return id;
        }

        public void tick(long nanoTime) {
            // Evict expired entries, every 5 seconds, 100 ticks at 20 TPS
            int ticksPerSecond = ServerFlag.SERVER_TICKS_PER_SECOND;
            int currentTick = (int) (nanoTime / (1_000_000_000L / ticksPerSecond));
            if (currentTick % (ticksPerSecond * 5) == 0) {
                this.callbacks.values().removeIf(callback -> !callback.valid());
            }

            StoredCallback<C, I> callback;
            while ((callback = this.queue.poll()) != null) {
                this.callbacks.put(callback.id(), callback);
            }
        }

        final void tryConsumeCallback(final I key, final Consumer<? super C> callbackConsumer) {
            final StoredCallback<C, I> callback = this.callbacks.get(key);
            if (callback != null && callback.valid()) {
                callback.takeUse();
                callbackConsumer.accept(callback.callback);
            }
        }

        abstract void doRunCallback(final @NotNull Audience audience, final Key key, final BinaryTag tag);

        public final void tryRunCallback(final @NotNull Audience audience, final Key key, final Optional<? extends BinaryTag> tag) {
            if (!this.locationPredicate.test(key) || tag.isEmpty()) return;
            this.doRunCallback(audience, key, tag.get());
        }
    }

    public static final class StoredCallback<C, I> {
        private final long startedAt = System.nanoTime();
        private final C callback;
        private final long lifetime;
        private final I id;
        private int remainingUses;

        private StoredCallback(final @NotNull C callback, final ClickCallback.@NotNull Options options, final I id) {
            long lifetimeValue;
            this.callback = callback;
            try {
                lifetimeValue = options.lifetime().toNanos();
            } catch (final ArithmeticException ex) {
                lifetimeValue = Long.MAX_VALUE;
            }
            this.lifetime = lifetimeValue;
            this.remainingUses = options.uses();
            this.id = id;
        }

        private void takeUse() {
            if (this.remainingUses != ClickCallback.UNLIMITED_USES) {
                this.remainingUses--;
            }
        }

        public boolean hasRemainingUses() {
            return this.remainingUses == ClickCallback.UNLIMITED_USES || this.remainingUses > 0;
        }

        public boolean expired() {
            if (this.lifetime == Long.MAX_VALUE) return false;
            return System.nanoTime() - this.startedAt >= this.lifetime;
        }

        private boolean valid() {
            return this.hasRemainingUses() && !this.expired();
        }

        public I id() {
            return this.id;
        }
    }
}
