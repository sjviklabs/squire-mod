package com.sjviklabs.squire.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.sjviklabs.squire.SquireMod;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Player-attached data that persists squire identity across death, crest loss,
 * server restarts, and dimension changes. The squire's soul lives here.
 */
public class SquireDataAttachment {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SquireMod.MODID);

    public static final Supplier<AttachmentType<SquireData>> SQUIRE_DATA =
            ATTACHMENTS.register("squire_data", () ->
                    AttachmentType.builder(SquireData::empty)
                            .serialize(SquireData.CODEC)
                            .build());

    public record SquireData(
            int totalXP,
            int level,
            String customName,
            boolean slimModel,
            Optional<UUID> squireUUID
    ) {
        public static final Codec<SquireData> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.fieldOf("totalXP").forGetter(SquireData::totalXP),
                        Codec.INT.fieldOf("level").forGetter(SquireData::level),
                        Codec.STRING.fieldOf("customName").forGetter(SquireData::customName),
                        Codec.BOOL.fieldOf("slimModel").forGetter(SquireData::slimModel),
                        Codec.STRING.optionalFieldOf("squireUUID").xmap(
                                opt -> opt.map(UUID::fromString),
                                opt -> opt.map(UUID::toString)
                        ).forGetter(SquireData::squireUUID)
                ).apply(instance, SquireData::new));

        public static SquireData empty() {
            return new SquireData(0, 0, "Squire", false, Optional.empty());
        }

        public SquireData withXP(int totalXP, int level) {
            return new SquireData(totalXP, level, this.customName, this.slimModel, this.squireUUID);
        }

        public SquireData withName(String name) {
            return new SquireData(this.totalXP, this.level, name, this.slimModel, this.squireUUID);
        }

        public SquireData withAppearance(boolean slim) {
            return new SquireData(this.totalXP, this.level, this.customName, slim, this.squireUUID);
        }

        public SquireData withSquireUUID(UUID uuid) {
            return new SquireData(this.totalXP, this.level, this.customName, this.slimModel, Optional.ofNullable(uuid));
        }

        public SquireData clearSquireUUID() {
            return new SquireData(this.totalXP, this.level, this.customName, this.slimModel, Optional.empty());
        }

        public boolean hasSquire() {
            return this.squireUUID.isPresent();
        }
    }
}
