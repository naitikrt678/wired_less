# raw_hid_dump.py
# Usage: python raw_hid_dump.py "HJD-X"           # match substring of device name
# Requires pywinusb (pip install pywinusb)

import sys, time
from pywinusb import hid

if len(sys.argv) < 2:
    print("Usage: python raw_hid_dump.py <device-name-substring>")
    sys.exit(1)

substr = sys.argv[1].lower()

def find_device():
    all_devs = hid.HidDeviceFilter().get_devices()
    for d in all_devs:
        try:
            pname = (d.product_name or "").lower()
        except Exception:
            pname = ""
        if substr in pname:
            return d
    return None

dev = find_device()
if not dev:
    print("No HID device found matching:", substr)
    print("Available devices:")
    for d in hid.HidDeviceFilter().get_devices():
        print("  vendor=0x%04x product=0x%04x name=%r" % (d.vendor_id, d.product_id, d.product_name))
    sys.exit(1)

print("Found device:", dev.product_name, "vendor=0x%04x product=0x%04x" % (dev.vendor_id, dev.product_id))

def raw_handler(data):
    # data is a list of ints (report id + payload bytes) or just payload depending on device
    ts = time.time()
    hexs = " ".join(f"{b:02x}" for b in data)
    print(f"{ts:.6f}  len={len(data)}  {hexs}")

try:
    dev.open()
    # subscribe to raw reports
    dev.set_raw_data_handler(lambda data: raw_handler(data))
    print("Listening for raw HID reports. Press controller buttons. Ctrl-C to stop.")
    while True:
        time.sleep(0.1)
except KeyboardInterrupt:
    pass
finally:
    try:
        dev.close()
    except Exception:
        pass
    print("Stopped.")
