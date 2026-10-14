"""Make the QR code that sets up a freshly reset tablet with KioskLock as Device Owner.

Usage:
  python3 make_qr.py --apk-url URL --cert cert.der --out setup-qr.png [--json-out setup-qr.json]
"""
import argparse
import base64
import hashlib
import json

import qrcode

p = argparse.ArgumentParser()
p.add_argument("--apk-url", required=True)
p.add_argument("--cert", required=True, help="DER signing certificate of the APK")
p.add_argument("--out", required=True)
p.add_argument("--json-out")
a = p.parse_args()

with open(a.cert, "rb") as f:
    checksum = base64.urlsafe_b64encode(hashlib.sha256(f.read()).digest()).decode().rstrip("=")

payload = {
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.kiosklock/.KioskAdminReceiver",
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": a.apk_url,
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": checksum,
    # Keep the camera, browser etc. so they can be offered as allowed apps.
    "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
    "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": False,
}

text = json.dumps(payload, separators=(",", ":"))
qrcode.make(text, error_correction=qrcode.constants.ERROR_CORRECT_M).save(a.out)
if a.json_out:
    with open(a.json_out, "w") as f:
        json.dump(payload, f, indent=2)
print(text)
