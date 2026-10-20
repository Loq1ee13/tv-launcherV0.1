package ua.tvlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Легкий лаунчер для Android TV / Google TV у стилі iOS.
 *
 * Зверху — віджети-картки: годинник, погода, курс НБУ, пам'ять.
 * Посередині — сітка іконок усіх програм (нові позначені синьою крапкою).
 * Знизу — док: YouTube без реклами, Браузер, Рекомендовані, Меню.
 *
 * Пульт:
 *   OK            — відкрити
 *   Довге OK      — сховати програму / "Про програму" (видалити, кеш)
 */
public class MainActivity extends Activity {

    // ---------------------------------------------------------------- Налаштування

    private static final String PREFS = "launcher";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_CITY = "city_name";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_WALL_TYPE = "wall_type";     // "preset" або "photo"
    private static final String KEY_WALL_PRESET = "wall_preset";
    private static final String KEY_ICON_SIZE = "icon_size";     // 0 маленькі, 1 звичайні, 2 великі

    private static final int REQ_PICK_PHOTO = 41;
    private static final int REQ_PHOTO_PERM = 42;

    // Розмір іконок: розмір у dp, кількість колонок, розмір підпису
    private static final int[] ICON_SIZES = {56, 72, 92};
    private static final int[] ICON_COLUMNS = {10, 8, 6};
    private static final float[] LABEL_SP = {11, 12, 14};
    private static final String[] ICON_SIZE_NAMES = {
            "Маленькі (більше програм на екрані)",
            "Звичайні",
            "Великі (краще видно з дивана)"
    };

    /** Готові шпалери: назва + три кольори переходу. */
    private static final String[] WALL_NAMES = {
            "Сутінки", "Океан", "Захід сонця", "Ліс", "Аврора", "Графіт"
    };
    private static final int[][] WALL_COLORS = {
            {0xFF141A3F, 0xFF4A2A7A, 0xFF9C3F6E},
            {0xFF0A2540, 0xFF0E5A8A, 0xFF19A1B8},
            {0xFF3A1C4A, 0xFFC2456B, 0xFFF29A4A},
            {0xFF0F2A24, 0xFF1F5E4A, 0xFF6FA36B},
            {0xFF0B1D3A, 0xFF1B6B6F, 0xFF7A4FB0},
            {0xFF121214, 0xFF2A2A30, 0xFF45454F},
    };

    private static final String DEFAULT_CITY = "Львів";
    private static final String DEFAULT_LAT = "49.8397";
    private static final String DEFAULT_LON = "24.0297";

    private static final String BROWSER_PKG = "com.phlox.tvwebbrowser";   // TV Bro
    private static final String DOWNLOADER_PKG = "com.esaba.downloader";  // Downloader від AFTVnews
    private static final String SMARTTUBE_CODE = "28544";                 // код у Downloader

    private static final long WEATHER_EVERY = 30 * 60_000L;
    private static final long FX_EVERY = 3 * 60 * 60_000L;
    private static final long RETRY_AFTER = 5 * 60_000L;
    private static final long NEW_FOR = 3 * 24 * 60 * 60_000L; // синя крапка 3 дні

    // ---------------------------------------------------------------- Кольори (стиль iOS)

    private static final int GLASS = 0x33FFFFFF;    // "матове скло" віджетів і доку
    private static final int GLASS_EDGE = 0x26FFFFFF;
    private static final int WEATHER_1 = 0xFF4C8EEA;
    private static final int WEATHER_2 = 0xFF2856B5;
    private static final int WARN_1 = 0xFFF2A33A;
    private static final int WARN_2 = 0xFFD9762B;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_SOFT = 0xCCFFFFFF;
    private static final int TEXT_DIM = 0x99FFFFFF;
    private static final int NEW_DOT = 0xFF4DA3FF;
    private static final int FOCUS = 0xFFFFFFFF;

    private static final int DOCK_ICON_DP = 64; // розмір іконки в доку
    private static final int WIDGET_RADIUS = 22;

    private static final Locale UK = new Locale("uk");

    // ---------------------------------------------------------------- Моделі

    static class AppItem {
        String pkg;
        String label;
        Drawable icon;
        boolean isHidden;
        boolean isNew;
        Intent launch;
    }

    /** Рекомендована програма для списку "Рекомендовані". */
    static class Rec {
        final String name;
        final String desc;
        final String pkg;      // пакет у Google Play, якщо відомий
        final String[] match;  // слова для пошуку серед встановлених, якщо пакет невідомий
        final String url;      // сайт, якщо програми немає в Google Play

        Rec(String name, String desc, String pkg, String[] match, String url) {
            this.name = name;
            this.desc = desc;
            this.pkg = pkg;
            this.match = match;
            this.url = url;
        }
    }

    private static final Rec[] RECS = {
            new Rec("Send Files to TV", "файли з телефона на ТВ", "com.yablio.sendfilestotv", null, null),
            new Rec("X-plore", "файловий менеджер", "com.lonelycatgames.Xplore", null, null),
            new Rec("VLC", "відео з флешки й мережі", "org.videolan.vlc", null, null),
            new Rec("Kodi", "медіацентр", "org.xbmc.kodi", null, null),
            new Rec("AirScreen", "екран телефона на ТВ", "com.ionitech.airscreen", null, null),
            new Rec("TV Bro", "браузер без реклами", BROWSER_PKG, null, null),
            new Rec("Downloader", "для встановлення SmartTube", DOWNLOADER_PKG, null, null),
            new Rec("Київстар ТБ", "ТБ, фільми, серіали", null, new String[]{"київстар", "kyivstar"}, null),
            new Rec("MEGOGO", "фільми, серіали, ТБ", null, new String[]{"megogo"}, null),
            new Rec("SWEET.TV", "ТБ і фільми", null, new String[]{"sweet.tv", "sweet tv"}, null),
            new Rec("AdGuard для Android TV", "блокує рекламу в усій системі", null,
                    new String[]{"adguard"},
                    "https://adguard.com/en/adguard-android-tv/browser/overview.html"),
    };

    /** Квадратна іконка з заокругленими кутами (як в iOS). */
    static class Squircle {
        FrameLayout frame;
        GradientDrawable bg;
        ImageView image;
        TextView glyph;
    }

    /** Віджет-картка. */
    static class Widget {
        LinearLayout box;
        TextView title;
        TextView main;
        TextView sub;
        TextView extra;
        FrameLayout bar;
        View barFill;
    }

    /** Іконка в доку. */
    static class DockItem {
        LinearLayout box;
        Squircle icon;
        TextView label;
        AppItem app;
    }

    // ---------------------------------------------------------------- Стан

    private final List<AppItem> apps = new ArrayList<>();
    private final List<AppItem> allApps = new ArrayList<>(); // усі, включно з прихованими
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();

