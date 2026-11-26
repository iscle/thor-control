package com.thorctl.protocol.ipc;

parcelable StatsParcel {
    float cpuLoad;
    int cpuFreqMhz;
    int[] clusterFreqsMhz;
    float cpuTemp;
    float gpuLoad;
    int gpuFreqMhz;
    float gpuTemp;
    int ramTotalMb;
    int ramAvailMb;
    int batteryLevel;
    String batteryStatus;
    float batteryWatts;
    float inputWatts;
    float batteryTemp;
    int fanPwm;
    float fanTemp;
}
