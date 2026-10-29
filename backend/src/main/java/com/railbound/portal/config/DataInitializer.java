package com.railbound.portal.config;

import com.railbound.portal.announcements.Announcement;
import com.railbound.portal.announcements.AnnouncementRepository;
import com.railbound.portal.bundles.CoinBundle;
import com.railbound.portal.bundles.CoinBundleRepository;
import com.railbound.portal.identity.User;
import com.railbound.portal.identity.UserRepository;
import com.railbound.portal.shop.ItemCategory;
import com.railbound.portal.shop.PublicationStatus;
import com.railbound.portal.shop.ShopItem;
import com.railbound.portal.shop.ShopItemRepository;
import com.railbound.portal.wallet.Wallet;
import com.railbound.portal.wallet.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CoinBundleRepository bundleRepository;
    private final ShopItemRepository itemRepository;
    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public DataInitializer(
            CoinBundleRepository bundleRepository,
            ShopItemRepository itemRepository,
            AnnouncementRepository announcementRepository,
            UserRepository userRepository,
            WalletRepository walletRepository) {
        this.bundleRepository = bundleRepository;
        this.itemRepository = itemRepository;
        this.announcementRepository = announcementRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
    }

    @Override
    public void run(String... args) {
        seedBundles();
        seedShopItems();
        seedAnnouncements();
        seedDemoUsers();
        log.info("Railbound Battle Royale seed initialization complete.");
    }

    private void seedBundles() {
        if (bundleRepository.count() == 0) {
            log.info("Seeding initial Real Coin bundles from PDF specification...");
            bundleRepository.saveAll(List.of(
                    new CoinBundle("bundle_rc_100", "Cadet Stash", 100, 0, 99, null, 1),
                    new CoinBundle("bundle_rc_500", "Field Cache", 500, 50, 499, "+50 BONUS", 2),
                    new CoinBundle("bundle_rc_1000", "Tactical Stash", 1000, 150, 999, "POPULAR", 3),
                    new CoinBundle("bundle_rc_2500", "Quartermaster Box", 2500, 500, 2499, "+20% VALUE", 4),
                    new CoinBundle("bundle_rc_5000", "Armory Crate", 5000, 1200, 4999, "BEST VALUE", 5),
                    new CoinBundle("bundle_rc_10000", "War Chest", 10000, 3000, 9999, "+30% VALUE", 6)
            ));
        }
    }

    private void seedShopItems() {
        if (itemRepository.count() == 0) {
            log.info("Seeding initial Armory catalog items from PDF specification...");
            itemRepository.saveAll(List.of(
                    new ShopItem(
                            "skin_op_shadow_operative",
                            ItemCategory.OPERATOR_SKIN,
                            "Shadow Operative",
                            "Covert tactical operator outfit tailored with radar-dampening composites and night-vision harness.",
                            1200,
                            PublicationStatus.PUBLISHED,
                            Map.of("slot", "OPERATOR", "minimumClientVersion", "1.0.0"),
                            Map.of("thumbnailUrl", "/assets/items/skin_op_shadow.png"),
                            "1.0.0"
                    ),
                    new ShopItem(
                            "skin_op_desert_nomad",
                            ItemCategory.OPERATOR_SKIN,
                            "Desert Nomad",
                            "Weathered ballistic cloak and dust-filtered respirator for arid skirmishes.",
                            850,
                            PublicationStatus.PUBLISHED,
                            Map.of("slot", "OPERATOR", "minimumClientVersion", "1.0.0"),
                            Map.of("thumbnailUrl", "/assets/items/skin_op_desert.png"),
                            "1.0.0"
                    ),
                    new ShopItem(
                            "att_scope_tactical_acog",
                            ItemCategory.ATTACHMENT,
                            "Tactical ACOG (4x)",
                            "High-precision 4x optical magnification optic featuring an illuminated chevron reticle.",
                            450,
                            PublicationStatus.PUBLISHED,
                            Map.of("slot", "OPTIC", "supportedWeapons", List.of("wp_m4a1", "wp_ak74m", "wp_scar_h"), "minimumClientVersion", "1.0.0"),
                            Map.of("thumbnailUrl", "/assets/items/att_scope_acog.png"),
                            "1.0.0"
                    ),
                    new ShopItem(
                            "att_barrel_suppressor_carbon",
                            ItemCategory.ATTACHMENT,
                            "Carbon Suppressor",
                            "Lightweight carbon-fiber barrel attachment reducing audible firing signature and flash.",
                            350,
                            PublicationStatus.PUBLISHED,
                            Map.of("slot", "BARREL", "supportedWeapons", List.of("wp_m4a1", "wp_ak74m", "wp_mp5"), "minimumClientVersion", "1.0.0"),
                            Map.of("thumbnailUrl", "/assets/items/att_barrel_suppressor.png"),
                            "1.0.0"
                    ),
                    new ShopItem(
                            "att_mag_extended_drum",
                            ItemCategory.ATTACHMENT,
                            "Extended Drum Magazine",
                            "High-capacity 60-round drum magazine engineered for sustained suppression fire.",
                            400,
                            PublicationStatus.PUBLISHED,
                            Map.of("slot", "MAGAZINE", "supportedWeapons", List.of("wp_m4a1", "wp_ak74m"), "minimumClientVersion", "1.0.0"),
                            Map.of("thumbnailUrl", "/assets/items/att_mag_drum.png"),
                            "1.0.0"
                    ),
                    new ShopItem(
                            "att_grip_vertical_ergonomic",
                            ItemCategory.ATTACHMENT,
                            "Ergonomic Vertical Grip",
                            "Textured underbarrel vertical foregrip delivering improved recoil control and handling.",
                            250,
                            PublicationStatus.PUBLISHED,
                            Map.of("slot", "UNDERBARREL", "supportedWeapons", List.of("wp_m4a1", "wp_scar_h"), "minimumClientVersion", "1.0.0"),
                            Map.of("thumbnailUrl", "/assets/items/att_grip_vertical.png"),
                            "1.0.0"
                    ),
                    new ShopItem(
                            "skin_wp_gold_damascus",
                            ItemCategory.WEAPON_CAMO,
                            "Gold Damascus Camo",
                            "Folded pattern-welded steel finish inlaid with pure 24K gold accents.",
                            650,
                            PublicationStatus.PUBLISHED,
                            Map.of("slot", "CAMO", "supportedWeapons", List.of("ALL"), "minimumClientVersion", "1.0.0"),
                            Map.of("thumbnailUrl", "/assets/items/skin_wp_damascus.png"),
                            "1.0.0"
                    )
            ));
        }
    }

    private void seedAnnouncements() {
        if (announcementRepository.count() == 0) {
            log.info("Seeding initial game announcements...");
            announcementRepository.saveAll(List.of(
                    new Announcement(
                            "ann_launch_01",
                            "Season 1 Armory Drop: Tactical Optics & Spec-Ops Kits",
                            "Quartermaster shipments have arrived at the sector! Equip the new 4x Tactical ACOG scope and Shadow Operative skin in the Armory.",
                            "/assets/news/ann_drop_01.png",
                            "att_scope_tactical_acog",
                            "PUBLISHED",
                            "usr_demo_admin_01"
                    ),
                    new Announcement(
                            "ann_patch_02",
                            "Patch 1.0.4 Combat Balance & Weapon Handling",
                            "Recoil patterns for vertical grips have been stabilized. Server tick rate increased to 64Hz for smoother close-quarters combat.",
                            "/assets/news/ann_patch_02.png",
                            null,
                            "PUBLISHED",
                            "usr_demo_admin_01"
                    )
            ));
        }
    }

    private void seedDemoUsers() {
        if (userRepository.count() == 0) {
            log.info("Seeding demo player and admin accounts...");
            User player = new User("usr_demo_viper_01", "00000000-0000-0000-0000-000000000001", "ViperActual", "PLAYER");
            player.setEmail("viper@railbound.internal");
            userRepository.save(player);
            walletRepository.save(new Wallet(player.getUserId(), 0));

            User admin = new User("usr_demo_admin_01", "00000000-0000-0000-0000-000000000099", "QuartermasterAdmin", "ADMIN");
            admin.setEmail("admin@railbound.internal");
            userRepository.save(admin);
            walletRepository.save(new Wallet(admin.getUserId(), 50000));
        }
    }
}
