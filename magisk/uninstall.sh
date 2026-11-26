#!/system/bin/sh
sh "${0%/*}/restore-kernel.sh"
sh "${0%/*}/sensor-orient.sh" revert
rm -rf /data/adb/thorctl

# Removing the module normally goes through the Magisk app, where thorctld sees the flag and
# service.sh reverts everything while Android runs. If this runs at early boot instead, give the
# disabled apps back once the package manager is up (the module files are gone by then).
(
  until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
  pm list packages -d | grep -qx "package:com.android.launcher3" && pm enable com.android.launcher3
) >/dev/null 2>&1 &
