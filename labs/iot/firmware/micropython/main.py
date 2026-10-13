"""Original getLancer MIT reference; optional hardware has no execution proof."""
import machine
import dht
import network
import socket
import json
import time

SSID = "REPLACE_LOCALLY"
PASSWORD = "REPLACE_LOCALLY"
BROKER_HOST = "127.0.0.1"  # Placeholder; supplied Node demo stays loopback-only.
BROKER_PORT = 18830
RUN_ID = "00000000-0000-4000-8000-000000000001"
THRESHOLD_C = 30
TOPIC = "getlancer/" + RUN_ID + "/telemetry"

def mqtt_string(value):
    encoded = value.encode("utf-8")
    if not 0 < len(encoded) <= 256:
        raise ValueError("string budget")
    return bytes((len(encoded) >> 8, len(encoded) & 255)) + encoded

def packet(header, body=b""):
    if len(body) > 1024:
        raise ValueError("packet budget")
    length, count = bytearray(), len(body)
    while True:
        digit, count = count % 128, count // 128
        length.append(digit | (128 if count else 0))
        if not count:
            break
    return bytes((header,)) + bytes(length) + body

def write_all(peer, data):
    offset = 0
    while offset < len(data):
        sent = peer.send(data[offset:])
        if not sent:
            raise OSError("closed broker")
        offset += sent

def read_exact(peer, size):
    result = b""
    while len(result) < size:
        part = peer.recv(size - len(result))
        if not part:
            raise OSError("closed broker")
        result += part
    return result

def main():
    if SSID == "REPLACE_LOCALLY" or BROKER_HOST == "127.0.0.1":
        print("Configure your optional isolated hardware exercise locally.")
        return
    wifi, peer = network.WLAN(network.STA_IF), None
    wifi.active(True)
    try:
        wifi.connect(SSID, PASSWORD)
        started = time.ticks_ms()
        while not wifi.isconnected() and time.ticks_diff(time.ticks_ms(), started) < 10000:
            time.sleep_ms(100)
        if not wifi.isconnected():
            raise OSError("WiFi deadline")
        peer = socket.socket()
        peer.settimeout(2)
        peer.connect(socket.getaddrinfo(BROKER_HOST, BROKER_PORT)[0][-1])
        connect_body = mqtt_string("MQTT") + bytes((4, 2, 0, 30)) + mqtt_string("getlancer-publisher-" + RUN_ID)
        write_all(peer, packet(0x10, connect_body))
        if read_exact(peer, 4) != bytes((0x20, 2, 0, 0)):
            raise OSError("CONNECT rejected")
        sensor, failures = dht.DHT22(machine.Pin(4)), 0
        for _ in range(20):
            time.sleep_ms(2000)
            try:
                sensor.measure()
                temperature, humidity = sensor.temperature(), sensor.humidity()
                if not (-20 <= temperature <= 80 and 0 <= humidity <= 100):
                    raise ValueError("sensor bounds")
                payload = json.dumps({"temperatureC": round(temperature, 2), "humidityPercent": round(humidity, 2), "thresholdC": THRESHOLD_C}).encode("utf-8")
                write_all(peer, packet(0x30, mqtt_string(TOPIC) + payload))
                print("Threshold flag", temperature >= THRESHOLD_C, "; no actuator connected.")
            except (OSError, ValueError):
                failures += 1
                if failures >= 3:
                    break
        write_all(peer, packet(0xE0))
    except (OSError, ValueError):
        print("Optional firmware exercise stopped; no credentials logged.")
    finally:
        if peer:
            peer.close()
        wifi.disconnect()
        wifi.active(False)

if __name__ == "__main__":
    main()
