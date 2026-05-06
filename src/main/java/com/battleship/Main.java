package com.battleship;

import com.battleship.system.GameManager;

/**
 * Entry point program. Hanya bertugas membuat GameManager dan memulai game.
 */
public class Main {
    public static void main(String[] args) {
        new GameManager().start();
    }
}
