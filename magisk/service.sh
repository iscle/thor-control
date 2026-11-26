#!/system/bin/sh
# Thor Control supervisor. Runs as root (late_start service) and keeps thorctld alive forever.
MODDIR=${0%/*}
DATA=/data/adb/thorctl
LOG=$DATA/daemon.log
PKG=com.thorctl

mkdir -p $DATA
chmod 0700 $DATA

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 1; done

# Gamepad mode over Bluetooth (registering as a HID device, the "make visible" prompt).
pm grant $PKG android.permission.BLUETOOTH_CONNECT 2>/dev/null
pm grant $PKG android.permission.BLUETOOTH_ADVERTISE 2>/dev/null
# The fallback overlay layer, used while the accessibility service is off.
appops set $PKG SYSTEM_ALERT_WINDOW allow 2>/dev/null
# The overlays draw in the accessibility service's layer: above the status bar and the keyboard,
# and out of reach of "hide overlays". Turned on at boot if it isn't; turning it off is up to you.
A11Y=$PKG/$PKG.app.ThorAccessibilityService
ENABLED=$(settings get secure enabled_accessibility_services 2>/dev/null)
case ":$ENABLED:" in
  *":$A11Y:"*) ;;
  ::|:null:) settings put secure enabled_accessibility_services "$A11Y" && settings put secure accessibility_enabled 1 ;;
  *) settings put secure enabled_accessibility_services "$ENABLED:$A11Y" && settings put secure accessibility_enabled 1 ;;
esac
# Thor Keyboard stays available in the keyboard list (choosing it as the keyboard is up to you).
ime enable $PKG/.app.ThorKeyboard >/dev/null 2>&1

# Cocoon only lists LineageOS apps once they are "updated" system apps (see unhide-apps.sh).
sh "$MODDIR/unhide-apps.sh" >> "$LOG" 2>&1 &

# Memory savings: unused services stopped, Trebuchet off, zstd swap (see system-tweaks.sh).
sh "$MODDIR/system-tweaks.sh" apply >> "$LOG" 2>&1 &

while true; do
  # Module disabled or being removed from the Magisk app (thorctld exits when it sees the flag):
  # hand the fan, clocks, apps and services back and stop.
  if [ -f "$MODDIR/disable" ] || [ -f "$MODDIR/remove" ]; then
    sh "$MODDIR/restore-kernel.sh"
    sh "$MODDIR/system-tweaks.sh" revert >> "$LOG" 2>&1
    exit 0
  fi

  # Prefer the active package (an adb-installed update lives in /data/app).
  APK=$(pm path $PKG 2>/dev/null | head -n1 | cut -d: -f2)
  [ -f "$APK" ] || APK=$MODDIR/system/app/ThorControl/ThorControl.apk

  if [ -f "$LOG" ] && [ "$(stat -c %s "$LOG")" -gt 1048576 ]; then mv -f "$LOG" "$LOG.old"; fi
  echo "$(date '+%F %T') starting thorctld from $APK" >> "$LOG"

  CLASSPATH=$APK /system/bin/app_process /system/bin --nice-name=thorctld com.thorctl.daemon.Main >> "$LOG" 2>&1
  echo "$(date '+%F %T') thorctld exited ($?), restoring kernel defaults" >> "$LOG"

  sh "$MODDIR/restore-kernel.sh"
  sleep 2
done
