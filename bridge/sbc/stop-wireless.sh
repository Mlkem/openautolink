#!/bin/bash
# stop-wireless.sh — Tear down the WiFi AP
set -u

# Load config (same env vars as start-wireless.sh)
for f in /etc/openautolink.env /boot/firmware/openautolink.env; do
    [ -f "$f" ] && source "$f" 2>/dev/null || true
done

echo "[wireless] Stopping WiFi AP..."

# Stop hostapd
if pidof hostapd >/dev/null 2>&1; then
    killall hostapd 2>/dev/null || true
    echo "  hostapd stopped"
fi

# Stop dnsmasq (WiFi DHCP, not the SSH one)
DNSMASQ_PID="/var/run/openautolink-wifi-dnsmasq.pid"
if [ -f "$DNSMASQ_PID" ]; then
    kill "$(cat "$DNSMASQ_PID")" 2>/dev/null || true
    rm -f "$DNSMASQ_PID"
    echo "  dnsmasq (WiFi) stopped"
fi

# Remove WiFi interface IP
WIFI_IFACE="${OAL_WIRELESS_INTERFACE:-wlan0}"
ip addr flush dev "$WIFI_IFACE" 2>/dev/null || true

echo "[wireless] WiFi AP stopped"
