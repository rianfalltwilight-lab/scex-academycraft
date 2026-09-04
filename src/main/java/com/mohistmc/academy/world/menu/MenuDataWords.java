package com.mohistmc.academy.world.menu;

/** Pure word codec for values synchronized through vanilla signed-short menu data packets. */
final class MenuDataWords {
    private MenuDataWords() {}

    static int low(int value) { return value & 0xffff; }
    static int high(int value) { return (value >>> 16) & 0xffff; }
    static int join(int low, int high) {
        return (low & 0xffff) | ((high & 0xffff) << 16);
    }
}
