#!/system/bin/sh
sh "${0%/*}/restore-kernel.sh"
sh "${0%/*}/sensor-orient.sh" revert
rm -rf /data/adb/thorctl

