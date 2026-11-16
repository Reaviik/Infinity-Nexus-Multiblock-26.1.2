package com.Infinity.Nexus.Multiblock.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final int DEFAULT_PATTERN_SCAN_LIMIT = 256;
    public static final int MAX_PATTERN_SCAN_LIMIT = 4096*10;
    public static final int DEFAULT_ASSEMBLY_BLOCKS_PER_TICK = 32;
    public static final int DEFAULT_ASSEMBLY_MAX_FAILED_SCANS_PER_TICK = 10;
    public static final int DEFAULT_ASSEMBLY_BLOCKED_HIGHLIGHT_SECONDS = 300;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue PATTERN_SCAN_LIMIT;
    public static final ModConfigSpec.IntValue ASSEMBLY_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue ASSEMBLY_MAX_FAILED_SCANS_PER_TICK;
    public static final ModConfigSpec.IntValue ASSEMBLY_BLOCKED_HIGHLIGHT_SECONDS;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("export");
        PATTERN_SCAN_LIMIT = BUILDER
                .comment("Maximum number of connected non-air blocks scanned when exporting a multiblock pattern.")
                .defineInRange("pattern_scan_limit", DEFAULT_PATTERN_SCAN_LIMIT, 1, MAX_PATTERN_SCAN_LIMIT);
        BUILDER.pop();

        BUILDER.push("assembly");
        ASSEMBLY_BLOCKS_PER_TICK = BUILDER
                .comment("Maximum number of blocks placed per server tick while assembling a multiblock.")
                .defineInRange("blocks_per_tick", DEFAULT_ASSEMBLY_BLOCKS_PER_TICK, 1, 4096);
        ASSEMBLY_MAX_FAILED_SCANS_PER_TICK = BUILDER
                .comment("Maximum number of cells without a matching item scanned per tick before waiting for the next tick.")
                .defineInRange("max_failed_scans_per_tick", DEFAULT_ASSEMBLY_MAX_FAILED_SCANS_PER_TICK, 1, 256);
        ASSEMBLY_BLOCKED_HIGHLIGHT_SECONDS = BUILDER
                .comment("Seconds to keep blocked structure positions highlighted in red after an assembly attempt.")
                .defineInRange("blocked_highlight_seconds", DEFAULT_ASSEMBLY_BLOCKED_HIGHLIGHT_SECONDS, 1, 3600);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
