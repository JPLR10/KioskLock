package com.kiosklock

import android.app.admin.DeviceAdminReceiver

/** Required so the app can be made device owner with `dpm set-device-owner`. */
class KioskAdminReceiver : DeviceAdminReceiver()
