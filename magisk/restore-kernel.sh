#!/system/bin/sh
# Hand the kernel back its defaults whenever thorctld is not running:
#  - fan trip points (saved by thorctld on first start each boot)
#  - CPU/GPU floors raised by game mode (saved before game mode writes them)
#  - the bottom panel's backlight, if gamepad mode turned it off
#  - the Bluetooth Device ID, if Bluetooth gamepad mode changed it (Bluetooth restarts to read it)
F=/dev/thorctl/trips.orig
if [ -f "$F" ]; then
  while read -r path value; do
    [ -n "$path" ] && [ -n "$value" ] && echo "$value" > "$path"
  done < "$F"
fi

G=/dev/thorctl/game.orig
if [ -f "$G" ]; then
  while read -r path value; do
    [ -n "$path" ] && [ -n "$value" ] && echo "$value" > "$path"
  done < "$G"
  rm -f "$G"
fi

L=/dev/thorctl/backlight.orig
if [ -f "$L" ]; then
  read -r node value < "$L"
  [ -n "$node" ] && [ -n "$value" ] && echo "$value" > "$node"
  rm -f "$L"
fi

B=/dev/thorctl/bt-device-id.orig
if [ -f "$B" ]; then
  while IFS='=' read -r name value; do
    [ -n "$name" ] && setprop "$name" "$value"
  done < "$B"
  rm -f "$B"
  if [ "$(settings get global bluetooth_on)" = 1 ]; then
    cmd bluetooth_manager disable && cmd bluetooth_manager wait-for-state:STATE_OFF
    cmd bluetooth_manager enable
  fi
fi
