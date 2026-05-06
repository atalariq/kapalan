package com.battleship.engine;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import com.battleship.model.*;
import com.battleship.model.enums.*;
import com.battleship.util.InputHelper;

/**
 * Mengelola seluruh logika pertempuran (battle loop + player actions).
 * Dipisahkan dari GameManager agar logika battle tidak tercampur dengan
 * logika orchestrator/UI flow.
 */
public class BattleEngine {

    private final PlayerShip   player;
    private final InputHelper  input;
    private final Random       rng;
    private final AtomicBoolean cannonDoubled;

    public BattleEngine(PlayerShip player, InputHelper input, Random rng,
                        AtomicBoolean cannonDoubled) {
        this.player         = player;
        this.input          = input;
        this.rng            = rng;
        this.cannonDoubled  = cannonDoubled;
    }

    /**
     * Jalankan satu pertempuran penuh antara player vs enemy.
     * @return true jika pemain menang, false jika kalah.
     */
    public boolean runBattle(Ship enemy) {
        boolean playerTurn = true;

        while (player.isAlive() && enemy.isAlive()) {

            if (playerTurn) {
                String statusLog = player.processStatus();
                if (!statusLog.isEmpty()) System.out.print(statusLog);

                if (player.isFrozen()) {
                    System.out.println("  [FROZEN] Anda membeku! Giliran dilewati.");
                    player.applyStatus(StatusEffect.NONE, 0);
                    playerTurn = !playerTurn;
                    input.pressEnter();
                    continue;
                }
                player.resetShield();

            } else {
                String statusLog = enemy.processStatus();
                if (!statusLog.isEmpty()) System.out.print(statusLog);

                if (enemy.isFrozen()) {
                    System.out.printf("  [FROZEN] %s membeku! Giliran dilewati.%n", enemy.getName());
                    enemy.applyStatus(StatusEffect.NONE, 0);
                    playerTurn = !playerTurn;
                    if (!enemy.isAlive()) break;
                    input.pressEnter();
                    continue;
                }
            }

            if (!enemy.isAlive() || !player.isAlive()) break;

            printBattleStatus(enemy);

            if (playerTurn) {
                doPlayerTurn(enemy);
            } else {
                printDivider();
                System.out.printf("  GILIRAN MUSUH: %s%n", enemy.getName());
                printDivider();
                enemy.takeTurn(player);
                if (player.isShielded()) {
                    System.out.println("  [SHIELD aktif -- damage dipotong 50%!]");
                }
            }

            playerTurn = !playerTurn;
            if (!enemy.isAlive() || !player.isAlive()) break;
            System.out.println();
            input.pressEnter();
        }

        return player.isAlive();
    }

    /** Kelola semua input dan aksi pada giliran player. */
    public void doPlayerTurn(Ship enemy) {
        printDivider();
        System.out.println("  GILIRAN ANDA");
        printDivider();
        System.out.println("  [1] Tembak Meriam   (pilih tipe peluru)");
        System.out.println("  [2] Sihir Mage      (elemen + sinergy)");
        System.out.println("  [3] Gunakan Jurus   (1x per battle!)");
        System.out.printf( "  [4] Bertahan        (-50%% damage masuk turn ini)%n");
        System.out.printf( "  [5] Minum Potion    (heal 50 HP | Sisa: %d)%n", player.getPotions());
        System.out.print("  Pilihan (1-5): ");

        int choice = input.readInt(1, 5, 1);
        System.out.println();

        switch (choice) {
            case 1: doCannonball(enemy);   break;
            case 2: doMagicAttack(enemy);  break;
            case 3: doSpellAttack(enemy);  break;
            case 4:
                player.activateShield();
                System.out.println("  Kapal bersiap bertahan! Damage masuk -50% turn ini.");
                break;
            case 5:
                if (!player.usePotion()) {
                    System.out.println("  Potion habis! Aksi dilewati.");
                } else {
                    System.out.printf("  Minum potion! HP: %d/%d | Sisa: %d%n",
                            player.getCurrentHp(), player.getMaxHp(), player.getPotions());
                }
                break;
        }
    }

    /** Aksi [1]: Tembak Meriam — pemain memilih tipe peluru. */
    public void doCannonball(Ship enemy) {
        System.out.println("  Pilih tipe peluru:");
        CannonballType[] types = CannonballType.values();

        for (int i = 0; i < types.length; i++) {
            CannonballType type = types[i];
            String stock = (type == CannonballType.IRON)
                    ? "tak terbatas"
                    : "stok: " + player.getAmmoCount(type);
            System.out.printf("  [%d] %s | %s%n",
                    i + 1, type.getFullDescription(), stock);
        }

        if (enemy instanceof EnemyShip) {
            EnemyTrait trait = ((EnemyShip) enemy).getTrait();
            if (trait == EnemyTrait.ARMORED)
                System.out.println("  [TIP] Musuh BERZIRAH -- cannon berkurang 35%, pertimbangkan sihir.");
            if (trait == EnemyTrait.REGENERATE)
                System.out.println("  [TIP] Musuh REGENERASI -- burst damage lebih efektif dari DoT.");
        }

        if (cannonDoubled.get()) System.out.println("  [KAPAL BERKIBAR] Cannon damage x2 aktif!");

        System.out.print("  Pilih (1-4): ");
        int choice = input.readInt(1, 4, 1);
        CannonballType chosen = types[choice - 1];

        int damage = player.fireCannonball(chosen, enemy);

        if (damage < 0) {
            System.out.printf("  Stok %s habis! Otomatis pakai Peluru Besi.%n",
                    chosen.getDisplayName());
            damage = player.fireCannonball(CannonballType.IRON, enemy);
            chosen = CannonballType.IRON;
        }

        if (enemy instanceof EnemyShip) {
            damage = ((EnemyShip) enemy).applyArmorReduction(damage, true);
        }

        if (cannonDoubled.get()) { damage *= 2; cannonDoubled.set(false); }

        System.out.printf("  Menembakkan %s! Damage: %d%n", chosen.getDisplayName(), damage);
        if (chosen.appliesBurn)
            System.out.printf("  %s terbakar! BURNED 3 turn aktif.%n", enemy.getName());
        if (chosen.appliesWeak)
            System.out.printf("  %s terjerat! WEAKENED 3 turn aktif.%n", enemy.getName());
    }

