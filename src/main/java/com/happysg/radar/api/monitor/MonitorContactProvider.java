package com.happysg.radar.api.monitor;

import java.util.List;

public interface MonitorContactProvider {
    List<MonitorContactSnapshot> getMonitorContacts();
}