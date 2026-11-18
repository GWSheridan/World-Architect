package com.gwsheridan.worldarchitect.config;

public record TerrainShape(int elevation, int relief) {
    public static final int MIN_ELEVATION = -2032;
    public static final int MAX_ELEVATION = 2028;
    public static final int MAX_RELIEF = 80;

    public static final TerrainShape FLAT = new TerrainShape(68, 0);
    public static final TerrainShape HILLS = new TerrainShape(68, 44);
    public static final TerrainShape HIGHLANDS = new TerrainShape(108, 72);
}
