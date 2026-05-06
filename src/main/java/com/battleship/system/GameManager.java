package com.battleship.system;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.battleship.engine.BattleEngine;
import com.battleship.model.*;
import com.battleship.model.enums.*;
import com.battleship.util.BalanceConfig;
import com.battleship.util.InputHelper;

/**
 * Mengelola seluruh siklus game:
 *   start() -> showHelp() -> initPlayer() -> gameLoop()
 *     -> offerEnemyChoice() -> runBattle() -> handleReward() -> [ulang]
 *
 * Semua logika UI (print) dan semua logika input pemain ada di sini,
 * sehingga class-class lain (Ship, Mage, dll) tetap bersih dari I/O.
 */
public class GameManager {

    private PlayerShip  player;
    private InputHelper input;
    private final Random rng   = new Random();
    private int         stage  = 1;
    private final AtomicBoolean cannonDoubled = new AtomicBoolean(false);
    private BattleEngine battleEngine;

    private static final String[] ENEMY_NAMES = {
        "Galleon Merah", "Fregat Vortex", "Brigantine Badai", "Junk Hantu",
        "Karakoa Iblis", "Schooner Kutukan", "Barque Neraka", "Dhow Kegelapan",
        "Frigate Bayangan", "Brig Kutukan"
    };
    private static final String[] BOSS_TITLES = {
        "Raja Tua Laut", "Sang Leviathan", "Dewa Badai", "Si Mata Satu",
        "Laksamana Kegelapan", "Naga Samudra", "Hantu Laut Dalam"
    };
    private static final String[] MAGE_NAMES = {
        "Ignis", "Aquara", "Voltus", "Pyra", "Hydra", "Tempest",
        "Ember", "Cascade", "Thunder", "Inferno", "Torrent", "Zephyr",
        "Cinder", "Deluge", "Gale", "Blaze", "Surge", "Frost", "Ash", "Mist"
    };

    public GameManager() {
        this.input = new InputHelper(new Scanner(System.in));
    }

    // -------------------------------------------------------------------------
    // START & INITIALIZATION
    // -------------------------------------------------------------------------

    public void start() {
        printBanner();
        showHelp();
        initPlayer();
        gameLoop();
    }

    private void printBanner() {
        System.out.println();
        System.out.println("+============================================================+");
        System.out.println("|    (*)   ROGUELIKE PIRATE MAGE BATTLE   (*)               |");
        System.out.println("|          v4 Strategic Edition -- OOP Final Project        |");
        System.out.println("+============================================================+");
        System.out.println();
    }

    private void showHelp() {
        System.out.println("  +------------------------------------------------------+");
        System.out.println("  |  ELEMEN  : FIRE > STORM > WATER > FIRE (x2.0 SE)    |");
        System.out.println("  +------------------------------------------------------+");
        System.out.println("  |  AKSI BATTLE:                                        |");
        System.out.println("  |   [1] Tembak Meriam -- pilih tipe peluru             |");
        System.out.println("  |       Iron(safe) | Explosive(x2.5) |                 |");
        System.out.println("  |       Chain(WEAKENED) | Grapeshot(BURNED)            |");
        System.out.println("  |   [2] Sihir Mage    -- elemen + sinergy bonus        |");
        System.out.println("  |   [3] Gunakan Jurus -- efek spesial, 1x per battle   |");
        System.out.println("  |   [4] Bertahan      -- -50% damage masuk turn ini    |");
        System.out.println("  |   [5] Minum Potion  -- heal 50 HP                    |");
        System.out.println("  +------------------------------------------------------+");
        System.out.println("  |  SINERGY : 2+ Mage elemen sama -> +20% magic dmg    |");
        System.out.println("  |            3+ Mage elemen sama -> +40% magic dmg    |");
        System.out.println("  +------------------------------------------------------+");
        System.out.println("  |  MUSUH   : Pilih 1 dari 3 opsi setiap stage         |");
        System.out.println("  |  REWARD  : 3 kartu acak dari pool besar (weighted)  |");
        System.out.println("  +------------------------------------------------------+");
        System.out.println();
        input.pressEnter();
    }

