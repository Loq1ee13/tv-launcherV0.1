package ua.tvlauncher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Таймер сну: у призначений час вимикає екран телевізора. */
public class SleepReceiver extends BroadcastReceiver {

    private static final String PREFS = "launcher";
    private static final String KEY_AT = "sleep_at";

    static ComponentName adminComponent(Context c) {
        return new ComponentName(c, SleepAdmin.class);
    }

    static boolean isAdmin(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(adminComponent(c));
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, SleepReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, 1, i, flags);
    }

    static void schedule(Context c, int minutes) {
        long at = System.currentTimeMillis() + minutes * 60_000L;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.RTC_WAKEUP, at, pending(c));
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_AT, at).apply();
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(c));
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_AT, 0).apply();
    }

    static long remainingMs(Context c) {
        long at = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_AT, 0);
        long left = at - System.currentTimeMillis();
        return left > 0 ? left : 0;
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_AT, 0).apply();
        if (!isAdmin(c)) return;
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        try {
            if (dpm != null) dpm.lockNow();
        } catch (SecurityException ignored) {
        }
    }
}
