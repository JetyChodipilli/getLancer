# Optional low-voltage wiring

Disconnect USB before changing wires. Use an ESP32 board that exposes GPIO4,
3V3 and GND. Read the labels and the datasheet for your exact sensor; bare
DHT22 sensors and breakout modules have different physical pin arrangements.
Follow signal names instead of assuming a module pin order.

| ESP32 signal | Sensor signal | Connection |
| --- | --- | --- |
| 3V3 | VCC | Supply the selected 3.3 V compatible DHT22 |
| GND | GND | Common ground |
| GPIO4 | DATA | Measurement data line |
| 3V3 | DATA | 10 kΩ pull-up resistor between these nets |
| None | NC, if present | Leave unconnected |

A breakout with an installed pull-up may not need the external resistor;
verify its schematic first. Do not connect 5 V to an ESP32 GPIO. USB powers
the development board; the sensor uses its 3.3 V rail. No external actuator
or mains circuit is connected. Both firmware examples use the same GPIO4
measurement connection and calculate an internal threshold flag only.

The Node reproduction binds to loopback and cannot be contacted by an ESP32
over WiFi. A separate manual hardware exercise needs a broker under your own
control on an isolated LAN, with your own authentication/TLS policy. That
configuration and physical test are outside the free account-free demonstration.