    private void initPlayer() {
        System.out.println("  +--------------------------------------------------+");
        System.out.println("  |           PENDAFTARAN KAPTEN                     |");
        System.out.println("  +--------------------------------------------------+");
        System.out.print("  Nama Kapten: ");
        String captainName = input.readLine();
        if (captainName.isEmpty()) captainName = "Nusantara";

        player = new PlayerShip(
            "Kapal " + captainName,
            BalanceConfig.PLAYER_HP,
            BalanceConfig.PLAYER_DAMAGE,
            Element.WATER
        );

        System.out.println();
        System.out.println("  Pilih Mage pertama + Jurus:");
        System.out.println("  [1] FIRE  + INFERNO    (Damage x3.0, sekali pakai)");
        System.out.println("  [2] FIRE  + IGNITE     (Damage + BURNED 3 turn)");
        System.out.println("  [3] WATER + TIDAL WAVE (Damage x2.0 + Heal 35 HP)");
        System.out.println("  [4] WATER + FREEZE     (Damage + FROZEN skip+kebal)");
        System.out.println("  [5] STORM + CHAIN BOLT (Damage x2.5 + WEAKENED 3t)");
        System.out.println("  [6] STORM + OVERCHARGE (Semua Mage x1.2 sekaligus)");
        System.out.print("  Pilihan (1-6): ");

        int choice = input.readInt(1, 6, 1);

        SpellType[] spells   = {
            SpellType.INFERNO,    SpellType.IGNITE,
            SpellType.TIDAL_WAVE, SpellType.FREEZE,
            SpellType.CHAIN_BOLT, SpellType.OVERCHARGE
        };
        Element[] elements = {
            Element.FIRE,  Element.FIRE,
            Element.WATER, Element.WATER,
            Element.STORM, Element.STORM
        };

        Mage startingMage = new Mage(
            randomMageName(),
            elements[choice - 1],
            BalanceConfig.START_MAGE_PWR,
            spells[choice - 1]
        );
        player.recruitMage(startingMage);

        System.out.println();
        System.out.printf("  Mage  : %s%n", startingMage.getInfo(true));
        System.out.printf("  Jurus : %s%n", startingMage.getSpellType().getFullDescription());
        System.out.println("  Peluru: Iron(tak terbatas), Explosive x3, Chain x3, Grapeshot x3");
        System.out.println("  Potion: 2");
        System.out.println();
        System.out.println("  Berlayar! Pilih musuh dengan bijak setiap stage.");
        System.out.println();
        input.pressEnter();

        battleEngine = new BattleEngine(player, input, rng, cannonDoubled);
    }

    // -------------------------------------------------------------------------
    // GAME LOOP
    // -------------------------------------------------------------------------

