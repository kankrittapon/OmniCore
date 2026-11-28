package com.omnicore;

import com.omnicore.config.OmniConfig;
import com.omnicore.network.ModNetwork;
import com.omnicore.server.AnnouncementTriggerHandler;
import com.omnicore.server.EmbeddedStudioServer;
import com.omnicore.server.GasHotReloadManager;
import com.omnicore.server.UniversalBossManager;
import com.omnicore.server.UniversalBossManager.BossProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

@Mod("omnicore")
public class OmniCoreMod {
    public static final String MOD_ID = "omnicore";
    public static volatile MinecraftServer SERVER;

    public OmniCoreMod() {
        // 100% Server-side support: Clients do NOT need this mod to connect!
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isFromServer) -> true
                )
        );

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, OmniConfig.SPEC, "omnicore-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, com.omnicore.config.OmniClientConfig.SPEC, "omnicore-client.toml");
        ModNetwork.register();
        com.omnicore.server.CuriosBridge.init();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(UniversalBossManager.INSTANCE);
        MinecraftForge.EVENT_BUS.register(AnnouncementTriggerHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.omnicore.server.PvPManager.INSTANCE);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        SERVER = event.getServer();
        UniversalBossManager.INSTANCE.initDefaults();
        UniversalBossManager.INSTANCE.exportRegisteredMobsJson();
        GasHotReloadManager.INSTANCE.start(event.getServer());
        EmbeddedStudioServer.INSTANCE.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        SERVER = null;
        GasHotReloadManager.INSTANCE.stop();
        EmbeddedStudioServer.INSTANCE.stop();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var omnicoreNode = event.getDispatcher().register(
            Commands.literal("omnicore")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(UniversalBossManager.INSTANCE.formatRegistrySummary()), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(UniversalBossManager.INSTANCE.formatAreaStatusSummary(ctx.getSource().getServer())), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    UniversalBossManager.INSTANCE.loadRegistryFromFile();
                    ctx.getSource().sendSuccess(() -> Component.literal("§aโหลดค่าตั้งค่าบอสจาก omnicore_bosses.json ใหม่สำเร็จแล้ว! (" + UniversalBossManager.INSTANCE.getRegisteredBossCount() + " ตัว)"), true);
                    return 1;
                }))
                .then(Commands.literal("artifacts")
                    .then(Commands.literal("list").executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        CompoundTag omniData = sp.getPersistentData().getCompound(AnnouncementTriggerHandler.NBT_PLAYER_DATA);
                        ListTag list = omniData.getList(AnnouncementTriggerHandler.NBT_DISCOVERED_ARTIFACTS, Tag.TAG_STRING);
                        ctx.getSource().sendSuccess(() -> Component.literal("§6=== [รายการ Artifact ที่คุณค้นพบแล้ว (" + list.size() + " ชิ้น)] ==="), false);
                        for (int i = 0; i < list.size(); i++) {
                            final int idx = i;
                            ctx.getSource().sendSuccess(() -> Component.literal(" §e• §f" + list.getString(idx)), false);
                        }
                        return 1;
                    }))
                    .then(Commands.literal("reset").executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        CompoundTag omniData = sp.getPersistentData().getCompound(AnnouncementTriggerHandler.NBT_PLAYER_DATA);
                        omniData.remove(AnnouncementTriggerHandler.NBT_DISCOVERED_ARTIFACTS);
                        ctx.getSource().sendSuccess(() -> Component.literal("§aรีเซ็ตประวัติการค้นพบ Artifact ของคุณเรียบร้อยแล้ว!"), true);
                        return 1;
                    })))
                .then(Commands.literal("respawnnow")
                    .then(Commands.argument("boss", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String bossQuery = StringArgumentType.getString(ctx, "boss");
                            BossProfile profile = UniversalBossManager.INSTANCE.findBossProfile(bossQuery);
                            if (profile == null) {
                                ctx.getSource().sendFailure(Component.literal("§cไม่พบ Boss: " + bossQuery));
                                return 0;
                            }
                            boolean ok = UniversalBossManager.INSTANCE.forceRespawnLandmark(ctx.getSource().getServer(), profile.entityId);
                            if (ok) {
                                ctx.getSource().sendSuccess(() -> Component.literal("§aรีเซ็ตคูลดาวน์และเสก " + profile.displayName + " ที่จุด Landmark เรียบร้อยแล้ว!"), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("§cไม่สามารถเสกที่ Landmark ได้ (ตรวจสอบว่าตั้งค่าเป็น ANCHOR_LANDMARK หรือไม่)"));
                            }
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("spawnhere")
                    .then(Commands.argument("boss", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String bossQuery = StringArgumentType.getString(ctx, "boss");
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            BlockPos pos = sp.blockPosition().relative(sp.getDirection(), 5);
                            BossProfile profile = UniversalBossManager.INSTANCE.findBossProfile(bossQuery);
                            if (profile == null) {
                                ctx.getSource().sendFailure(Component.literal("§cไม่พบ Boss ที่ตรงกับ: '" + bossQuery + "' (พิมพ์ /dfboss list เพื่อดูหมายเลข 1-4 หรือชื่อ)"));
                                return 0;
                            }
                            boolean ok = UniversalBossManager.INSTANCE.spawnWorldBossAt(ctx.getSource().getServer(), profile, sp.serverLevel(), pos);
                            if (ok) {
                                ctx.getSource().sendSuccess(() -> Component.literal("§aเสก World Boss ตรงหน้าสำเร็จ: §6" + profile.displayName + " §7(" + profile.entityId + ") ที่ " + pos.toShortString()), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("§cเกิดข้อผิดพลาดในการสร้าง Entity: " + profile.entityId));
                            }
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("spawn")
                    .then(Commands.argument("boss", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String bossQuery = StringArgumentType.getString(ctx, "boss");
                            BossProfile profile = UniversalBossManager.INSTANCE.findBossProfile(bossQuery);
                            if (profile == null) {
                                ctx.getSource().sendFailure(Component.literal("§cไม่พบ Boss ที่ตรงกับ: '" + bossQuery + "' (พิมพ์ /dfboss list เพื่อดูหมายเลข 1-4 หรือชื่อ)"));
                                return 0;
                            }
                            boolean ok = UniversalBossManager.INSTANCE.spawnWorldBossNow(ctx.getSource().getServer(), bossQuery, null);
                            if (ok) {
                                ctx.getSource().sendSuccess(() -> Component.literal("§aเสก World Boss สำเร็จ: §6" + profile.displayName + " §7(" + profile.entityId + ")"), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("§cเกิดข้อผิดพลาดในการเสก: " + profile.entityId));
                            }
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("spawnnear")
                    .then(Commands.argument("boss", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String bossQuery = StringArgumentType.getString(ctx, "boss");
                            BossProfile profile = UniversalBossManager.INSTANCE.findBossProfile(bossQuery);
                            if (profile == null) {
                                ctx.getSource().sendFailure(Component.literal("§cไม่พบ Boss ที่ตรงกับ: '" + bossQuery + "' (พิมพ์ /dfboss list เพื่อดูหมายเลข 1-4 หรือชื่อ)"));
                                return 0;
                            }
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            boolean ok = UniversalBossManager.INSTANCE.spawnWorldBossNow(ctx.getSource().getServer(), bossQuery, sp);
                            if (ok) {
                                ctx.getSource().sendSuccess(() -> Component.literal("§aเสก World Boss ห่าง 500 บล็อกสำเร็จ: §6" + profile.displayName + " §7(" + profile.entityId + ")"), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("§cเกิดข้อผิดพลาดในการเสก: " + profile.entityId));
                            }
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("promote")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(5, 500))
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            int rad = IntegerArgumentType.getInteger(ctx, "radius");
                            AABB box = sp.getBoundingBox().inflate(15.0);
                            LivingEntity target = sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != sp && e.isAlive())
                                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(sp))).orElse(null);
                            if (target == null) {
                                ctx.getSource().sendFailure(Component.literal("§cไม่พบม็อบในระยะ 15 บล็อก กรุณาเข้าใกล้ม็อบที่ต้องการโปรโมต"));
                                return 0;
                            }
                            UniversalBossManager.PromoteResult res = UniversalBossManager.INSTANCE.promoteExistingEntity(target, rad);
                            if (res.success) {
                                ctx.getSource().sendSuccess(() -> Component.literal("§a" + res.message), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("§c" + res.message));
                            }
                            return res.success ? 1 : 0;
                        }))
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        AABB box = sp.getBoundingBox().inflate(15.0);
                        LivingEntity target = sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != sp && e.isAlive())
                                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(sp))).orElse(null);
                        if (target == null) {
                            ctx.getSource().sendFailure(Component.literal("§cไม่พบม็อบในระยะ 15 บล็อก กรุณาเข้าใกล้ม็อบที่ต้องการโปรโมต"));
                            return 0;
                        }
                        UniversalBossManager.PromoteResult res = UniversalBossManager.INSTANCE.promoteExistingEntity(target, 0);
                        if (res.success) {
                            ctx.getSource().sendSuccess(() -> Component.literal("§a" + res.message), true);
                        } else {
                            ctx.getSource().sendFailure(Component.literal("§c" + res.message));
                        }
                        return res.success ? 1 : 0;
                    }))
                .then(Commands.literal("anchor")
                    .then(Commands.literal("set")
                        .then(Commands.argument("entityId", StringArgumentType.string())
                            .then(Commands.argument("radius", IntegerArgumentType.integer(10, 500))
                                .executes(ctx -> {
                                    String eid = StringArgumentType.getString(ctx, "entityId");
                                    int rad = IntegerArgumentType.getInteger(ctx, "radius");
                                    BlockPos pos = ctx.getSource().getPlayerOrException().blockPosition();
                                    UniversalBossManager.INSTANCE.setAnchorForBoss(eid, pos, rad);
                                    ctx.getSource().sendSuccess(() -> Component.literal(String.format("§6ล็อกจุดแลนด์มาร์กให้ %s ที่ (%d, %d, %d) รัศมี %d บล็อกแล้ว!", eid, pos.getX(), pos.getY(), pos.getZ(), rad)), true);
                                    return 1;
                                }))
                            .executes(ctx -> {
                                String eid = StringArgumentType.getString(ctx, "entityId");
                                BlockPos pos = ctx.getSource().getPlayerOrException().blockPosition();
                                UniversalBossManager.INSTANCE.setAnchorForBoss(eid, pos, 80);
                                ctx.getSource().sendSuccess(() -> Component.literal(String.format("§6ล็อกจุดแลนด์มาร์กให้ %s ที่ (%d, %d, %d) แล้ว! (จุดอื่นๆ ในแมพจะเป็นบอสธรรมดา)", eid, pos.getX(), pos.getY(), pos.getZ())), true);
                                return 1;
                            })))
                    .then(Commands.literal("clear")
                        .then(Commands.argument("entityId", StringArgumentType.string())
                            .executes(ctx -> {
                                String eid = StringArgumentType.getString(ctx, "entityId");
                                UniversalBossManager.INSTANCE.clearAnchorForBoss(eid);
                                ctx.getSource().sendSuccess(() -> Component.literal("§eล้างจุดแลนด์มาร์กของ " + eid + " แล้ว"), true);
                                return 1;
                            }))))
                .then(Commands.literal("scale")
                    .then(Commands.argument("hpMult", FloatArgumentType.floatArg(0.1f, 50.0f))
                        .then(Commands.argument("atkMult", FloatArgumentType.floatArg(0.1f, 20.0f))
                            .executes(ctx -> {
                                float hp = FloatArgumentType.getFloat(ctx, "hpMult");
                                float atk = FloatArgumentType.getFloat(ctx, "atkMult");
                                UniversalBossManager.INSTANCE.globalHpScale = hp;
                                UniversalBossManager.INSTANCE.globalAtkScale = atk;
                                ctx.getSource().sendSuccess(() -> Component.literal(String.format("§6ปรับ Global Boss Scaling เป็น HP x%.2f, ATK x%.2f สำเร็จ!", hp, atk)), true);
                                return 1;
                            }))))
                .then(Commands.literal("mobs")
                    .then(Commands.argument("filter", StringArgumentType.string())
                        .executes(ctx -> {
                            String f = StringArgumentType.getString(ctx, "filter").toLowerCase(Locale.ROOT);
                            String matches = ForgeRegistries.ENTITY_TYPES.getEntries().stream()
                                    .map(e -> e.getKey().location().toString())
                                    .filter(id -> id.contains(f))
                                    .limit(20)
                                    .collect(Collectors.joining(", "));
                            ctx.getSource().sendSuccess(() -> Component.literal("§b[ผลการค้นหาม็อบ (" + f + ")]: §f" + (matches.isEmpty() ? "ไม่พบ" : matches)), false);
                            return 1;
                        }))
                    .executes(ctx -> {
                        UniversalBossManager.INSTANCE.exportRegisteredMobsJson();
                        ctx.getSource().sendSuccess(() -> Component.literal("§aส่งออกรายชื่อม็อบทั้งหมดของทุก Mod ไปยัง config/dragonfyre_bridge/registered_mobs.json แล้ว!"), false);
                        return 1;
                    }))
                .then(Commands.literal("clear").executes(ctx -> {
                    UniversalBossManager.INSTANCE.despawnAllActiveBosses(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("§eเคลียร์ World Boss และถอนบาเรียทั้งหมดแล้ว!"), true);
                    return 1;
                }))
                .then(Commands.literal("announce")
                    .then(Commands.literal("toggle").executes(ctx -> {
                        AnnouncementTriggerHandler.INSTANCE.announcementsEnabled = !AnnouncementTriggerHandler.INSTANCE.announcementsEnabled;
                        boolean en = AnnouncementTriggerHandler.INSTANCE.announcementsEnabled;
                        ctx.getSource().sendSuccess(() -> Component.literal("§6[OmniCore] การประกาศกลางจอทั้งหมด: " + (en ? "§aเปิดใช้งาน (ON)" : "§cปิดใช้งาน (OFF)")), true);
                        return 1;
                    }))
                    .then(Commands.literal("mob")
                        .then(Commands.literal("enable").executes(ctx -> {
                            AnnouncementTriggerHandler.INSTANCE.mobAnnounceEnabled = true;
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[OmniCore] เปิดการประกาศเมื่อสังหารบอสแล้ว"), true);
                            return 1;
                        }))
                        .then(Commands.literal("disable").executes(ctx -> {
                            AnnouncementTriggerHandler.INSTANCE.mobAnnounceEnabled = false;
                            ctx.getSource().sendSuccess(() -> Component.literal("§c[OmniCore] ปิดการประกาศเมื่อสังหารบอสแล้ว"), true);
                            return 1;
                        }))
                        .then(Commands.literal("toggle").executes(ctx -> {
                            AnnouncementTriggerHandler.INSTANCE.mobAnnounceEnabled = !AnnouncementTriggerHandler.INSTANCE.mobAnnounceEnabled;
                            boolean en = AnnouncementTriggerHandler.INSTANCE.mobAnnounceEnabled;
                            ctx.getSource().sendSuccess(() -> Component.literal("§6[OmniCore] การประกาศสังหารบอส: " + (en ? "§aเปิด (ON)" : "§cปิด (OFF)")), true);
                            return 1;
                        }))
                    )
                    .then(Commands.literal("msg")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String msg = StringArgumentType.getString(ctx, "message");
                                AnnouncementTriggerHandler.broadcastCustom("📢 " + msg, "ประกาศจากผู้ดูแลเซิร์ฟเวอร์", 0xF59E0B, "RUNE_GLOW", "minecraft:block.bell.use", 120);
                                return 1;
                            })))
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            AnnouncementTriggerHandler.broadcastCustom("📢 " + msg, "ประกาศจากผู้ดูแลเซิร์ฟเวอร์", 0xF59E0B, "RUNE_GLOW", "minecraft:block.bell.use", 120);
                            return 1;
                        })))
                .then(Commands.literal("sync").executes(ctx -> {
                    GasHotReloadManager.INSTANCE.triggerImmediatePoll(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("§aสั่งซิงก์ค่าตั้งค่าจาก Google Apps Script ทันทีแล้ว!"), false);
                    return 1;
                }))
                .then(Commands.literal("studio").executes(ctx -> {
                    int p = OmniConfig.INSTANCE.webServerPort.get();
                    String t = OmniConfig.INSTANCE.webServerToken.get();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6🌐 === [OmniCore Web Studio] ===§r\n" +
                            " » URL: §fhttp://127.0.0.1:" + p + "/ (หรือ http://<server-ip>:" + p + "/)\n" +
                            " » Token: §a" + t
                    ), false);
                    return 1;
                }))
                .then(Commands.literal("system")
                    .then(Commands.literal("status").executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            String.format("§6=== [World Boss Engine System Status] ===§r\n » ระบบสุ่มเกิด World Boss (Random Spawn): %s\n » ระบบโปรโมตม็อบธรรมชาติ (Auto Promote Mobs): %s",
                                UniversalBossManager.INSTANCE.worldBossSystemEnabled ? "§aเปิด (ON)" : "§cปิด (OFF)",
                                UniversalBossManager.INSTANCE.autoPromoteSystemEnabled ? "§aเปิด (ON)" : "§cปิด (OFF)"
                            )), false);
                        return 1;
                    }))
                    .then(Commands.literal("random")
                        .then(Commands.literal("enable").executes(ctx -> {
                            UniversalBossManager.INSTANCE.worldBossSystemEnabled = true;
                            ctx.getSource().sendSuccess(() -> Component.literal("§aเปิดระบบสุ่มเกิด World Boss อัตโนมัติแล้ว"), true);
                            return 1;
                        }))
                        .then(Commands.literal("disable").executes(ctx -> {
                            UniversalBossManager.INSTANCE.worldBossSystemEnabled = false;
                            ctx.getSource().sendSuccess(() -> Component.literal("§cปิดระบบสุ่มเกิด World Boss อัตโนมัติแล้ว"), true);
                            return 1;
                        }))
                        .then(Commands.literal("toggle").executes(ctx -> {
                            UniversalBossManager.INSTANCE.worldBossSystemEnabled = !UniversalBossManager.INSTANCE.worldBossSystemEnabled;
                            boolean s = UniversalBossManager.INSTANCE.worldBossSystemEnabled;
                            ctx.getSource().sendSuccess(() -> Component.literal("§eระบบสุ่มเกิด World Boss อัตโนมัติ: " + (s ? "§aเปิด (ON)" : "§cปิด (OFF)")), true);
                            return 1;
                        })))
                    .then(Commands.literal("promote")
                        .then(Commands.literal("enable").executes(ctx -> {
                            UniversalBossManager.INSTANCE.autoPromoteSystemEnabled = true;
                            ctx.getSource().sendSuccess(() -> Component.literal("§aเปิดระบบโปรโมตม็อบธรรมชาติเป็นบอสแล้ว"), true);
                            return 1;
                        }))
                        .then(Commands.literal("disable").executes(ctx -> {
                            UniversalBossManager.INSTANCE.autoPromoteSystemEnabled = false;
                            ctx.getSource().sendSuccess(() -> Component.literal("§cปิดระบบโปรโมตม็อบธรรมชาติเป็นบอสแล้ว"), true);
                            return 1;
                        }))
                        .then(Commands.literal("toggle").executes(ctx -> {
                            UniversalBossManager.INSTANCE.autoPromoteSystemEnabled = !UniversalBossManager.INSTANCE.autoPromoteSystemEnabled;
                            boolean s = UniversalBossManager.INSTANCE.autoPromoteSystemEnabled;
                            ctx.getSource().sendSuccess(() -> Component.literal("§eระบบโปรโมตม็อบธรรมชาติ: " + (s ? "§aเปิด (ON)" : "§cปิด (OFF)")), true);
                            return 1;
                        }))))
        );

        // Command Aliases: /dfboss, /wboss, /core ชี้ไปที่ /omnicore โดยตรง
        event.getDispatcher().register(Commands.literal("dfboss").requires(src -> src.hasPermission(2)).redirect(omnicoreNode));
        event.getDispatcher().register(Commands.literal("wboss").requires(src -> src.hasPermission(2)).redirect(omnicoreNode));
        event.getDispatcher().register(Commands.literal("core").requires(src -> src.hasPermission(2)).redirect(omnicoreNode));

        // คำสั่ง /pvp สำหรับผู้เล่นทุกคน (ไม่จำกัด op) สลับโหมดต่อสู้ PvP / ตรวจสอบสถานะ
        event.getDispatcher().register(
            Commands.literal("pvp")
                .then(Commands.literal("on").executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    com.omnicore.server.PvPManager.INSTANCE.setPvPState(sp, true);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    com.omnicore.server.PvPManager.INSTANCE.setPvPState(sp, false);
                    return 1;
                }))
                .then(Commands.literal("toggle").executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    com.omnicore.server.PvPManager.INSTANCE.togglePvP(sp);
                    return 1;
                }))
                .executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    com.omnicore.server.PvPManager.INSTANCE.togglePvP(sp);
                    return 1;
                })
        );
    }
}
