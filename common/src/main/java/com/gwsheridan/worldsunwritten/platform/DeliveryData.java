package com.gwsheridan.worldsunwritten.platform;

import net.minecraft.nbt.CompoundTag;

public interface DeliveryData {
    String KEY = "worldsunwritten:delivery_data";

    CompoundTag worldsunwritten$deliveryData();
}
