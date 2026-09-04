package com.mohistmc.academy.energy.impl;

/** Fail-closed numeric boundary for persisted state and third-party wireless implementations. */
public final class EnergyBoundary {
    /** Legacy machines peak below 20k/500 IF-t; these leave ample integration headroom. */
    public static final double MAX_ENERGY = 1_000_000_000D;
    public static final double MAX_TRANSFER = 1_000_000D;
    public static final int MAX_CONNECTIONS = 1024;
    public static final double MAX_NETWORK_ENERGY = MAX_ENERGY * MAX_CONNECTIONS;
    private EnergyBoundary() {}
    public static double nonNegative(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
    }
    public static double bounded(double value, double maximum) {
        double max = nonNegative(maximum);
        return Math.min(nonNegative(value), max);
    }
    public static double energy(double value) { return Math.min(nonNegative(value), MAX_ENERGY); }
    public static double transfer(double value) { return Math.min(nonNegative(value), MAX_TRANSFER); }
    public static int capacity(int value) { return Math.clamp(value, 0, MAX_CONNECTIONS); }
    public static double saturatedAdd(double left, double right, double maximum) {
        double max = nonNegative(maximum);
        double a = Math.min(nonNegative(left), max), b = Math.min(nonNegative(right), max);
        return b >= max - a ? max : a + b;
    }
    public static double finiteRatio(double numerator, double denominator) {
        double den = nonNegative(denominator);
        if (den == 0) return 0;
        return Math.clamp(Math.min(nonNegative(numerator), MAX_NETWORK_ENERGY)
                / Math.min(den, MAX_NETWORK_ENERGY), 0, 1);
    }
}
