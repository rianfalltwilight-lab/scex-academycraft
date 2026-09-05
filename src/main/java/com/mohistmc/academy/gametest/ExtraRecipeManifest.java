package com.mohistmc.academy.gametest;

import java.util.List;
import java.util.stream.Stream;

/**
 * Independently audited ExtraAcC compatibility recipe IDs, shared by execution
 * tests and the real-client JEI gate. This list is not generated from recipe JSON.
 */
public final class ExtraRecipeManifest {
    private ExtraRecipeManifest() {}

    public static final List<String> CRAFTING = List.of(
            "academy:extra_air_jet",
            "academy:extra_avalon",
            "academy:extra_drop_item_magnet",
            "academy:extra_electricalibur",
            "academy:extra_energy_unit_group",
            "academy:extra_imag_boots",
            "academy:extra_imag_chestplate",
            "academy:extra_imag_helmet",
            "academy:extra_imag_leggings",
            "academy:extra_lasor_component",
            "academy:extra_lasor_gun",
            "academy:extra_optical_chip",
            "academy:extra_paper_boots",
            "academy:extra_paper_chestplate",
            "academy:extra_paper_helmet",
            "academy:extra_paper_leggings",
            "academy:extra_paper_plane",
            "academy:extra_ray_twister",
            "academy:extra_reso_boots",
            "academy:extra_reso_chestplate",
            "academy:extra_reso_helmet",
            "academy:extra_reso_leggings",
            "academy:extra_teleporter");
    public static final List<String> IMAG_FUSOR = List.of(
            "academy:extra_cp_potion",
            "academy:extra_fuse_constraint_ingot",
            "academy:extra_fuse_crystal_low",
            "academy:extra_fuse_imag_silicon",
            "academy:extra_fuse_reso_crystal");
    public static final List<String> METAL_FORMING = List.of(
            "academy:extra_etched_cobblestone");
    public static final List<String> ALL =
            Stream.of(CRAFTING, IMAG_FUSOR, METAL_FORMING).flatMap(List::stream).toList();
}