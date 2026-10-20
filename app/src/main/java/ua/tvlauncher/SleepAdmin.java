package ua.tvlauncher;

import android.app.admin.DeviceAdminReceiver;

/**
 * Дозвіл "адміністратора пристрою" потрібен лише для одного: щоб таймер сну
 * міг вимкнути екран (DevicePolicyManager.lockNow). Інших прав він не дає.
 */
public class SleepAdmin extends DeviceAdminReceiver {
}
