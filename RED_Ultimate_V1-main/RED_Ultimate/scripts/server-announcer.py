#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
YOUNES RED Sovereign - LAN server announcer (host-side).

Layer 1: mDNS advertisement of "_younes._tcp.local." on port 8088 (zeroconf,
         auto-installed once if missing). Re-registered every cycle so a changed
         DHCP IP is picked up automatically.
Layer 2 (zero dependencies): UDP responder on 0.0.0.0:8095 answering
         "YOUNES_DISCOVERY_V1" probes with {"service":"YOUNES","url":"http://<current-LAN-IP>:8088"}.

The current LAN IP is recomputed on every cycle / every reply - never cached -
so the announcer survives network changes (Wi-Fi <-> hotspot <-> new router).
"""
import json
import os
import socket
import sys
import time
import threading

# P0 2026-10-28: قابلة للضبط من .env / docker-compose. كان المنفذ 8088
# صلباً — فإذا غيّر المستخدم RED_HTTP_PORT إلى 8090 يعلن المكتشف عن منفذ
# خاطئ ويفشل الاتصال رغم أن الخادم يعمل.
HTTP_PORT = int(os.environ.get("YOUNES_HTTP_PORT", os.environ.get("RED_HTTP_PORT", "8088")))
UDP_PORT = int(os.environ.get("YOUNES_UDP_PORT", "8095"))
# يسمح بتجاوز الاكتشاف التلقائي (VPN/هوتسبوت/عدة بطاقات شبكة).
ADVERTISE_IP = os.environ.get("YOUNES_ADVERTISE_IP", "").strip()
PROBE_MARK = "YOUNES_DISCOVERY_V1"
MDNS_TYPE = "_younes._tcp.local."
MDNS_NAME = "YOUNES Server"

def log(msg: str) -> None:
    print(time.strftime("[%Y-%m-%d %H:%M:%S] "), msg, flush=True)

def current_lan_ip() -> str:
    """Best-effort current private LAN IP (no traffic sent)."""
    if ADVERTISE_IP:
        return ADVERTISE_IP
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(("10.255.255.255", 1))  # route lookup only, no packets
            ip = s.getsockname()[0]
            if ip and not ip.startswith("127.") and not ip.startswith("169.254."):
                return ip
        finally:
            s.close()
    except OSError:
        pass
    try:
        for ip in socket.gethostbyname_ex(socket.gethostname())[2]:
            if ip.startswith(("192.168.", "10.", "172.")) and not ip.startswith("169.254."):
                return ip
    except OSError:
        pass
    return ""

def udp_responder() -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    try:
        sock.bind(("", UDP_PORT))
    except OSError as e:
        log(f"UDP responder bind failed on {UDP_PORT}: {e} (discovery layer disabled)")
        return
    log(f"UDP responder listening on 0.0.0.0:{UDP_PORT}")
    while True:
        try:
            data, addr = sock.recvfrom(512)
            if PROBE_MARK.encode() not in data:
                continue
            ip = current_lan_ip()
            if not ip:
                ip = addr[0]  # last resort: echo the prober's subnet member
            reply = json.dumps({
                "service": "YOUNES",
                "version": 1,
                "url": f"http://{ip}:{HTTP_PORT}",
                "ts": int(time.time()),
            }).encode()
            # Reply via a connected socket so the source IP matches the probed subnet.
            r = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try:
                r.connect((addr[0], addr[1]))
                r.send(reply)
            finally:
                r.close()
            log(f"UDP probe answered {addr} -> http://{ip}:{HTTP_PORT}")
        except OSError as e:
            log(f"UDP responder error: {e}")
            time.sleep(1)

def mdns_loop() -> None:
    try:
        from zeroconf import IPVersion, ServiceInfo, Zeroconf
    except ImportError:
        log("zeroconf not installed - mDNS layer disabled (UDP layer still active).")
        log("Enable later with: python -m pip install zeroconf --user")
        return
    while True:
        ip = current_lan_ip()
        if not ip:
            log("mDNS: no LAN IP yet, retrying in 15s")
            time.sleep(15)
            continue
        zc = None
        try:
            zc = Zeroconf(ip_version=IPVersion.V4Only)
            info = ServiceInfo(
                MDNS_TYPE,
                f"{MDNS_NAME}.{MDNS_TYPE}",
                addresses=[socket.inet_aton(ip)],
                port=HTTP_PORT,
                properties={"brand": "YOUNES", "path": "/", "version": "1"},
            )
            zc.register_service(info, ttl=120)
            log(f"mDNS: advertising {MDNS_NAME} -> {ip}:{HTTP_PORT}")
            time.sleep(60)  # re-register each minute with the CURRENT ip
        except OSError as e:
            log(f"mDNS cycle error: {e}")
            time.sleep(10)
        finally:
            if zc is not None:
                try:
                    zc.close()
                except OSError:
                    pass

def main() -> int:
    log(f"YOUNES server announcer starting (mDNS + UDP discovery) on HTTP:{HTTP_PORT} UDP:{UDP_PORT}")
    threading.Thread(target=udp_responder, daemon=True).start()
    if os.environ.get("YOUNES_MDNS_ENABLED", "1") == "1":
        mdns_loop()
    # P0 2026-10-28: mdns_loop كان يعود فوراً عند غياب zeroconf (وهي الحالة
    # الافتراضية في حاوية python:alpine)، فينهي العملية ويقتل مستجيب UDP
    # (daemon thread) — أي أن طبقة الاكتشاف كانت ميتة تماماً في Docker.
    # نُبقي العملية حيّة دائماً لأن UDP responder هو الطبقة الأهم.
    log("mDNS layer unavailable/disabled - keeping UDP responder alive")
    while True:
        time.sleep(3600)

if __name__ == "__main__":
    sys.exit(main())
