package com.omnicore.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class OmniConfig {
    public static final ForgeConfigSpec SPEC;
    public static final OmniConfig INSTANCE;

    // [embedded_web_studio]
    public final ForgeConfigSpec.BooleanValue webServerEnabled;
    public final ForgeConfigSpec.IntValue webServerPort;
    public final ForgeConfigSpec.ConfigValue<String> webServerBindAddress;
    public final ForgeConfigSpec.ConfigValue<String> webServerToken;

    // [world_bosses]
    public final ForgeConfigSpec.BooleanValue worldBossEnabled;
    public final ForgeConfigSpec.BooleanValue autoPromoteEnabled;
    public final ForgeConfigSpec.DoubleValue globalBossHpScale;
    public final ForgeConfigSpec.DoubleValue globalBossAtkScale;
    public final ForgeConfigSpec.IntValue arenaRadius;
    public final ForgeConfigSpec.IntValue bossLeashDistance;
    public final ForgeConfigSpec.IntValue defaultSpawnIntervalMinutes;
    public final ForgeConfigSpec.IntValue landmarkRespawnCooldownMinutes;
    public final ForgeConfigSpec.IntValue defaultProtectRadius;
    public final ForgeConfigSpec.IntValue raidScalePercentPerPlayer;
    public final ForgeConfigSpec.ConfigValue<String> bossBarTheme;

    // [google_apps_script]
    public final ForgeConfigSpec.BooleanValue gasEnabled;
    public final ForgeConfigSpec.ConfigValue<String> gasWebAppUrl;
    public final ForgeConfigSpec.ConfigValue<String> gasSecretToken;
    public final ForgeConfigSpec.IntValue gasPollIntervalSeconds;

    // [announcements]
    public final ForgeConfigSpec.BooleanValue announcementsEnabled;
    public final ForgeConfigSpec.ConfigValue<String> apotheosisAnnounceRarities;
    public final ForgeConfigSpec.BooleanValue announceArtifactsFirstTimeOnly;
    public final ForgeConfigSpec.ConfigValue<String> artifactItemFilters;
    public final ForgeConfigSpec.ConfigValue<String> mobKillFilter;
    public final ForgeConfigSpec.ConfigValue<String> mobKillBlacklist;
    public final ForgeConfigSpec.DoubleValue mobKillMinHealth;

    static {
        Pair<OmniConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(OmniConfig::new);
        SPEC = specPair.getRight();
        INSTANCE = specPair.getLeft();
    }

    private OmniConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("==================================================================",
                        " Dragonfyre Bridge & Embedded Studio Configuration (.toml)",
                        " มาตรฐาน Forge 1.20.1 รองรับการตั้งค่าเว็บในตัวและระบบ World Boss",
                        "==================================================================")
               .push("embedded_web_studio");

        webServerEnabled = builder
                .comment("เปิดใช้งาน Embedded Web Studio ภายในตัว Mod หรือไม่ (เข้าผ่านเบราว์เซอร์ไม่ต้องพึ่ง Node.js หรือแผงภายนอก)")
                .define("enabled", true);

        webServerPort = builder
                .comment("หมายเลขพอร์ต HTTP สำหรับ Web Studio (เช่น 8088)")
                .defineInRange("port", 8088, 1024, 65535);

        webServerBindAddress = builder
                .comment("IP สำหรับ Bind Server (0.0.0.0 คือรับการเชื่อมต่อจากทุก IP / Tailscale / LAN)")
                .define("bindAddress", "0.0.0.0");

        webServerToken = builder
                .comment("รหัสความปลอดภัย (Security Secret Token) สำหรับล็อกอินจัดการในหน้า Web Studio")
                .define("secretToken", "DF_SECRET_2026");

        builder.pop();

        builder.comment("==================================================================",
                        " World Boss & Arena Settings",
                        "==================================================================")
               .push("world_bosses");

        worldBossEnabled = builder
                .comment("เปิด/ปิดระบบ World Boss ตามเวลาและคำสั่ง")
                .define("worldBossEnabled", true);

        autoPromoteEnabled = builder
                .comment("เปิด/ปิดการยกระดับม็อบเกิดธรรมชาติเป็น World Boss (สำหรับจุด Anchor แลนด์มาร์ก)")
                .define("autoPromoteEnabled", true);

        globalBossHpScale = builder
                .comment("ตัวคูณเลือดรวมสำหรับบอสทั้งหมด (Global HP Multiplier)")
                .defineInRange("globalBossHpScale", 1.0, 0.1, 100.0);

        globalBossAtkScale = builder
                .comment("ตัวคูณพลังโจมตีรวมสำหรับบอสทั้งหมด (Global ATK Multiplier)")
                .defineInRange("globalBossAtkScale", 1.0, 0.1, 100.0);

        arenaRadius = builder
                .comment("รัศมีของสนามประลอง World Boss รอบตัวบอส (Arena Radius in blocks, default: 30)")
                .defineInRange("arenaRadius", 30, 5, 500);

        bossLeashDistance = builder
                .comment("ระยะจำกัดไม่ให้บอสเดิน/บินหลุดออกจากจุดเกิด (Boss Leash Distance in blocks from spawn origin, default: 8)")
                .defineInRange("bossLeashDistance", 8, 2, 100);

        defaultSpawnIntervalMinutes = builder
                .comment("ระยะเวลาระหว่างการสุ่มเกิดของ World Boss แต่ละรอบ (นาที) - Default Spawn Interval in minutes, default: 120)")
                .defineInRange("defaultSpawnIntervalMinutes", 120, 5, 1440);

        landmarkRespawnCooldownMinutes = builder
                .comment("ระยะเวลารอเกิดใหม่ของบอสประจำ Landmark หลังจากบอสตายหรือหมดเวลา (นาที) - Landmark Respawn Cooldown in minutes, default: 120)")
                .defineInRange("landmarkRespawnCooldownMinutes", 120, 5, 1440);

        defaultProtectRadius = builder
                .comment("รัศมีบาเรียห้ามขุด/ระเบิดทำลายฉากเริ่มต้น (Anti-Grief Arena Radius in blocks)")
                .defineInRange("defaultProtectRadius", 30, 5, 2000);

        raidScalePercentPerPlayer = builder
                .comment("เปอร์เซ็นต์เลือดที่เพิ่มขึ้นต่อผู้เล่น 1 คนในระยะ 40m (Raid Dynamic Scaling %)")
                .defineInRange("raidScalePercentPerPlayer", 15, 0, 100);

        bossBarTheme = builder
                .comment("ธีมหลอดเลือดบอสเริ่มต้น (DRAGON_GOLD, CRIMSON_FIRE, VOID_PURPLE, FROST_BLUE, EMERALD)")
                .define("bossBarTheme", "DRAGON_GOLD");

        builder.pop();

        builder.comment("==================================================================",
                        " Google Apps Script Integration (Optional Remote Bridge)",
                        "==================================================================")
               .push("google_apps_script");

        gasEnabled = builder
                .comment("เปิดใช้งานการเชื่อมต่อ Google Apps Script Web App หรือไม่")
                .define("enabled", false);

        gasWebAppUrl = builder
                .comment("Google Apps Script Web App Deployment URL")
                .define("webAppUrl", "");

        gasSecretToken = builder
                .comment("Google Apps Script Secret Key")
                .define("secretToken", "DF_SECRET_2026");

        gasPollIntervalSeconds = builder
                .comment("ระยะห่างการ Polling ดึงข้อมูลจาก Google Sheets (วินาที)")
                .defineInRange("pollIntervalSeconds", 30, 5, 3600);

        builder.pop();

        builder.comment("==================================================================",
                        " Center-Screen Announcements & Loot Detection Settings",
                        "==================================================================")
               .push("announcements");

        announcementsEnabled = builder
                .comment("เปิด/ปิดระบบป้ายประกาศกลางจอสำหรับไอเทมหายากและสังหารบอส")
                .define("enabled", true);

        apotheosisAnnounceRarities = builder
                .comment("ระดับความหายากของ Apotheosis ที่จะประกาศกลางจอ (ประกาศเฉพาะ 2 ลำดับบนสุด: MYTHIC, ANCIENT)")
                .define("apotheosisRarities", "MYTHIC,ANCIENT,HEIRLOOM,ESOTERIC");

        announceArtifactsFirstTimeOnly = builder
                .comment("ประกาศเมื่อผู้เล่นค้นพบ Artifact ชิ้นใหม่เป็นครั้งแรกเท่านั้น (ชิ้นซ้ำกันจะไม่ประกาศ)")
                .define("artifactFirstTimeOnly", true);

        artifactItemFilters = builder
                .comment("ฟิลเตอร์ Namespace หรือ Item ID สำหรับไอเทม Artifact/Relic (คั่นด้วยจุลภาค)")
                .define("artifactItemFilters", "relics:*,artifacts:*,*artifact*,*relic*");

        mobKillFilter = builder
                .comment("ฟิลเตอร์ Entity ID สำหรับบอสจริงที่จะประกาศเมื่อถูกสังหาร (ระบุเฉพาะตัวบอส ไม่ใส่ wildcard ม็อบทั่วไป เพื่อป้องกันสแปมลูกน้อง)")
                .define("mobKillFilter", "cataclysm:*,iceandfire:*_dragon,iceandfire:gorgon,iceandfire:cyclops,iceandfire:hydra,mowziesmobs:ferrous_wroughtnaut,mowziesmobs:frostmaw,mowziesmobs:barako,twilightforest:naga,twilightforest:lich,twilightforest:minoshroom,twilightforest:hydra,twilightforest:knight_phantom,twilightforest:ur_ghast,twilightforest:alpha_yeti,twilightforest:snow_queen");

        mobKillBlacklist = builder
                .comment("แบล็กลิสต์ม็อบลูกน้อง/มินเนียน/สัตว์เล็กที่ห้ามประกาศกลางจอเด็ดขาด (Wildcard)")
                .define("mobKillBlacklist", "*minion*,*baby*,*drone*,*larva*,*servant*,*summon*,*crane*,*fitter*,*raptor*,*grottol*,*lantern*,*foliaath*,*umvuthana*,twilightforest:raven,twilightforest:squirrel,twilightforest:bunny,twilightforest:tiny_bird,twilightforest:kobold,twilightforest:firefly,twilightforest:mosquito_swarm,twilightforest:slime_beetle,twilightforest:redcap*");

        mobKillMinHealth = builder
                .comment("เลือดขั้นต่ำของม็อบที่จะประกาศกลางจอ (HP Threshold ป้องกันลูกน้อง/มินเนียน)")
                .defineInRange("mobKillMinHealth", 80.0, 1.0, 100000.0);

        builder.pop();
    }
}
