#!/system/bin/sh
# Changes to the system applied after boot, and undone when the module is disabled or removed.
#
#   apply   service.sh, every boot (after boot_completed)
#   revert  service.sh, when the module is disabled/removed from the Magisk app
MODE=${1:-apply}
DATA=/data/adb/thorctl

ZRAM=/sys/block/zram0
ZRAM_SAVED=$DATA/zram-algorithm # the algorithm Android set up, for revert

# The algorithm in use (the one in brackets).
zram_current() { sed -n 's/.*\[\([^]]*\)\].*/\1/p' $ZRAM/comp_algorithm; }

# Rebuilds the swap with algorithm $1, keeping its size and priority.
zram_use() {
  [ -f $ZRAM/comp_algorithm ] || return 1
  grep -qw "$1" $ZRAM/comp_algorithm || return 1
  [ "$(zram_current)" = "$1" ] && return 0
  size=$(cat $ZRAM/disksize)
  prio=$(awk '$1 == "/dev/block/zram0" { print $5 }' /proc/swaps)
  # A string check: 4 GiB in bytes overflows the shell's integer test.
  case "$size" in "" | 0 | *[!0-9]*) return 1 ;; esac
  # swapoff pulls swapped pages back into RAM; skip if that fails (memory pressure).
  swapoff /dev/block/zram0 2>/dev/null || return 1
  echo 1 > $ZRAM/reset
  echo "$1" > $ZRAM/comp_algorithm
  echo "$size" > $ZRAM/disksize
  mkswap /dev/block/zram0 >/dev/null 2>&1
  swapon -p "${prio:-32758}" /dev/block/zram0 2>/dev/null || swapon /dev/block/zram0
  echo "system-tweaks: zram now $(zram_current)"
}

# Earlier versions disabled Trebuchet, hid the top navigation bar for it, and installed Jelly and
# Glimpse as updates of themselves (for the Cocoon launcher). None of that is done any more:
# give those back once.
undo_earlier_versions() {
  if pm list packages -d 2>/dev/null | grep -qx "package:com.android.launcher3"; then
    pm enable com.android.launcher3 >/dev/null 2>&1 && echo "system-tweaks: re-enabled Trebuchet (reboot for its gestures)"
  fi
  for f in "$DATA"/unhide/*; do
    [ -f "$f" ] || continue
    pm uninstall-system-updates "${f##*/}" >/dev/null 2>&1 && echo "system-tweaks: removed the ${f##*/} update"
  done
  rm -rf "$DATA/unhide" "$DATA/keep-top-gestures"
}

case "$MODE" in
  apply)
    undo_earlier_versions
    # zstd fits more into the same swap than lzo-rle.
    if [ -f $ZRAM/comp_algorithm ] && [ "$(zram_current)" != zstd ]; then
      before=$(zram_current)
      zram_use zstd && echo "$before" > $ZRAM_SAVED
    fi
    ;;
  revert)
    if [ -f $ZRAM_SAVED ]; then
      zram_use "$(cat $ZRAM_SAVED)" && rm -f $ZRAM_SAVED
    fi
    echo "system-tweaks: reverted"
    ;;
esac
