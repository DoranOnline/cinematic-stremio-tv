package il.cinematic.stremio;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AppUpdateManager {
    private static final String RELEASE_API =
        "https://api.github.com/repos/DoranOnline/cinematic-stremio-tv/releases/latest";
    private static final String APK_NAME = "NUVYRO-update.apk";

    private final MainActivity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long activeDownloadId = -1L;
    private File downloadedApk;
    private boolean promptShown;
    private AlertDialog checkingDialog;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (id == activeDownloadId) {
                handleCompletedDownload(id);
            }
        }
    };

    AppUpdateManager(MainActivity activity) {
        this.activity = activity;
    }

    void start() {
        final IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(
            activity, downloadReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        activity.getWindow().getDecorView().postDelayed(() -> checkForUpdate(false), 3500L);
    }

    void resume() {
        if (isValidApk(downloadedApk)) {
            openInstallerWhenAllowed();
        }
    }

    void stop() {
        try {
            activity.unregisterReceiver(downloadReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was not registered or was already removed.
        }
        executor.shutdownNow();
    }

    String getCurrentVersion() {
        try {
            return activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "";
        }
    }

    void requestUpdate() {
        activity.runOnUiThread(() -> {
            if (isValidApk(downloadedApk)) {
                openInstallerWhenAllowed();
                return;
            }
            if (activeDownloadId != -1L) {
                Toast.makeText(activity, "העדכון כבר יורד. ההתקנה תיפתח בסיום.", Toast.LENGTH_LONG).show();
                return;
            }
            showCheckingDialog();
            checkForUpdate(true);
        });
    }

    private void checkForUpdate(boolean userInitiated) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "NUVYRO-TV-Android");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    showCheckFailure(userInitiated);
                    return;
                }

                final StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }

                final JSONObject release = new JSONObject(body.toString());
                final String latestVersion = release.optString("tag_name", "");
                final String currentVersion = getCurrentVersion();
                if (!VersionComparator.isNewer(latestVersion, currentVersion)) {
                    if (userInitiated) activity.runOnUiThread(() -> showUpToDate(currentVersion));
                    return;
                }

                final JSONArray assets = release.optJSONArray("assets");
                if (assets == null) return;
                for (int index = 0; index < assets.length(); index++) {
                    final JSONObject asset = assets.getJSONObject(index);
                    final String name = asset.optString("name", "");
                    final String url = asset.optString("browser_download_url", "");
                    if (name.endsWith(".apk") && url.startsWith("https://github.com/")) {
                        activity.runOnUiThread(() -> showUpdatePrompt(latestVersion, url, userInitiated));
                        return;
                    }
                }
                showCheckFailure(userInitiated);
            } catch (Exception ignored) {
                // Updates are optional. Never block startup on network/API errors.
                showCheckFailure(userInitiated);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void showUpdatePrompt(String version, String apkUrl, boolean userInitiated) {
        dismissCheckingDialog();
        if ((!userInitiated && promptShown) || activity.isFinishing()) return;
        promptShown = true;
        new AlertDialog.Builder(activity)
            .setTitle("עדכון חדש זמין")
            .setMessage("גרסה " + version + " מוכנה. להוריד ולהתקין עכשיו?")
            .setPositiveButton("עדכן עכשיו", (dialog, which) -> download(apkUrl))
            .setNegativeButton("אחר כך", null)
            .show();
    }

    private void showCheckingDialog() {
        dismissCheckingDialog();
        checkingDialog = new AlertDialog.Builder(activity)
            .setTitle("עדכוני NUVYRO")
            .setMessage("בודק אם קיימת גרסה חדשה…")
            .setCancelable(false)
            .setNegativeButton("ביטול", null)
            .show();
    }

    private void showUpToDate(String currentVersion) {
        dismissCheckingDialog();
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
            .setTitle("האפליקציה מעודכנת")
            .setMessage("מותקנת אצלך הגרסה החדשה ביותר: " + currentVersion)
            .setPositiveButton("סגור", null)
            .show();
    }

    private void showCheckFailure(boolean userInitiated) {
        if (!userInitiated) return;
        activity.runOnUiThread(() -> {
            dismissCheckingDialog();
            if (activity.isFinishing()) return;
            new AlertDialog.Builder(activity)
                .setTitle("לא הצלחנו לבדוק עדכון")
                .setMessage("בדוק את החיבור לאינטרנט ונסה שוב.")
                .setPositiveButton("נסה שוב", (dialog, which) -> requestUpdate())
                .setNegativeButton("סגור", null)
                .show();
        });
    }

    private void dismissCheckingDialog() {
        if (checkingDialog != null) {
            checkingDialog.dismiss();
            checkingDialog = null;
        }
    }

    private void download(String apkUrl) {
        final File downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null) {
            Toast.makeText(activity, "לא ניתן לפתוח את תיקיית ההורדות", Toast.LENGTH_LONG).show();
            return;
        }
        downloadedApk = new File(downloadDir, APK_NAME);
        if (downloadedApk.exists()) downloadedApk.delete();

        final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("NUVYRO update")
            .setDescription("מוריד גרסה חדשה")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, APK_NAME)
            .setMimeType("application/vnd.android.package-archive");
        final DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        activeDownloadId = manager.enqueue(request);
        Toast.makeText(activity, "העדכון יורד. חלון ההתקנה ייפתח אוטומטית.", Toast.LENGTH_LONG).show();
    }

    private void openInstallerWhenAllowed() {
        if (!isValidApk(downloadedApk)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.getPackageManager().canRequestPackageInstalls()) {
            final Intent settingsIntent = new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivity(settingsIntent);
            Toast.makeText(activity, "אפשר התקנה ממקור זה ואז חזור לאפליקציה", Toast.LENGTH_LONG).show();
            return;
        }

        final Uri apkUri = FileProvider.getUriForFile(
            activity,
            activity.getPackageName() + ".fileprovider",
            downloadedApk
        );
        final Intent installIntent = new Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (installIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(installIntent);
        }
    }

    private void handleCompletedDownload(long downloadId) {
        final DownloadManager manager =
            (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        try (Cursor cursor = manager.query(
            new DownloadManager.Query().setFilterById(downloadId))) {
            if (cursor == null || !cursor.moveToFirst()) return;
            final int status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL && isValidApk(downloadedApk)) {
                activeDownloadId = -1L;
                openInstallerWhenAllowed();
            } else {
                activeDownloadId = -1L;
                if (downloadedApk != null && downloadedApk.exists()) downloadedApk.delete();
                Toast.makeText(activity, "ההורדה לא הושלמה. נסה שוב.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isValidApk(File apk) {
        if (apk == null || !apk.isFile() || apk.length() < 1_000_000L) return false;
        try {
            final PackageManager manager = activity.getPackageManager();
            final int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
            final PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            final PackageInfo installed = manager.getPackageInfo(activity.getPackageName(), flags);
            return archive != null && activity.getPackageName().equals(archive.packageName) &&
                signaturesMatch(installed, archive);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean signaturesMatch(PackageInfo installed, PackageInfo archive) {
        final Signature[] current;
        final Signature[] candidate;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (installed.signingInfo == null || archive.signingInfo == null) return false;
            current = installed.signingInfo.getApkContentsSigners();
            candidate = archive.signingInfo.getApkContentsSigners();
        } else {
            current = installed.signatures;
            candidate = archive.signatures;
        }
        if (current == null || candidate == null || current.length != candidate.length) return false;
        for (Signature expected : current) {
            boolean found = false;
            for (Signature actual : candidate) {
                if (expected.equals(actual)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