    /** Aksi [2]: Sihir Mage — pilih Mage, hitung damage elemen + sinergy. */
    public void doMagicAttack(Ship enemy) {
        if (player.getRoster().isEmpty()) {
            System.out.println("  Tidak ada Mage! Tembak meriam Iron otomatis.");
            int dmg = player.fireCannonball(CannonballType.IRON, enemy);
            System.out.printf("  Damage: %d%n", dmg);
            return;
        }

        System.out.println("  Pilih Mage:");
        player.displayMageRoster(false);
        System.out.printf("  Elemen musuh: %-5s | Counter: %s%n",
                enemy.getElement().sym(), enemy.getElement().weakness().sym());
        printSynergyStatus();
        System.out.print("  Nomor Mage: ");

        int  mageIndex = input.readInt(1, player.getMageCount(), 1) - 1;
        Mage mage      = player.getRoster().get(mageIndex);

        double elemMult    = mage.getElement().getMultiplier(enemy.getElement());
        double synergyMult = player.getSynergyMult(mage.getElement());

        int damage = player.castMagic(enemy, mage);

        if (enemy instanceof EnemyShip && ((EnemyShip) enemy).getTrait() == EnemyTrait.THORNS) {
            int reflected = (int)(damage * 0.15);
            player.takeDamage(reflected);
            System.out.printf("  [BERDURI] %d damage dipantulkan ke Anda!%n", reflected);
        }

        System.out.printf("  %s melepas sihir %s!%n", mage.getName(), mage.getElement().sym());
        System.out.printf("  %s%n", mage.getElement().effectText(enemy.getElement()));
        if (synergyMult > 1.0)
            System.out.printf("  [SINERGY +%d%%!]%n", (int)((synergyMult - 1.0) * 100));
        System.out.printf("  Damage: %d  (x%.1f elem, x%.2f sinergy)%n",
                damage, elemMult, synergyMult);
    }

    /** Aksi [3]: Gunakan Jurus — efek spesial, 1x per battle per Mage. */
    public void doSpellAttack(Ship enemy) {
        if (player.getRoster().isEmpty()) {
            System.out.println("  Tidak ada Mage! Tembak meriam Iron otomatis.");
            int dmg = player.fireCannonball(CannonballType.IRON, enemy);
            System.out.printf("  Damage: %d%n", dmg);
            return;
        }

        System.out.println("  Pilih Mage untuk Jurus (READY = bisa dipakai):");
        player.displayMageRoster(true);
        System.out.print("  Nomor Mage: ");

        int  mageIndex = input.readInt(1, player.getMageCount(), 1) - 1;
        Mage mage      = player.getRoster().get(mageIndex);

        if (mage.isSpellUsed()) {
            System.out.printf("  [!] Jurus %s sudah dipakai! Pilih aksi lain.%n",
                    mage.getSpellType().getDisplayName());
            return;
        }

        System.out.println();
        String spellResult = player.castSpell(enemy, mage);
        System.out.println(spellResult);

        if (enemy instanceof EnemyShip && ((EnemyShip) enemy).getTrait() == EnemyTrait.THORNS) {
            System.out.println("  [BERDURI] Jurus dipantulkan 15% ke Anda -- cek HP!");
        }
    }

    /** Tampilkan sinergy bonus yang sedang aktif di roster. */
    private void printSynergyStatus() {
        for (Element el : Element.values()) {
            int count = player.countMageByElement(el);
            if (count >= 2) {
                System.out.printf("  [SINERGY] %dx %s -> +%d%% magic damage!%n",
                        count, el.sym(), count >= 3 ? 40 : 20);
            }
        }
    }

    public void printBattleStatus(Ship enemy) {
        System.out.println();
        System.out.println("  ------- STATUS PERTEMPURAN ---------------------------");
        System.out.printf( "  PLAYER : %s%s%n", player.getHpBar(),
                player.isShielded() ? " [SHIELD]" : "");
        System.out.printf( "  MUSUH  : %s%n", enemy.getHpBar());
        System.out.println("  -------------------------------------------------------");
    }

    public void printDivider() {
        System.out.println("  -------------------------------------------------------");
    }
}
