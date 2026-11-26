#!/system/bin/sh
# Memory savings applied after boot, and undone when the module is disabled or removed.
#
#   apply   service.sh, every boot (after boot_completed)
#   revert  service.sh, when the module is disabled/removed from the Magisk app
#
# File pinning is switched off by the framework overlay in /product/overlay.
MODE=${1:-apply}

LAUNCHER3=com.android.launcher3 # Trebuchet: only the gesture-nav / recents provider here
SERVICES="cameraserver wifidisplayhalservice" # no cameras; Wi-Fi Display (Miracast) casting

disabled() { pm list packages -d 2>/dev/null | grep -qx "package:$1"; }

# Another launcher must be able to take Home, or the device would have none.
other_home() {
  cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null |
    grep '/' | grep -v -e "$LAUNCHER3/" -e 'com.android.settings/' | grep -q .
}

ZRAM=/sys/block/zram0
ZRAM_SAVED=/data/adb/thorctl/zram-algorithm # the algorithm Android set up, for revert

# The algorithm in use (the one in brackets).
zram_current() { sed -n 's/.*\[\([^]]*\)\].*/\1/p' $ZRAM/comp_algorithm; }

# Rebuilds the swap with algorithm $1, keeping its size and priority.
zram_use() {
  [ -f $ZRAM/comp_algorithm ] || return 1
  grep -qw "$1" $ZRAM/comp_algorithm || return 1
  [ "$(zram_current)" = "$1" ] && return 0
  size=$(cat $ZRAM/disksize)
  prio=$(awk '$1 == "/dev/block/zram0" { print $5 }' /proc/swaps)
  [ "$size" -gt 0 ] 2>/dev/null || return 1
  # swapoff pulls swapped pages back into RAM; skip if that fails (memory pressure).
  swapoff /dev/block/zram0 2>/dev/null || return 1
  echo 1 > $ZRAM/reset
  echo "$1" > $ZRAM/comp_algorithm
  echo "$size" > $ZRAM/disksize
  mkswap /dev/block/zram0 >/dev/null 2>&1
  swapon -p "${prio:-32758}" /dev/block/zram0 2>/dev/null || swapon /dev/block/zram0
  echo "system-tweaks: zram now $(zram_current)"
}

case "$MODE" in
  apply)
    if [ ! -f /data/adb/thorctl/keep-top-gestures ] && other_home; then
      disabled $LAUNCHER3 || { pm disable $LAUNCHER3 >/dev/null 2>&1 && echo "system-tweaks: disabled $LAUNCHER3"; }
    elif disabled $LAUNCHER3; then
      pm enable $LAUNCHER3 >/dev/null 2>&1 && echo "system-tweaks: re-enabled $LAUNCHER3"
    fi
    for s in $SERVICES; do
      [ "$(getprop init.svc.$s)" = running ] && stop "$s" && echo "system-tweaks: stopped $s"
    done
    if [ -f $ZRAM/comp_algorithm ] && [ "$(zram_current)" != zstd ]; then
      before=$(zram_current)
      zram_use zstd && echo "$before" > $ZRAM_SAVED
    fi
    ;;
  revert)
    disabled $LAUNCHER3 && pm enable $LAUNCHER3 >/dev/null 2>&1
    for s in $SERVICES; do
      [ "$(getprop init.svc.$s)" = stopped ] && start "$s"
    done
    if [ -f $ZRAM_SAVED ]; then
      zram_use "$(cat $ZRAM_SAVED)" && rm -f $ZRAM_SAVED
    fi
    echo "system-tweaks: reverted"
    ;;
esac
