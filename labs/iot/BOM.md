# Optional bench bill of materials

The browser and local MQTT demonstrations require no hardware, paid provider,
account or embedded external service. This list is only for a separate manual
ESP32 firmware learning exercise; getLancer never connects to its hardware.

| Item | Quantity | Purpose |
| --- | ---: | --- |
| ESP32 development board with 3.3 V GPIO and USB interface | 1 | Firmware execution |
| DHT22/AM2302 temperature and humidity sensor | 1 | Local sensor input |
| 10 kΩ resistor | 1 | Pull up the bare sensor data line to 3.3 V |
| Breadboard | 1 | Temporary low-voltage connections |
| USB data cable appropriate to the board | 1 | Power, flashing and serial |
| Jumper wires | 4 | VCC, GND, data and pull-up connections |

An external actuator, relay, motor, heater, fan and mains supply are deliberately
absent. `actuatorOn` is a computed boolean in synthetic state. Firmware publishes
measurements; it does not accept remote control or drive an output pin.

Hardware, internet/WiFi access, local LAN broker setup, tools, shipping, taxes,
cloud execution, and any provider charges are excluded from the free source.
No current hardware price, availability, calibration, certification or physical
performance is promised. Verify your exact board/sensor documentation before
manual assembly. Firmware dependency downloads are separate from this archive.