    private void gameLoop() {
        Ship currentEnemy;

        while (player.isAlive()) {
            cannonDoubled.set(false);
            printStageHeader();

            boolean isBoss = (stage % 5 == 0);

            if (isBoss) {
                currentEnemy = generateBoss(stage);
                System.out.println("  *** STAGE BOSS! Tidak ada pilihan musuh. ***");
                System.out.println(currentEnemy.getStatusDisplay());
            } else {
                currentEnemy = offerEnemyChoice(stage);
            }

            printPreBattleInfo(currentEnemy);
            input.pressEnter();

            player.resetAllSpells();

            boolean playerWon = battleEngine.runBattle(currentEnemy);

            if (playerWon) {
                EnemyShip defeatedEnemy = (EnemyShip) currentEnemy;
                player.addBounty(defeatedEnemy.getBountyReward());
                List<String> levelUps = player.grantXpToAll(defeatedEnemy.getXpReward());
                printVictory(defeatedEnemy, levelUps);
                input.pressEnter();
                handleReward();
                stage++;
            } else {
                printGameOver();
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // ENEMY CHOICE
    // -------------------------------------------------------------------------

    private Ship offerEnemyChoice(int stage) {
        EnemyShip[] options = new EnemyShip[3];
        options[0] = generateEnemy(stage);
        options[1] = generateEnemyDifferentElement(stage, options[0].getElement());
        options[2] = generateEliteEnemy(stage);

        System.out.println("  Pilih musuh yang akan dihadapi:");
        System.out.println();

        for (int i = 0; i < 3; i++) {
            System.out.printf("  [%d] %s%n", i + 1, options[i].getFullDescription());
            printCounterHint(options[i]);
            System.out.println();
        }

        System.out.print("  Pilihan (1-3): ");
        int choice = input.readInt(1, 3, 1);
        System.out.println();
        return options[choice - 1];
    }

    private void printCounterHint(EnemyShip enemy) {
        Element counter       = enemy.getElement().weakness();
        boolean hasCounter    = player.hasCounterMage(enemy.getElement());
        int     synergyCount  = player.countMageByElement(counter);

        System.out.printf("     Counter: %s", counter.sym());

        if (hasCounter) {
            System.out.print(" | Anda punya Mage counter!");
            if (synergyCount >= 2) {
                System.out.printf(" + SINERGY +%d%%", synergyCount >= 3 ? 40 : 20);
            }
        } else {
            System.out.print(" | (Tidak punya Mage counter)");
        }

        if (enemy.getTrait() != EnemyTrait.NONE) {
            System.out.printf(" | Sifat: %s", enemy.getTrait().description);
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // ENEMY GENERATION
    // -------------------------------------------------------------------------

    private EnemyShip generateEnemy(int stage) {
        int hp     = BalanceConfig.BASE_ENEMY_HP  + stage * BalanceConfig.HP_SCALE;
        int damage = BalanceConfig.BASE_ENEMY_DMG + stage * BalanceConfig.DMG_SCALE;
        int bounty = BalanceConfig.BASE_BOUNTY    + stage * BalanceConfig.BOUNTY_SCALE;
        int xp     = BalanceConfig.BASE_XP        + stage * BalanceConfig.XP_SCALE;
        return new EnemyShip(randomEnemyName(), hp, damage,
                randomElement(), bounty, xp, randomTrait());
    }

    private EnemyShip generateEnemyDifferentElement(int stage, Element exclude) {
        int hp     = (int)((BalanceConfig.BASE_ENEMY_HP  + stage * BalanceConfig.HP_SCALE)     * 1.15);
        int damage =        BalanceConfig.BASE_ENEMY_DMG + stage * BalanceConfig.DMG_SCALE;
        int bounty = (int)((BalanceConfig.BASE_BOUNTY    + stage * BalanceConfig.BOUNTY_SCALE)  * 1.30);
        int xp     = (int)((BalanceConfig.BASE_XP        + stage * BalanceConfig.XP_SCALE)      * 1.15);

        Element element;
        do { element = randomElement(); } while (element == exclude);

        return new EnemyShip(randomEnemyName(), hp, damage,
                element, bounty, xp, randomTrait());
    }

    private EnemyShip generateEliteEnemy(int stage) {
        int hp     = (int)((BalanceConfig.BASE_ENEMY_HP  + stage * BalanceConfig.HP_SCALE)     * 1.40);
        int damage = (int)((BalanceConfig.BASE_ENEMY_DMG + stage * BalanceConfig.DMG_SCALE)    * 1.20);
        int bounty = (int)((BalanceConfig.BASE_BOUNTY    + stage * BalanceConfig.BOUNTY_SCALE)  * 1.80);
        int xp     = (int)((BalanceConfig.BASE_XP        + stage * BalanceConfig.XP_SCALE)      * 1.50);
        return new EnemyShip(randomEnemyName() + " [ELITE]", hp, damage,
                randomElement(), bounty, xp, randomTrait());
    }

    private BossShip generateBoss(int stage) {
        int baseHp  = BalanceConfig.BASE_ENEMY_HP  + stage * BalanceConfig.HP_SCALE;
        int baseDmg = BalanceConfig.BASE_ENEMY_DMG + stage * BalanceConfig.DMG_SCALE;
        int hp      = (int)(baseHp  * BalanceConfig.BOSS_HP_MULT);
        int damage  = (int)(baseDmg * BalanceConfig.BOSS_DMG_MULT);
        int bounty  = BalanceConfig.BASE_BOUNTY + stage * BalanceConfig.BOUNTY_SCALE
                    + BalanceConfig.BOSS_BOUNTY_BON;
        int xp      = (BalanceConfig.BASE_XP + stage * BalanceConfig.XP_SCALE) * 2;

        return new BossShip(
            randomEnemyName() + " [BOSS]",
            BOSS_TITLES[rng.nextInt(BOSS_TITLES.length)],
            hp, damage, randomElement(), bounty, xp, randomTrait()
        );
    }

    private EnemyTrait randomTrait() {
        EnemyTrait[] traits = EnemyTrait.values();
        int roll = rng.nextInt(traits.length + traits.length - 1);
        return roll < traits.length ? traits[roll] : EnemyTrait.NONE;
    }

    // -------------------------------------------------------------------------
    // REWARD SYSTEM
    // -------------------------------------------------------------------------

    private void handleReward() {
        List<RewardCard> cards = RewardCard.drawThree(rng, player);

        System.out.println();
        System.out.println("  +--------------------------------------------------+");
        System.out.println("  |              PILIH REWARD                        |");
        System.out.println("  +--------------------------------------------------+");
        for (int i = 0; i < cards.size(); i++) {
            System.out.printf("  [%d] %s%n", i + 1, cards.get(i).getDisplay());
        }
        System.out.print("  Pilihan (1-3): ");

        int        choice     = input.readInt(1, 3, 1);
        RewardCard chosenCard = cards.get(choice - 1);
        System.out.println();
        applyReward(chosenCard);
        System.out.println();
        input.pressEnter();
    }

    private void applyReward(RewardCard card) {
        switch (card.getType()) {
            case RECRUIT_MAGE: {
                if (player.getMageCount() >= 5) {
                    System.out.println("  Roster penuh! Diganti Heal Kecil.");
                    player.heal(45); break;
                }
                Mage newMage = new Mage(randomMageName(), randomElement(),
                        18 + rng.nextInt(18), rng);
                player.recruitMage(newMage);
                System.out.printf("  Mage baru bergabung: %s%n", newMage.getInfo(true));
                System.out.printf("  Jurus: %s%n", newMage.getSpellType().getFullDescription());
                int count = player.countMageByElement(newMage.getElement());
                if (count >= 2) System.out.printf(
                        "  [SINERGY TERBENTUK!] %dx %s -> +%d%% magic!%n",
                        count, newMage.getElement().sym(), count >= 3 ? 40 : 20);
                break;
            }
            case HEAL_SMALL: {
                int before = player.getCurrentHp();
                player.heal(45);
                System.out.printf("  Perbaikan cepat! HP: %d -> %d (+%d)%n",
                        before, player.getCurrentHp(), player.getCurrentHp() - before);
                break;
            }
            case HEAL_LARGE: {
                int before = player.getCurrentHp();
                player.heal(80);
                System.out.printf("  Perbaikan total! HP: %d -> %d (+%d)%n",
                        before, player.getCurrentHp(), player.getCurrentHp() - before);
                break;
            }
            case UPGRADE_CANNON: {
                int before = player.getBaseDamage();
                player.upgradeBaseDamage(7);
                System.out.printf("  Meriam di-upgrade! Damage: %d -> %d%n",
                        before, player.getBaseDamage());
                break;
            }
            case UPGRADE_MAGE_POWER: {
                if (player.getRoster().isEmpty()) {
                    System.out.println("  Tidak ada Mage! Diganti Heal Kecil.");
                    player.heal(45); break;
                }
                System.out.println("  Pilih Mage yang di-upgrade:");
                player.displayMageRoster(false);
                System.out.print("  Nomor: ");
                int  idx  = input.readInt(1, player.getMageCount(), 1) - 1;
                Mage mage = player.getRoster().get(idx);
                int before = mage.getMagicPower();
                mage.upgradePower(18);
                System.out.printf("  %s di-upgrade! Power: %d -> %d%n",
                        mage.getName(), before, mage.getMagicPower());
                break;
            }
            case ADD_POTION: {
                if (!player.addPotion()) {
                    System.out.println("  Potion penuh! Diganti Heal Kecil.");
                    player.heal(45);
                } else {
                    System.out.printf("  +1 Potion! Sisa: %d/3%n", player.getPotions());
                }
                break;
            }
            case ADD_EXPLOSIVE:
                player.addAmmo(CannonballType.EXPLOSIVE, 3);
                System.out.printf("  +3 Peluru Ledak! Stok: %d%n",
                        player.getAmmoCount(CannonballType.EXPLOSIVE));
                break;
            case ADD_CHAIN:
                player.addAmmo(CannonballType.CHAIN, 3);
                System.out.printf("  +3 Peluru Rantai! Stok: %d%n",
                        player.getAmmoCount(CannonballType.CHAIN));
                break;
            case ADD_GRAPESHOT:
                player.addAmmo(CannonballType.GRAPESHOT, 3);
                System.out.printf("  +3 Peluru Angin! Stok: %d%n",
                        player.getAmmoCount(CannonballType.GRAPESHOT));
                break;
            case UPGRADE_ALL_MAGE_SMALL:
                for (Mage m : player.getRoster()) m.upgradePower(5);
                System.out.println("  Ritual Kolektif! Semua Mage +5 Magic Power.");
                break;
            case DOUBLE_CANNON_DMG:
                cannonDoubled.set(true);
                System.out.println("  [LANGKA] Kapal Berkibar! Cannon damage x2 di battle berikutnya.");
                break;
        }
    }

    // -------------------------------------------------------------------------
    // UI / DISPLAY
    // -------------------------------------------------------------------------

    private void printStageHeader() {
        System.out.println();
        System.out.println("============================================================");
        String bossTag = (stage % 5 == 0) ? "  *** BOSS BATTLE! ***" : "";
        System.out.printf("  STAGE %d%s%n", stage, bossTag);
        System.out.println("============================================================");
        System.out.println(player.getStatusDisplay());

        System.out.printf("  Amunisi: Iron(inf) | Explosive: %d | Chain: %d | Grapeshot: %d%n",
                player.getAmmoCount(CannonballType.EXPLOSIVE),
                player.getAmmoCount(CannonballType.CHAIN),
                player.getAmmoCount(CannonballType.GRAPESHOT));

        for (Element el : Element.values()) {
            int count = player.countMageByElement(el);
            if (count >= 2) {
                System.out.printf("  [SINERGY] %dx %s Mage -> +%d%% magic damage%n",
                        count, el.sym(), count >= 3 ? 40 : 20);
            }
        }

        System.out.println("  Kru Mage:");
        player.displayMageRoster(false);
        System.out.println();
    }

    private void printPreBattleInfo(Ship enemy) {
        System.out.println("  -- SIAP TEMPUR --");
        System.out.println(enemy.getStatusDisplay());
        System.out.println();

        if (enemy instanceof EnemyShip) {
            EnemyTrait trait = ((EnemyShip) enemy).getTrait();
            if (trait == EnemyTrait.ARMORED)
                System.out.println("  [TIP] BERZIRAH: Utamakan sihir Mage daripada cannon.");
            if (trait == EnemyTrait.REGENERATE)
                System.out.println("  [TIP] REGENERASI: Burst damage dalam sedikit turn lebih efektif.");
            if (trait == EnemyTrait.BERSERKER)
                System.out.println("  [TIP] BERSERKER: Bunuh sebelum HP-nya turun ke 50%!");
            if (trait == EnemyTrait.THORNS)
                System.out.println("  [TIP] BERDURI: 15% sihir dipantulkan -- INFERNO atau cannon lebih aman.");
        }

        Element counter = enemy.getElement().weakness();
        System.out.printf("  Counter elemen: %s%n", counter.sym());
        if (player.hasCounterMage(enemy.getElement()))
            System.out.println("  [GOOD] Anda punya Mage counter!");
    }

    private void printVictory(EnemyShip enemy, List<String> levelUps) {
        System.out.println();
        System.out.println("  +=======================================================+");
        System.out.printf( "  | MENANG! %s tenggelam!%n", enemy.getName());
        System.out.printf( "  | Bounty: +$%d | XP Mage: +%d | Total: $%d%n",
                enemy.getBountyReward(), enemy.getXpReward(), player.getBounty());
        System.out.println("  +=======================================================+");
        for (String msg : levelUps) System.out.println(msg);
    }

    private void printGameOver() {
        System.out.println();
        System.out.println("  +=======================================================+");
        System.out.println("  |                    GAME OVER                          |");
        System.out.println("  +=======================================================+");
        System.out.printf( "  Kapal tenggelam di Stage %d.%n", stage);
        System.out.printf( "  Total Bounty: $%d%n", player.getBounty());
        System.out.println();
        System.out.println("  Kru terakhir:");
        player.displayMageRoster(false);
        System.out.println();
        System.out.println("  Terima kasih sudah bermain! Arr, sampai jumpa di lautan!");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private Element randomElement()   { return Element.values()[rng.nextInt(3)]; }
    private String  randomEnemyName() { return ENEMY_NAMES[rng.nextInt(ENEMY_NAMES.length)]; }
    private String  randomMageName()  { return MAGE_NAMES[rng.nextInt(MAGE_NAMES.length)]; }
}
