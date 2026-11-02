package com.Infinity.Nexus.Multiblock.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final int DEFAULT_PATTERN_SCAN_LIMIT = 256;
    public static final int MAX_PATTERN_SCAN_LIMIT = 4096;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue PATTERN_SCAN_LIMIT;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("export");
        PATTERN_SCAN_LIMIT = BUILDER
                .comment("Maximum number of connected non-air blocks scanned when exporting a multiblock pattern.")
                .defineInRange("pattern_scan_limit", DEFAULT_PATTERN_SCAN_LIMIT, 1, MAX_PATTERN_SCAN_LIMIT);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