    private AppAdapter adapter;
    private GridView grid;
    private ImageView wallView;
    private View wallShade;
    private int iconDp = 72;
    private float labelSp = 12;
    private Widget clockW;
    private Widget weatherW;
    private Widget fxW;
    private Widget memW;
    private DockItem ytDock;
    private DockItem brDock;

    private boolean showHidden = false;
    private boolean gridFocused = false;
    private boolean firstLoad = true;
    private int selectedPos = 0;
    private int loadGeneration = 0;
    private volatile long nextWeather = 0;
    private volatile long nextFx = 0;

    private final Runnable reload = this::loadApps;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateMemory();
            long now = System.currentTimeMillis();
            if (now >= nextWeather) fetchWeather();
            if (now >= nextFx) fetchFx();
            handler.postDelayed(this, 10_000);
        }
    };

    /** Ловить встановлення / видалення програм, щоб сітка оновлювалась одразу. */
    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
            if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction()) && !replacing
                    && intent.getData() != null) {
                String pkg = intent.getData().getSchemeSpecificPart();
                try {
                    PackageManager pm = getPackageManager();
                    CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                    Toast.makeText(MainActivity.this, "Додано на головний екран: " + label,
                            Toast.LENGTH_LONG).show();
                } catch (Exception ignored) {
                }
            }
            handler.removeCallbacks(reload);
            handler.postDelayed(reload, 700);
        }
    };

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    /** Світлі діалоги, як в iOS. */
    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    // ================================================================ Побудова екрана

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);

        // Шпалери: окремий шар під усім, щоб можна було поставити і градієнт, і фото
        wallView = new ImageView(this);
        screen.addView(wallView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wallShade = new View(this);
        wallShade.setBackgroundColor(0x4D000000); // легке затемнення фото, щоб текст читався
        screen.addView(wallShade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        applyWallpaper();
        loadIconSize();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(36), dp(22), dp(36), dp(14));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        screen.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- Віджети
        LinearLayout widgets = new LinearLayout(this);
        widgets.setOrientation(LinearLayout.HORIZONTAL);
        widgets.setClipChildren(false);

        clockW = makeWidget(glass(), false, null);
        clockW.main.setTextSize(46);
        clockW.main.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

        weatherW = makeWidget(gradient(WEATHER_1, WEATHER_2), true, this::showCityDialog);
        weatherW.main.setTextSize(40);
        weatherW.main.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        fxW = makeWidget(glass(), false, null);
        memW = makeWidget(glass(), true, this::showCleanupDialog);
        addStorageBar(memW);

        widgets.addView(clockW.box, weightLp(1.25f, true));
        widgets.addView(weatherW.box, weightLp(1.35f, true));
        widgets.addView(fxW.box, weightLp(1f, true));
        widgets.addView(memW.box, weightLp(1f, false));

        setWidget(weatherW, DEFAULT_CITY, "—", "Завантаження…", "");
        setWidget(fxW, "КУРС НБУ", "—", "Завантаження…", "");
        root.addView(widgets, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(138)));

        // ---- Сітка іконок
        grid = new GridView(this);
        grid.setNumColumns(ICON_COLUMNS[iconSizeIndex()]);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(14));
        grid.setPadding(0, dp(20), 0, dp(16));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setVerticalFadingEdgeEnabled(true);
        grid.setFadingEdgeLength(dp(24));

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> launch(apps.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            showAppMenu(apps.get(pos));
            return true;
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedPos = pos;
                refreshHighlight();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        grid.setOnFocusChangeListener((v, has) -> {
            gridFocused = has;
            refreshHighlight();
        });
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- Док
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setClipChildren(false);
        dock.setPadding(dp(22), dp(10), dp(22), dp(8));
        GradientDrawable dockBg = glass();
        dockBg.setCornerRadius(dp(30));
        dock.setBackground(dockBg);

        ytDock = makeDockItem("YouTube", "▶", 0xFFFF3B30, 0xFFD70015, this::showSmartTubeHelp);
        brDock = makeDockItem("Браузер", "🌐", 0xFF34AADC, 0xFF1C6FD1, this::installBrowser);
        DockItem recDock = makeDockItem("Рекомендовані", "📦", 0xFFFF9F0A, 0xFFFF6A00, this::showRecommended);
        DockItem menuDock = makeDockItem("Меню", "⚙", 0xFF8E8E93, 0xFF5A5A60, this::showMenu);
        dock.addView(ytDock.box);
        dock.addView(brDock.box);
        dock.addView(recDock.box);
        dock.addView(menuDock.box);

        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(dock, dockLp);

        setContentView(screen);
        ytDock.box.requestFocus();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(pkgReceiver, f);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.post(tick);
        loadApps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        handler.removeCallbacks(reload);
        net.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        // Це головний екран — "Назад" повертає фокус на сітку.
        if (!apps.isEmpty()) grid.requestFocus(); else ytDock.box.requestFocus();
    }

    // ================================================================ Будівельні блоки

    private Drawable makeWallpaper(int preset) {
        int[] c = WALL_COLORS[Math.max(0, Math.min(WALL_COLORS.length - 1, preset))];
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR, c);
        // два м'яких "світіння", щоб шпалери виглядали глибше
        int light1 = (c[2] & 0x00FFFFFF);
        int light2 = (c[1] & 0x00FFFFFF);
        GradientDrawable glow1 = new GradientDrawable();
        glow1.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow1.setColors(new int[]{0x66000000 | light1, light1});
        glow1.setGradientRadius(dp(520));
        glow1.setGradientCenter(0.85f, 0.1f);
        GradientDrawable glow2 = new GradientDrawable();
        glow2.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow2.setColors(new int[]{0x55000000 | light2, light2});
        glow2.setGradientRadius(dp(460));
        glow2.setGradientCenter(0.1f, 0.95f);
        return new LayerDrawable(new Drawable[]{base, glow1, glow2});
    }

    // ================================================================ Шпалери

    private File wallFile() {
        return new File(getFilesDir(), "wallpaper.jpg");
    }

    private void applyWallpaper() {
        File f = wallFile();
        if ("photo".equals(prefs().getString(KEY_WALL_TYPE, "preset")) && f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) {
                wallView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                wallView.setImageBitmap(b);
                wallShade.setVisibility(View.VISIBLE);
                return;
            }
        }
        wallView.setScaleType(ImageView.ScaleType.FIT_XY);
        wallView.setImageDrawable(makeWallpaper(prefs().getInt(KEY_WALL_PRESET, 0)));
        wallShade.setVisibility(View.GONE);
    }

    private void showWallpaperDialog() {
        final int n = WALL_NAMES.length;
        String[] items = new String[n + 2];
        boolean isPhoto = "photo".equals(prefs().getString(KEY_WALL_TYPE, "preset"));
        int current = prefs().getInt(KEY_WALL_PRESET, 0);
        for (int i = 0; i < n; i++) {
            items[i] = (!isPhoto && i == current ? "✓  " : "     ") + WALL_NAMES[i];
        }
        items[n] = "🖼  Своє фото: вибрати файл";
        items[n + 1] = "📁  Своє фото: зі списку фото на ТВ і флешці";

        dialog().setTitle("Шпалери")
                .setItems(items, (d, w) -> {
                    if (w < n) {
                        prefs().edit().putString(KEY_WALL_TYPE, "preset").putInt(KEY_WALL_PRESET, w).apply();
                        applyWallpaper();
                    } else if (w == n) {
                        pickPhotoFile();
                    } else {
                        pickPhotoFromList();
                    }
                })
                .setNegativeButton("Закрити", null)
                .show();
    }

    /** Системне вікно вибору файлу. На деяких ТВ його немає — тоді йдемо до списку фото. */
    private void pickPhotoFile() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        try {
            startActivityForResult(open, REQ_PICK_PHOTO);
            return;
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
        Intent get = new Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        try {
            startActivityForResult(get, REQ_PICK_PHOTO);
            return;
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
        Toast.makeText(this, "Тут немає вікна вибору файлів — показую список фото", Toast.LENGTH_LONG).show();
        pickPhotoFromList();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_PHOTO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importWallpaper(data.getData());
        }
    }

    private String photoPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void pickPhotoFromList() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(photoPermission()) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{photoPermission()}, REQ_PHOTO_PERM);
            return;
        }
        listPhotos();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_PHOTO_PERM) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            listPhotos();
        } else {
            Toast.makeText(this, "Без дозволу на фото список не показати", Toast.LENGTH_LONG).show();
        }
    }

    private void listPhotos() {
        net.execute(() -> {
            final List<String> names = new ArrayList<>();
            final List<Uri> uris = new ArrayList<>();
            Uri coll = Build.VERSION.SDK_INT >= 29
                    ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] proj = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            };
            try (Cursor c = getContentResolver().query(coll, proj, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (c != null) {
                    while (c.moveToNext() && names.size() < 80) {
                        long id = c.getLong(0);
                        String name = c.getString(1);
                        String folder = c.getString(2);
                        names.add((folder != null ? folder + " / " : "") + (name != null ? name : "фото"));
                        uris.add(ContentUris.withAppendedId(coll, id));
                    }
                }
            } catch (Exception ignored) {
            }
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (names.isEmpty()) {
                    dialog().setTitle("Фото не знайдено")
                            .setMessage("Встав флешку з фото (зачекай хвилину, поки ТВ її прочитає) "
                                    + "або надішли фото з телефона через «Send Files to TV».")
                            .setPositiveButton("Зрозуміло", null)
                            .show();
                    return;
                }
                dialog().setTitle("Обери фото для шпалер")
                        .setItems(names.toArray(new String[0]), (d, w) -> importWallpaper(uris.get(w)))
                        .setNegativeButton("Скасувати", null)
                        .show();
            });
        });
    }

    /** Копіює фото в пам'ять лаунчера, зменшене до розміру екрана, і ставить шпалерами. */
    private void importWallpaper(final Uri uri) {
        Toast.makeText(this, "Ставлю шпалери…", Toast.LENGTH_SHORT).show();
        final ContentResolver cr = getContentResolver();
        net.execute(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = cr.openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) {
                    sample *= 2;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                Bitmap b;
                try (InputStream in = cr.openInputStream(uri)) {
                    b = BitmapFactory.decodeStream(in, null, opts);
                }
                if (b == null) throw new Exception("not an image");
                try (FileOutputStream out = new FileOutputStream(wallFile())) {
                    b.compress(Bitmap.CompressFormat.JPEG, 90, out);
                }
                b.recycle();
                prefs().edit().putString(KEY_WALL_TYPE, "photo").apply();
                runOnUiThread(() -> {
                    applyWallpaper();
                    Toast.makeText(this, "Шпалери встановлено", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Не вдалося відкрити це фото",
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    // ================================================================ Розмір іконок

    private int iconSizeIndex() {
        return Math.max(0, Math.min(2, prefs().getInt(KEY_ICON_SIZE, 1)));
    }

    private void loadIconSize() {
        int i = iconSizeIndex();
        iconDp = ICON_SIZES[i];
        labelSp = LABEL_SP[i];
    }

    private void showIconSizeDialog() {
        int cur = iconSizeIndex();
        String[] items = new String[ICON_SIZE_NAMES.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = (i == cur ? "✓  " : "     ") + ICON_SIZE_NAMES[i];
        }
        dialog().setTitle("Розмір іконок")
                .setItems(items, (d, w) -> {
                    prefs().edit().putInt(KEY_ICON_SIZE, w).apply();
                    loadIconSize();
                    grid.setNumColumns(ICON_COLUMNS[w]);
                    grid.setAdapter(adapter); // перебудувати іконки з новим розміром
                    if (!apps.isEmpty()) grid.requestFocus();
                })
                .setNegativeButton("Закрити", null)
                .show();
    }

    // ================================================================ Очищення пам'яті

    private void showCleanupDialog() {
        String[] items = {
                "🚀  Звільнити оперативну пам'ять",
                "🗂  Очистити кеш програм",
                "💾  Налаштування сховища"
        };
        dialog().setTitle("Очищення пам'яті")
                .setItems(items, (d, w) -> {
                    if (w == 0) freeRam();
                    else if (w == 1) showCacheList();
                    else openStorageSettings();
                })
                .setNegativeButton("Закрити", null)
                .show();
    }

    /** Закриває фонові процеси інших програм (не чіпає ту, що зараз на екрані). */
    private void freeRam() {
        final ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) return;
        final List<String> pkgs = new ArrayList<>();
        for (AppItem a : allApps) pkgs.add(a.pkg);
        final String self = getPackageName();
        Toast.makeText(this, "Звільняю пам'ять…", Toast.LENGTH_SHORT).show();

        net.execute(() -> {
            ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(before);
            // фонові процеси, які зараз працюють, — теж закриваємо
            try {
                List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
                if (running != null) {
                    for (ActivityManager.RunningAppProcessInfo p : running) {
                        if (p.pkgList != null) Collections.addAll(pkgs, p.pkgList);
                    }
                }
            } catch (Exception ignored) {
            }
            for (String p : new HashSet<>(pkgs)) {
                if (p.equals(self)) continue;
                try {
                    am.killBackgroundProcesses(p);
                } catch (Exception ignored) {
                }
            }
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
            }
            ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(after);
            final long freed = Math.max(0, after.availMem - before.availMem);
            final String msg = "Звільнено " + mb(freed) + " · вільно " + gb(after.availMem)
                    + " з " + gb(after.totalMem);
            runOnUiThread(() -> {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                updateMemory();
            });
        });
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void showCacheList() {
        if (Build.VERSION.SDK_INT >= 26 && !hasUsageAccess()) {
            dialog().setTitle("Розмір кешу")
                    .setMessage("Щоб показати, скільки кешу займає кожна програма, лаунчеру потрібен "
                            + "дозвіл «Доступ до використання». Його можна дати один раз, "
                            + "а можна відкрити список програм і без розмірів.")
                    .setPositiveButton("Дати дозвіл", (d, w) -> requestUsageAccess())
                    .setNeutralButton("Без розмірів", (d, w) -> showCacheListPlain())
                    .setNegativeButton("Скасувати", null)
                    .show();
            return;
        }
        if (Build.VERSION.SDK_INT < 26) {
            showCacheListPlain();
            return;
        }
        Toast.makeText(this, "Рахую кеш…", Toast.LENGTH_SHORT).show();
        final List<AppItem> list = new ArrayList<>(allApps);
        net.execute(() -> {
            final List<long[]> sizes = new ArrayList<>(); // [індекс, байти]
            long total = 0;
            try {
                StorageStatsManager ssm = (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
                for (int i = 0; i < list.size(); i++) {
                    try {
                        StorageStats st = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT,
                                list.get(i).pkg, Process.myUserHandle());
                        long cache = st.getCacheBytes();
                        sizes.add(new long[]{i, cache});
                        total += cache;
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            Collections.sort(sizes, (a, b) -> Long.compare(b[1], a[1]));
            final String[] items = new String[sizes.size()];
            final AppItem[] targets = new AppItem[sizes.size()];
            for (int i = 0; i < sizes.size(); i++) {
                AppItem a = list.get((int) sizes.get(i)[0]);
                targets[i] = a;
                items[i] = a.label + "  —  " + mb(sizes.get(i)[1]);
            }
            final long totalFinal = total;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (items.length == 0) {
                    showCacheListPlain();
                    return;
                }
                dialog().setTitle("Кеш: " + mb(totalFinal) + " · обери програму → «Очистити кеш»")
                        .setItems(items, (d, w) -> openAppInfo(targets[w]))
                        .setNegativeButton("Закрити", null)
                        .show();
            });
        });
    }

    private void showCacheListPlain() {
        final List<AppItem> list = new ArrayList<>(allApps);
        final Collator col = Collator.getInstance();
        Collections.sort(list, (x, y) -> col.compare(x.label, y.label));
        String[] items = new String[list.size()];
        for (int i = 0; i < items.length; i++) items[i] = list.get(i).label;
        dialog().setTitle("Обери програму → «Очистити кеш»")
                .setItems(items, (d, w) -> openAppInfo(list.get(w)))
                .setNegativeButton("Закрити", null)
                .show();
    }

    private void requestUsageAccess() {
        Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!safeStart(i)) {
            dialog().setTitle("Потрібен дозвіл")
                    .setMessage("Цей телевізор не показує екран дозволу. Дай його один раз з комп'ютера:\n\n"
                            + "adb shell appops set ua.tvlauncher GET_USAGE_STATS allow")
                    .setPositiveButton("Зрозуміло", null)
                    .show();
        } else {
            Toast.makeText(this, "Знайди «Легкий лаунчер» і дозволь доступ", Toast.LENGTH_LONG).show();
        }
    }

    private void openAppInfo(AppItem a) {
        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg));
        info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!safeStart(info)) {
            Toast.makeText(this, "Не вдалося відкрити налаштування", Toast.LENGTH_SHORT).show();
        }
    }

    private static String mb(long bytes) {
        if (bytes >= 1073741824L) return gb(bytes);
        return String.format(Locale.US, "%d МБ", Math.round(bytes / 1048576.0));
    }

    private GradientDrawable glass() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(GLASS);
        g.setCornerRadius(dp(WIDGET_RADIUS));
        g.setStroke(dp(1), GLASS_EDGE);
        return g;
    }

    private GradientDrawable gradient(int c1, int c2) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(WIDGET_RADIUS));
        return g;
    }

    private LinearLayout.LayoutParams weightLp(float w, boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, w);
        if (gap) lp.rightMargin = dp(16);
        return lp;
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        return t;
    }

    /** Фокус у стилі tvOS: картка трохи "підіймається" і світиться білою рамкою. */
    private void focusLift(View v, GradientDrawable bg, float scale, int strokeDp) {
        v.setOnFocusChangeListener((view, has) -> {
            if (bg != null) bg.setStroke(has ? dp(strokeDp) : 0, FOCUS);
            float s = has ? scale : 1f;
            view.animate().scaleX(s).scaleY(s).setDuration(140).start();
            view.setElevation(has ? dp(14) : 0);
        });
    }

    private Widget makeWidget(GradientDrawable bg, boolean focusable, Runnable onClick) {
        Widget w = new Widget();
        w.box = new LinearLayout(this);
        w.box.setOrientation(LinearLayout.VERTICAL);
        w.box.setPadding(dp(18), dp(14), dp(18), dp(12));
        w.box.setBackground(bg);
        w.box.setClipToOutline(true);

        w.title = text(12, TEXT_SOFT, true);
        w.main = text(30, TEXT, false);
        w.sub = text(13, TEXT, true);
        w.extra = text(12, TEXT_DIM, false);
        w.box.addView(w.title);
        w.box.addView(w.main);
        w.box.addView(w.sub);
        w.box.addView(w.extra);

        if (focusable) {
            w.box.setFocusable(true);
            w.box.setClickable(true);
            focusLift(w.box, bg, 1.04f, 2);
            if (onClick != null) w.box.setOnClickListener(v -> onClick.run());
        }
        return w;
    }

    private void setWidget(Widget w, String title, String main, String sub, String extra) {
        w.title.setText(title);
        w.main.setText(main);
        w.sub.setText(sub);
        w.extra.setText(extra);
        w.extra.setVisibility(extra == null || extra.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void addStorageBar(Widget w) {
        w.bar = new FrameLayout(this);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x33FFFFFF);
        track.setCornerRadius(dp(3));
        w.bar.setBackground(track);
        w.barFill = new View(this);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xFF34C759);
        fill.setCornerRadius(dp(3));
        w.barFill.setBackground(fill);
        w.bar.addView(w.barFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(4);
        w.box.addView(w.bar, 3, lp); // між "sub" і "extra"
    }

    private Squircle makeSquircle(int sizeDp) {
        Squircle s = new Squircle();
        s.frame = new FrameLayout(this);
        s.bg = new GradientDrawable();
        s.bg.setColor(0xFFF2F2F7);
        s.bg.setCornerRadius(dp(sizeDp * 0.225f));
        s.frame.setBackground(s.bg);
        s.frame.setClipToOutline(true);

        s.image = new ImageView(this);
        s.frame.addView(s.image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        s.glyph = new TextView(this);
        s.glyph.setGravity(Gravity.CENTER);
        s.glyph.setTextColor(TEXT);
        s.glyph.setTextSize(sizeDp * 0.42f);
        s.glyph.setVisibility(View.GONE);
        s.frame.addView(s.glyph, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return s;
    }

    /** Ставить іконку програми так, щоб вона заповнювала "квадрат" як в iOS. */
    private void setSquircleIcon(Squircle s, Drawable icon) {
        s.glyph.setVisibility(View.GONE);
        s.image.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable) {
            // Адаптивна іконка: фон + передній план на весь квадрат (без системної маски)
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            Drawable back = a.getBackground();
            Drawable fore = a.getForeground();
            Drawable layered;
            if (back != null && fore != null) layered = new LayerDrawable(new Drawable[]{back, fore});
            else layered = back != null ? back : fore;
            s.image.setImageDrawable(layered != null ? layered : icon);
            s.image.setScaleType(ImageView.ScaleType.FIT_XY);
            s.image.setPadding(0, 0, 0, 0);
            // шари адаптивної іконки мають 18% запасу з кожного боку — збільшуємо, щоб обрізати
            s.image.setScaleX(1.5f);
            s.image.setScaleY(1.5f);
            s.bg.setColor(0xFFF2F2F7);
        } else {
            // Звичайна іконка: на світлому квадраті з відступом
            s.image.setImageDrawable(icon);
            s.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = Math.max(dp(8), s.frame.getLayoutParams() != null
                    ? s.frame.getLayoutParams().width / 7 : dp(10));
            s.image.setPadding(pad, pad, pad, pad);
            s.image.setScaleX(1f);
            s.image.setScaleY(1f);
            s.bg.setColor(0xFFF2F2F7);
        }
    }

    /** Іконка-символ на кольоровому градієнті (для доку, коли програму ще не встановлено). */
    private void setSquircleGlyph(Squircle s, String glyph, int c1, int c2) {
        s.image.setImageDrawable(null);
        s.image.setVisibility(View.GONE);
        s.glyph.setVisibility(View.VISIBLE);
        s.glyph.setText(glyph);
        s.bg.setColors(new int[]{c1, c2});
        s.bg.setOrientation(GradientDrawable.Orientation.TL_BR);
    }

    private DockItem makeDockItem(String label, String glyph, int c1, int c2, Runnable onMissing) {
        DockItem d = new DockItem();
        d.box = new LinearLayout(this);
        d.box.setOrientation(LinearLayout.VERTICAL);
        d.box.setGravity(Gravity.CENTER_HORIZONTAL);
        d.box.setFocusable(true);
        d.box.setClickable(true);
        d.box.setClipChildren(false);
        d.box.setPadding(dp(14), dp(2), dp(14), 0);

        d.icon = makeSquircle(DOCK_ICON_DP);
        d.box.addView(d.icon.frame, new LinearLayout.LayoutParams(dp(DOCK_ICON_DP), dp(DOCK_ICON_DP)));
        setSquircleGlyph(d.icon, glyph, c1, c2);

        d.label = text(11, TEXT_SOFT, true);
        d.label.setText(label);
        d.label.setGravity(Gravity.CENTER);
        d.label.setPadding(0, dp(4), 0, 0);
        d.box.addView(d.label);

        d.box.setOnFocusChangeListener((v, has) -> {
            d.icon.bg.setStroke(has ? dp(3) : 0, FOCUS);
            d.label.setTextColor(has ? TEXT : TEXT_SOFT);
            float s = has ? 1.18f : 1f;
            d.icon.frame.animate().scaleX(s).scaleY(s).translationY(has ? -dp(4) : 0)
                    .setDuration(140).start();
            d.icon.frame.setElevation(has ? dp(14) : 0);
        });
        d.box.setOnClickListener(v -> {
            if (d.app != null) launch(d.app); else onMissing.run();
        });
        d.box.setOnLongClickListener(v -> {
            if (d.app == null) return false;
            showAppMenu(d.app);
            return true;
        });
        return d;
    }

    private void bindDock(DockItem d, AppItem app, String glyph, int c1, int c2) {
        d.app = app;
        if (app != null && app.icon != null) {
            setSquircleIcon(d.icon, app.icon);
        } else {
            setSquircleGlyph(d.icon, glyph, c1, c2);
        }
    }

    // ================================================================ Віджети: годинник

    private void updateClock() {
        Date now = new Date();
        String d = new SimpleDateFormat("EEEE, d MMMM", UK).format(now);
        long left = SleepReceiver.remainingMs(this);
        String extra = left > 0 ? "🌙 Сон через " + minutesLeft(left) + " хв" : "";
        setWidget(clockW, d.toUpperCase(UK),
                new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), "", extra);
        clockW.sub.setVisibility(View.GONE);
    }

    private static long minutesLeft(long ms) {
        return (ms + 59_999) / 60_000;
    }

    // ================================================================ Віджети: погода (Open-Meteo)

    private void fetchWeather() {
        nextWeather = System.currentTimeMillis() + WEATHER_EVERY;
        final SharedPreferences p = prefs();
        final String city = p.getString(KEY_CITY, DEFAULT_CITY);
        final double lat = parseD(p.getString(KEY_LAT, DEFAULT_LAT));
        final double lon = parseD(p.getString(KEY_LON, DEFAULT_LON));

        net.execute(() -> {
            try {
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,weather_code"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                + "&timezone=auto&forecast_days=4", lat, lon);
                JSONObject j = new JSONObject(Net.get(url, null));
                JSONObject cur = j.getJSONObject("current");
                long t = Math.round(cur.getDouble("temperature_2m"));
                int code = cur.getInt("weather_code");

                JSONObject d = j.getJSONObject("daily");
                JSONArray times = d.getJSONArray("time");
                JSONArray codes = d.getJSONArray("weather_code");
                JSONArray max = d.getJSONArray("temperature_2m_max");
                JSONArray min = d.getJSONArray("temperature_2m_min");

                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat out = new SimpleDateFormat("EE", UK);
                StringBuilder fc = new StringBuilder();
                for (int i = 1; i < times.length() && i < 4; i++) {
                    if (fc.length() > 0) fc.append("   ");
                    Date day = in.parse(times.getString(i));
                    fc.append(day != null ? out.format(day) : "").append(' ')
                            .append(weatherIcon(codes.getInt(i))).append(' ')
                            .append(Math.round(max.getDouble(i))).append('°');
                }
                long hi = Math.round(max.getDouble(0));
                long lo = Math.round(min.getDouble(0));

                final String main = (t > 0 ? "+" : "") + t + "°";
                final String sub = weatherIcon(code) + " " + weatherText(code)
                        + "   макс " + hi + "° · мін " + lo + "°";
                final String extra = fc.toString();
                runOnUiThread(() -> setWidget(weatherW, city.toUpperCase(UK), main, sub, extra));
            } catch (Exception e) {
                nextWeather = System.currentTimeMillis() + RETRY_AFTER;
                runOnUiThread(() -> {
                    if (weatherW.main.getText().toString().equals("—")) {
                        setWidget(weatherW, city.toUpperCase(UK), "—", "Немає зв'язку", "");
                    }
                });
            }
        });
    }

    private static double parseD(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Коди погоди WMO → значок. */
    private static String weatherIcon(int c) {
        if (c == 0) return "☀️";
        if (c <= 2) return "🌤";
        if (c == 3) return "☁️";
        if (c == 45 || c == 48) return "🌫";
        if (c >= 51 && c <= 57) return "🌦";
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return "🌧";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "🌨";
        if (c >= 95) return "⛈";
        return "🌡";
    }

    /** Коди погоди WMO → опис. */
    private static String weatherText(int c) {
        if (c == 0) return "Ясно";
        if (c <= 2) return "Мінлива хмарність";
        if (c == 3) return "Хмарно";
        if (c == 45 || c == 48) return "Туман";
        if (c >= 51 && c <= 57) return "Мряка";
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return "Дощ";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "Сніг";
        if (c >= 95) return "Гроза";
        return "";
    }

    private void showCityDialog() {
        final EditText input = new EditText(this);
        input.setHint("Наприклад: Київ");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);

        dialog().setTitle("Місто для погоди")
                .setMessage("Зараз: " + prefs().getString(KEY_CITY, DEFAULT_CITY))
                .setView(box)
                .setPositiveButton("Знайти", (d, w) -> searchCity(input.getText().toString().trim()))
                .setNegativeButton("Скасувати", null)
                .show();
        input.requestFocus();
    }

    private void searchCity(final String query) {
        if (query.isEmpty()) return;
        Toast.makeText(this, "Шукаю…", Toast.LENGTH_SHORT).show();
        net.execute(() -> {
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=uk&format=json&name="
                        + URLEncoder.encode(query, "UTF-8");
                JSONObject j = new JSONObject(Net.get(url, null));
                JSONArray res = j.optJSONArray("results");
                final List<String> labels = new ArrayList<>();
                final List<String[]> data = new ArrayList<>();
                if (res != null) {
                    for (int i = 0; i < res.length(); i++) {
                        JSONObject o = res.getJSONObject(i);
                        String name = o.optString("name");
                        String region = o.optString("admin1");
                        String country = o.optString("country");
                        StringBuilder l = new StringBuilder(name);
                        if (!region.isEmpty()) l.append(", ").append(region);
                        if (!country.isEmpty() && !country.equals("Україна")) l.append(", ").append(country);
                        labels.add(l.toString());
                        data.add(new String[]{name,
                                String.valueOf(o.getDouble("latitude")),
                                String.valueOf(o.getDouble("longitude"))});
                    }
                }
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (labels.isEmpty()) {
                        Toast.makeText(this, "Нічого не знайдено", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog().setTitle("Обери місто")
                            .setItems(labels.toArray(new String[0]), (d, w) -> {
                                String[] c = data.get(w);
                                prefs().edit().putString(KEY_CITY, c[0])
                                        .putString(KEY_LAT, c[1]).putString(KEY_LON, c[2]).apply();
                                setWidget(weatherW, c[0].toUpperCase(UK), "—", "Завантаження…", "");
                                fetchWeather();
                            })
                            .setNegativeButton("Скасувати", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Немає зв'язку з інтернетом",
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ================================================================ Віджети: курс НБУ

    private void fetchFx() {
        nextFx = System.currentTimeMillis() + FX_EVERY;
        net.execute(() -> {
            try {
                JSONArray a = new JSONArray(Net.get(
                        "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?json", null));
                double usd = 0, eur = 0;
                String day = "";
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    String cc = o.optString("cc");
                    if ("USD".equals(cc)) {
                        usd = o.optDouble("rate", 0);
                        day = o.optString("exchangedate");
                    } else if ("EUR".equals(cc)) {
                        eur = o.optDouble("rate", 0);
                    }
                }
                if (usd == 0 && eur == 0) throw new Exception("no rates");
                final String main = String.format(Locale.US, "$ %.2f", usd);
                final String sub = String.format(Locale.US, "€ %.2f", eur);
                final String extra = day.isEmpty() ? "" : "на " + day;
                runOnUiThread(() -> setWidget(fxW, "КУРС НБУ", main, sub, extra));
            } catch (Exception e) {
                nextFx = System.currentTimeMillis() + RETRY_AFTER;
                runOnUiThread(() -> {
                    if (fxW.main.getText().toString().equals("—")) {
                        setWidget(fxW, "КУРС НБУ", "—", "Немає зв'язку", "");
                    }
                });
            }
        });
    }

    // ================================================================ Віджети: пам'ять

    private void updateMemory() {
        long free = 0, total = 0;
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            free = sf.getAvailableBytes();
            total = sf.getTotalBytes();
        } catch (Exception ignored) {
        }
        String ram = "";
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ram = "ОЗП: " + gb(mi.availMem) + " вільно";
        }
        boolean low = free > 0 && free < 1_500_000_000L;
        setWidget(memW, "ПАМ'ЯТЬ", gb(free),
                low ? "Мало місця — натисни" : "вільно з " + gb(total), ram);
        memW.main.setTextSize(28);

        // смужка заповнення сховища
        final float used = total > 0 ? 1f - (float) free / total : 0f;
        memW.bar.post(() -> {
            ViewGroup.LayoutParams lp = memW.barFill.getLayoutParams();
            lp.width = Math.round(memW.bar.getWidth() * Math.max(0f, Math.min(1f, used)));
            memW.barFill.setLayoutParams(lp);
        });
        ((GradientDrawable) memW.barFill.getBackground())
                .setColor(low ? 0xFFFF453A : used > 0.8f ? 0xFFFF9F0A : 0xFF34C759);
        GradientDrawable bg = (GradientDrawable) memW.box.getBackground();
        if (low) {
            bg.setColors(new int[]{WARN_1, WARN_2});
        } else {
            bg.setColors(null);
            bg.setColor(GLASS);
        }
    }

    private static String gb(long bytes) {
        return String.format(Locale.US, "%.1f ГБ", bytes / 1073741824.0);
    }

    private void openStorageSettings() {
        if (!safeStart(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))) {
            safeStart(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ================================================================ Меню і таймер сну

    private void showMenu() {
        long left = SleepReceiver.remainingMs(this);
        String sleep = left > 0
                ? "🌙  Таймер сну (залишилось " + minutesLeft(left) + " хв)"
                : "🌙  Таймер сну";
        String[] items = {
                sleep,
                "🖼  Шпалери",
                "🔠  Розмір іконок",
                "🧹  Очищення пам'яті",
                "📦  Рекомендовані програми",
                "🌤  Місто для погоди",
                showHidden ? "🙈  Не показувати приховані програми" : "👁  Показати приховані програми",
                "⚙️  Налаштування телевізора"
        };
        dialog().setTitle("Меню")
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0:
                            showSleepDialog();
                            break;
                        case 1:
                            showWallpaperDialog();
                            break;
                        case 2:
                            showIconSizeDialog();
                            break;
                        case 3:
                            showCleanupDialog();
                            break;
                        case 4:
                            showRecommended();
                            break;
                        case 5:
                            showCityDialog();
                            break;
                        case 6:
                            showHidden = !showHidden;
                            loadApps();
                            break;
                        default:
                            safeStart(new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNegativeButton("Закрити", null)
                .show();
    }

    private void showSleepDialog() {
        final int[] mins = {15, 30, 60, 90, 120};
        final String[] items = {"15 хвилин", "30 хвилин", "1 година", "1.5 години", "2 години", "Вимкнути таймер"};
        dialog().setTitle("Вимкнути екран через…")
                .setItems(items, (d, w) -> {
                    if (w == mins.length) {
                        SleepReceiver.cancel(this);
                        Toast.makeText(this, "Таймер сну вимкнено", Toast.LENGTH_SHORT).show();
                    } else {
                        SleepReceiver.schedule(this, mins[w]);
                        Toast.makeText(this, "Телевізор засне приблизно через " + items[w],
                                Toast.LENGTH_LONG).show();
                        if (!SleepReceiver.isAdmin(this)) requestSleepPermission();
                    }
                    updateClock();
                })
                .setNegativeButton("Скасувати", null)
                .show();
    }

    private void requestSleepPermission() {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, SleepReceiver.adminComponent(this));
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Потрібно лише для таймера сну: щоб лаунчер міг вимкнути екран у призначений час.");
        if (!safeStart(i)) {
            dialog().setTitle("Потрібен дозвіл")
                    .setMessage("Цей телевізор не показує вікно дозволу. Дай його один раз з комп'ютера:\n\n"
                            + "adb shell dpm set-active-admin ua.tvlauncher/.SleepAdmin")
                    .setPositiveButton("Зрозуміло", null)
                    .show();
        }
    }

    // ================================================================ Рекомендовані програми

    /** Шукає програму серед встановлених: за пакетом або за словами в назві. */
    private AppItem findInstalled(Rec r) {
        for (AppItem a : allApps) {
            if (r.pkg != null && r.pkg.equals(a.pkg)) return a;
            if (r.match != null) {
                String l = a.label.toLowerCase(Locale.ROOT);
                String p = a.pkg.toLowerCase(Locale.ROOT);
                for (String m : r.match) {
                    if (l.contains(m) || p.contains(m.replace(" ", "").replace(".", ""))) return a;
                }
            }
        }
        if (r.pkg != null && isInstalled(r.pkg)) {
            AppItem a = new AppItem();
            a.pkg = r.pkg;
            a.label = r.name;
            a.launch = getPackageManager().getLeanbackLaunchIntentForPackage(r.pkg);
            if (a.launch == null) a.launch = getPackageManager().getLaunchIntentForPackage(r.pkg);
            if (a.launch != null) return a;
        }
        return null;
    }

    private void showRecommended() {
        final AppItem[] found = new AppItem[RECS.length];
        String[] items = new String[RECS.length];
        for (int i = 0; i < RECS.length; i++) {
            found[i] = findInstalled(RECS[i]);
            items[i] = (found[i] != null ? "✅  " : "⬇️  ") + RECS[i].name + " — " + RECS[i].desc;
        }
        dialog().setTitle("Рекомендовані  (✅ є · ⬇️ встановити)")
                .setItems(items, (d, w) -> {
                    if (found[w] != null) launch(found[w]);
                    else installRec(RECS[w]);
                })
                .setNegativeButton("Закрити", null)
                .show();
    }

    private void installRec(Rec r) {
        if (r.url != null) {
            dialog().setTitle(r.name)
                    .setMessage("Цієї програми немає в Google Play. Її ставлять з офіційного сайту:\n\n"
                            + "1. Відкрий сайт кнопкою нижче (потрібен браузер, наприклад TV Bro).\n"
                            + "2. Завантаж файл і встанови його.\n\n" + r.url)
                    .setPositiveButton("Відкрити сайт", (d, w) -> {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(r.url));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        if (!safeStart(i)) {
                            Toast.makeText(this, "Спершу встанови браузер (TV Bro)", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Закрити", null)
                    .show();
            return;
        }
        boolean opened;
        if (r.pkg != null) {
            opened = openPlayStore(r.pkg);
        } else {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://search?c=apps&q=" + Uri.encode(r.name)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            opened = safeStart(i);
        }
        if (!opened) {
            Toast.makeText(this, "Відкрий Google Play і знайди «" + r.name + "»", Toast.LENGTH_LONG).show();
        }
    }

    // ================================================================ YouTube і браузер

    private static boolean isSmartTube(AppItem a) {
        String p = a.pkg.toLowerCase(Locale.ROOT);
        String l = a.label.toLowerCase(Locale.ROOT);
        return p.contains("smarttube") || p.contains("teamsmart") || l.contains("smarttube");
    }

    private static boolean isBrowser(AppItem a) {
        return a.pkg.equals(BROWSER_PKG) || a.label.toLowerCase(Locale.ROOT).contains("tv bro");
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean openPlayStore(String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return safeStart(i);
    }

    private void installBrowser() {
        if (!openPlayStore(BROWSER_PKG)) {
            dialog().setTitle("Браузер TV Bro")
                    .setMessage("Відкрий Google Play на телевізорі, знайди «TV Bro» і встанови. "
                            + "У ньому є вбудоване блокування реклами.")
                    .setPositiveButton("Зрозуміло", null)
                    .show();
        }
    }

    private void showSmartTubeHelp() {
        boolean hasDownloader = isInstalled(DOWNLOADER_PKG);
        String msg = "SmartTube — YouTube для телевізора без реклами. Його немає в Google Play, "
                + "тому ставиться через програму Downloader:\n\n"
                + (hasDownloader ? "" : "1. Встанови «Downloader» (від AFTVnews) з Google Play.\n")
                + (hasDownloader ? "1. " : "2. ") + "Відкрий Downloader і введи код " + SMARTTUBE_CODE + ".\n"
                + (hasDownloader ? "2. " : "3. ") + "Встанови завантажений файл і дозволь встановлення, якщо ТВ попросить.\n\n"
                + "Після встановлення ця іконка сама відкриватиме SmartTube.\n"
                + "Став тільки з офіційного джерела (GitHub: yuliskov/SmartTube).";

        AlertDialog.Builder b = dialog().setTitle("YouTube без реклами")
                .setMessage(msg)
                .setNegativeButton("Закрити", null);
        if (hasDownloader) {
            b.setPositiveButton("Відкрити Downloader", (d, w) -> {
                Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(DOWNLOADER_PKG);
                if (i == null) i = getPackageManager().getLaunchIntentForPackage(DOWNLOADER_PKG);
                if (i == null || !safeStart(i)) {
                    Toast.makeText(this, "Не вдалося відкрити Downloader", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            b.setPositiveButton("Встановити Downloader", (d, w) -> {
                if (!openPlayStore(DOWNLOADER_PKG)) {
                    Toast.makeText(this, "Знайди «Downloader» у Google Play", Toast.LENGTH_LONG).show();
                }
            });
        }
        b.show();
    }

    // ================================================================ Програми

    private void loadApps() {
        final int gen = ++loadGeneration;
        final boolean includeHidden = showHidden;
        final Set<String> hidden = getHidden();
        final Context ctx = getApplicationContext();
        final String self = getPackageName();

        new Thread(() -> {
            PackageManager pm = ctx.getPackageManager();
            Map<String, AppItem> map = new LinkedHashMap<>();
            collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, map, self);
            collect(pm, Intent.CATEGORY_LAUNCHER, map, self);

            AppItem yt = null;
            AppItem br = null;
            List<AppItem> result = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (AppItem a : map.values()) {
                // YouTube і браузер живуть у доку, а не в сітці
                if (yt == null && isSmartTube(a)) {
                    yt = a;
                    continue;
                }
                if (br == null && isBrowser(a)) {
                    br = a;
                    continue;
                }
                a.isHidden = hidden.contains(a.pkg);
                try {
                    a.isNew = now - pm.getPackageInfo(a.pkg, 0).firstInstallTime < NEW_FOR;
                } catch (Exception ignored) {
                }
                if (!a.isHidden || includeHidden) result.add(a);
            }
            // Нові — першими, далі за абеткою
            final Collator col = Collator.getInstance();
            Collections.sort(result, (x, y) -> {
                if (x.isNew != y.isNew) return x.isNew ? -1 : 1;
                return col.compare(x.label, y.label);
            });
            final AppItem ytFinal = yt;
            final AppItem brFinal = br;

            runOnUiThread(() -> {
                if (gen != loadGeneration || isFinishing()) return;
                bindDock(ytDock, ytFinal, "▶", 0xFFFF3B30, 0xFFD70015);
                bindDock(brDock, brFinal, "🌐", 0xFF34AADC, 0xFF1C6FD1);
                allApps.clear();
                allApps.addAll(map.values());
                apps.clear();
                apps.addAll(result);
                if (selectedPos >= apps.size()) selectedPos = Math.max(0, apps.size() - 1);
                adapter.notifyDataSetChanged();
                if (firstLoad) {
                    firstLoad = false;
                    if (!apps.isEmpty()) grid.requestFocus();
                }
            });
        }).start();
    }

    private static void collect(PackageManager pm, String category, Map<String, AppItem> map, String self) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> list;
        try {
            list = pm.queryIntentActivities(query, 0);
        } catch (Exception e) {
            return;
        }
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(self) || map.containsKey(pkg)) continue;

            AppItem a = new AppItem();
            a.pkg = pkg;
            a.label = String.valueOf(ri.loadLabel(pm));
            try {
                a.icon = ri.loadIcon(pm);
            } catch (Exception e) {
                a.icon = null;
            }
            a.launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            map.put(pkg, a);
        }
    }

    private void launch(AppItem a) {
        if (a.launch == null || !safeStart(a.launch)) {
            Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
            if (fallback == null) fallback = getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (fallback == null || !safeStart(fallback)) {
                Toast.makeText(this, "Не вдалося відкрити " + a.label, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private void showAppMenu(AppItem a) {
        String toggle = a.isHidden ? "Показати на головному екрані" : "Сховати з головного екрана";
        String[] items = {toggle, "Про програму (видалити, очистити кеш)"};
        dialog().setTitle(a.label)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        Set<String> h = getHidden();
                        if (a.isHidden) h.remove(a.pkg); else h.add(a.pkg);
                        saveHidden(h);
                        loadApps();
                    } else {
                        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + a.pkg));
                        info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        if (!safeStart(info)) {
                            Toast.makeText(this, "Не вдалося відкрити налаштування", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Скасувати", null)
                .show();
    }

    private Set<String> getHidden() {
        return new HashSet<>(prefs().getStringSet(KEY_HIDDEN, new HashSet<>()));
    }

    private void saveHidden(Set<String> h) {
        prefs().edit().putStringSet(KEY_HIDDEN, h).apply();
    }

    // ================================================================ Сітка іконок

    private void refreshHighlight() {
        int first = grid.getFirstVisiblePosition();
        for (int i = 0; i < grid.getChildCount(); i++) {
            styleItem(grid.getChildAt(i), gridFocused && (first + i) == selectedPos, true);
        }
    }

    private void styleItem(View item, boolean selected, boolean animate) {
        if (item == null) return;
        Holder h = (Holder) item.getTag();
        h.icon.bg.setStroke(selected ? dp(3) : 0, FOCUS);
        h.label.setTypeface(selected ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        float s = selected ? 1.16f : 1f;
        float ty = selected ? -dp(4) : 0;
        if (animate) {
            h.icon.frame.animate().scaleX(s).scaleY(s).translationY(ty).setDuration(140).start();
        } else {
            h.icon.frame.setScaleX(s);
            h.icon.frame.setScaleY(s);
            h.icon.frame.setTranslationY(ty);
        }
        h.icon.frame.setElevation(selected ? dp(14) : dp(2));
    }

    static class Holder {
        Squircle icon;
        TextView label;
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            View item = convertView;
            if (item == null) {
                LinearLayout box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER_HORIZONTAL);
                box.setClipChildren(false);
                box.setPadding(0, dp(6), 0, 0);

                h = new Holder();
                h.icon = makeSquircle(iconDp);
                box.addView(h.icon.frame, new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));

                h.label = text(labelSp, TEXT, false);
                h.label.setGravity(Gravity.CENTER);
                h.label.setPadding(dp(4), dp(7), dp(4), 0);
                h.label.setShadowLayer(dp(3), 0, dp(1), 0x66000000);
                box.addView(h.label, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                box.setTag(h);
                item = box;
            } else {
                h = (Holder) item.getTag();
            }

            AppItem a = apps.get(position);
            if (a.icon != null) setSquircleIcon(h.icon, a.icon);
            else setSquircleGlyph(h.icon, a.label.isEmpty() ? "?" : a.label.substring(0, 1),
                    0xFF8E8E93, 0xFF5A5A60);

            // Нова програма — синя крапка перед назвою, як в iOS
            if (a.isNew) {
                SpannableString s = new SpannableString("● " + a.label);
                s.setSpan(new ForegroundColorSpan(NEW_DOT), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                h.label.setText(s);
            } else {
                h.label.setText(a.label);
            }
            item.setAlpha(a.isHidden ? 0.4f : 1f);
            styleItem(item, gridFocused && position == selectedPos, false);
            return item;
        }
    }
}
