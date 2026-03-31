package com.navasmart.vda5050.proxy.callback;

import com.navasmart.vda5050.model.Factsheet;

public interface Vda5050ProxyStateProvider {

    VehicleStatus getVehicleStatus(String vehicleId);

    Factsheet getFactsheet(String vehicleId);
}
