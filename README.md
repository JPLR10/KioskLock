# KioskLock – Android business kiosk

Locks an Android phone/tablet so staff or customers can only use the apps you approve.
Settings, the notification shade, recents, app installs and factory reset are all blocked.
A PIN-protected admin screen lets you change the allowed apps or unlock the device for maintenance.

Requires Android 9 or newer.

---

## 1. One-time setup: let GitHub build the APK and QR code

You never need Android Studio or a PC cable. GitHub builds, signs and publishes the APK for free.

1. Create a free account at github.com and a new **public** repository called `KioskLock`.
   (It must be public so tablets can download the APK during setup. The signing key stays secret.)
2. Upload the contents of this folder to the repository (**Add file → Upload files**, drag everything in,
   including the hidden `.github` folder; or use GitHub Desktop).
3. In the repository go to **Settings → Secrets and variables → Actions → New repository secret** and add:
   - `KEYSTORE_BASE64` = the contents of `keystore-base64.txt` (from the separate keys download)
   - `KEYSTORE_PASSWORD` = the contents of `password.txt`
4. Go to **Actions → Build KioskLock → Run workflow**. After about 5 minutes, open **Releases**:
   you'll find `KioskLock.apk` and `setup-qr.png`.
5. Print `setup-qr.png` or keep it open on a phone/laptop screen. The same QR code works for every tablet,
   and keeps working after updates (it always points to the latest release).

Every time you upload changed code, a new release is built automatically.

**Never lose the signing key** (`kiosklock-release.jks` + password). Every tablet trusts only that key;
without it you can't update tablets that are already set up.

## 2. Set up each tablet (about 5 minutes, no PC)

1. Factory reset the tablet (Settings → System → Reset options → Erase all data).
2. On the first **Welcome** screen, **tap the same spot 6 times** quickly. A QR scanner opens.
   (On Android 9 the tablet first asks for Wi-Fi and downloads the scanner.)
3. Connect to Wi-Fi if asked, then scan `setup-qr.png`.
4. Accept the prompts ("This device belongs to your organisation"). The tablet downloads KioskLock,
   makes it Device Owner and finishes setup. Skip adding a Google account.

If the 6-tap trick does nothing, the tablet maker has disabled it; set that tablet up with the
ADB method at the end of this file instead.

## 3. Configure the kiosk

1. Press Home. If asked which launcher to use, pick **KioskLock** → *Always*.
2. **Long-press the "KioskLock" title** → you'll be asked to create an admin PIN.
3. Tick the apps staff/customers may use → **Save & lock**.

The device is now locked: only the ticked apps appear and open; everything else is blocked.
This survives reboots.

## Day-to-day

| Task | How |
|---|---|
| Change allowed apps | Long-press title → PIN → tick/untick → Save & lock |
| Use Settings / install updates | Long-press title → PIN → **Exit kiosk**. Do your work, then come back and **Save & lock** |
| Change PIN | Admin screen → Change PIN |
| Retire the device | Admin screen → **Remove owner**, then uninstall KioskLock normally |

5 wrong PIN attempts lock the admin screen for 60 seconds.

## What's locked while the kiosk is on

- Only approved apps can run (Settings, Play Store, browser etc. are blocked unless you tick them)
- No status bar, notifications, quick settings or recent-apps switcher
- Home button returns to the kiosk screen; Back does nothing there
- Power menu still works so the device can be restarted
- Blocked: factory reset from Settings, safe mode, new users, SD cards, USB file transfer,
  unknown-source installs, force-stopping/clearing apps, developer options & USB debugging

## Things to know

- **Helper apps:** some apps open other apps (e.g. a link opening Chrome, a camera button,
  a payment app opening a banking app). Anything they open must also be ticked, otherwise it's blocked.
- **USB debugging is disabled while locked.** Use *Exit kiosk* first if you need `adb` again.
- **Forgotten PIN:** there's no back door. Settings-based reset is blocked, so you'd need a
  hardware-button recovery-mode reset (and possibly the Google account for FRP).
  Keep the PIN somewhere safe.
- **Many devices?** For a fleet, an Android Enterprise MDM (e.g. Google's managed Android,
  Microsoft Intune, Scalefusion, Hexnode) does the same thing with remote management.
- **Updating KioskLock on a tablet:** Exit kiosk, open the Releases page in the tablet's browser,
  download the new `KioskLock.apk` and install it over the old one, then Save & lock again.
  Settings and installs stay as you configured them.

## Project layout

```
app/src/main/java/com/kiosklock/
  MainActivity.kt        kiosk home screen (launcher), enters lock-task mode
  AdminActivity.kt       PIN-protected admin: allowed apps, exit, change PIN, remove owner
  KioskPolicy.kt         all device-owner lockdown rules – edit RESTRICTIONS here
  Prefs.kt               allowed apps + salted/hashed PIN + lockout
  KioskAdminReceiver.kt  device admin receiver
  ProvisioningActivities.kt  answers the QR setup wizard (fully managed device)
tools/make_qr.py         builds the setup QR code (run by GitHub automatically)
.github/workflows/build.yml  GitHub build: APK, signing, QR code, release
  AppEntry.kt, Adapters.kt, PinDialog.kt   UI helpers
```

---

## Alternative: set up one tablet with a PC (ADB)

Only needed if a tablet doesn't support QR setup. Factory reset, skip all accounts, turn on USB debugging, then:

```
adb install KioskLock.apk
adb shell dpm set-device-owner com.kiosklock/.KioskAdminReceiver
```
