package com.baramulla.evacueeregister;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Hosts the Evacuee Property Register (a self-contained HTML application bundled
 * in assets/) inside a WebView.
 *
 * The register itself is unmodified between the browser version and this app.
 * Three browser behaviours do not work inside a plain WebView, so they are
 * bridged to the platform here:
 *
 *   1. Backup / CSV download. The page builds a blob: URL and clicks an anchor,
 *      which a WebView ignores. The injected shim hands the bytes to
 *      Bridge.saveFile(), which opens the system "create document" picker so the
 *      clerk chooses where the backup goes. This needs no storage permission.
 *   2. Restore. <input type="file"> requires onShowFileChooser() to be handled.
 *   3. Printing. window.print() is a no-op in a WebView, so it is routed to the
 *      Android print service, which also provides "save as PDF".
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK_FILE = 1001;
    private static final int REQ_SAVE_FILE = 1002;
    private static final int REQ_SYNC_OPEN = 1003;
    private static final int REQ_SYNC_CREATE = 1004;

    private static final String PREFS = "evacuee_register";
    private static final String KEY_SYNC_URI = "sync_uri";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingBytes;
    private String pendingName;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        web = new WebView(this);
        setContentView(web);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        // The register's records live in localStorage. Without this the app
        // would forget everything on exit.
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        // The only content ever loaded is the bundled asset; nothing remote is
        // fetched, so widening file-URL access carries no exposure here and lets
        // the blob: reads used for backups succeed.
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(false);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectShim();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(pick, REQ_PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    toast("No file manager is available on this device.");
                    return false;
                }
                return true;
            }
        });

        if (state != null) {
            web.restoreState(state);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    /**
     * Teaches the bundled page the three things a WebView cannot do by itself.
     * Injected on every page finish; guards against running twice.
     */
    private void injectShim() {
        String js =
            "(function(){"
          + "if(window.__androidShim){return;}window.__androidShim=1;"

          // In the app there is only one printable view, so the report is
          // selected directly rather than through the transient class the
          // browser build toggles around window.print().
          + "var st=document.createElement('style');"
          + "st.textContent='@media print{#tab-reports{display:block !important}"
          + ".panel:not(#tab-reports){display:none !important}}';"
          + "document.head.appendChild(st);"

          + "window.print=function(){AndroidBridge.printPage();};"

          // Route blob:/data: downloads (backup .json and .csv) to the platform.
          + "var origClick=HTMLAnchorElement.prototype.click;"
          + "HTMLAnchorElement.prototype.click=function(){"
          + "var href=this.getAttribute('href')||'';"
          + "var name=this.getAttribute('download');"
          + "if(name&&(href.indexOf('blob:')===0||href.indexOf('data:')===0)){"
          + "var x=new XMLHttpRequest();"
          + "x.open('GET',href,true);"
          + "x.responseType='blob';"
          + "x.onload=function(){"
          + "var fr=new FileReader();"
          + "fr.onload=function(){"
          + "var s=String(fr.result);var c=s.indexOf(',');"
          + "AndroidBridge.saveFile(s.substring(c+1),name,x.response.type||'application/octet-stream');"
          + "};"
          + "fr.onerror=function(){AndroidBridge.toast('Could not prepare the file.');};"
          + "fr.readAsDataURL(x.response);"
          + "};"
          + "x.onerror=function(){AndroidBridge.toast('Could not prepare the file.');};"
          + "x.send();"
          + "return;"
          + "}"
          + "return origClick.apply(this,arguments);"
          + "};"
          + "})();";
        web.evaluateJavascript(js, null);
    }

    /** Exposed to the bundled page as window.AndroidBridge. */
    private class Bridge {

        @JavascriptInterface
        public void saveFile(String base64, String name, String mime) {
            final String fileName = (name == null || name.length() == 0)
                    ? "evacuee-register-backup.json" : name;
            final String fileMime = (mime == null || mime.length() == 0)
                    ? "application/octet-stream" : mime;
            try {
                pendingBytes = Base64.decode(base64, Base64.DEFAULT);
            } catch (Exception e) {
                pendingBytes = null;
                toast("Could not prepare the file.");
                return;
            }
            pendingName = fileName;
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    save.addCategory(Intent.CATEGORY_OPENABLE);
                    save.setType(fileMime);
                    save.putExtra(Intent.EXTRA_TITLE, fileName);
                    try {
                        startActivityForResult(save, REQ_SAVE_FILE);
                    } catch (Exception e) {
                        pendingBytes = null;
                        toast("No app is available to save the file.");
                    }
                }
            });
        }

        /* ---- cloud sync ----
           The sync file is an ordinary document the clerk picks with Android's
           own file picker, so it can live in Google Drive, OneDrive, Dropbox, on
           an SD card or on a USB stick — whatever the office already uses. Taking
           a persistable permission lets the app reopen it on later runs. No
           account, API key or INTERNET permission is involved: the cloud app that
           owns the folder does the networking. */

        @JavascriptInterface
        public String syncFileName() {
            Uri uri = syncUri();
            return uri == null ? "" : displayName(uri);
        }

        @JavascriptInterface
        public void chooseSyncFile() {
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    open.addCategory(Intent.CATEGORY_OPENABLE);
                    open.setType("*/*");
                    open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    try {
                        startActivityForResult(open, REQ_SYNC_OPEN);
                    } catch (Exception e) {
                        toast("No file manager is available on this device.");
                    }
                }
            });
        }

        @JavascriptInterface
        public void createSyncFile(String suggested) {
            final String name = (suggested == null || suggested.length() == 0)
                    ? "evacuee-register-sync.json" : suggested;
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent make = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    make.addCategory(Intent.CATEGORY_OPENABLE);
                    make.setType("application/json");
                    make.putExtra(Intent.EXTRA_TITLE, name);
                    make.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    try {
                        startActivityForResult(make, REQ_SYNC_CREATE);
                    } catch (Exception e) {
                        toast("No file manager is available on this device.");
                    }
                }
            });
        }

        /** Returns the sync file's text, "" for a new or empty file, null on failure. */
        @JavascriptInterface
        public String readSync() {
            Uri uri = syncUri();
            if (uri == null) return null;
            InputStream in = null;
            try {
                in = getContentResolver().openInputStream(uri);
                if (in == null) return null;
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) != -1) buf.write(chunk, 0, read);
                return new String(buf.toByteArray(), "UTF-8");
            } catch (Exception e) {
                return null;
            } finally {
                if (in != null) { try { in.close(); } catch (Exception ignored) { } }
            }
        }

        @JavascriptInterface
        public boolean writeSync(String text) {
            Uri uri = syncUri();
            if (uri == null || text == null) return false;
            OutputStream out = null;
            try {
                // "wt" truncates first; without it a shorter register would leave
                // the tail of the previous, longer file behind and corrupt it.
                out = getContentResolver().openOutputStream(uri, "wt");
                if (out == null) return false;
                out.write(text.getBytes("UTF-8"));
                out.flush();
                return true;
            } catch (Exception e) {
                return false;
            } finally {
                if (out != null) { try { out.close(); } catch (Exception ignored) { } }
            }
        }

        @JavascriptInterface
        public void forgetSync() {
            Uri uri = syncUri();
            if (uri != null) {
                try {
                    getContentResolver().releasePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                          | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) { }
            }
            prefs().edit().remove(KEY_SYNC_URI).commit();
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(new Runnable() {
                public void run() {
                    doPrint();
                }
            });
        }

        @JavascriptInterface
        public void toast(String message) {
            final String m = message;
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private Uri syncUri() {
        String stored = prefs().getString(KEY_SYNC_URI, "");
        if (stored.length() == 0) return null;
        try {
            return Uri.parse(stored);
        } catch (Exception e) {
            return null;
        }
    }

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) {
                    String name = c.getString(col);
                    if (name != null && name.length() > 0) return name;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) { try { c.close(); } catch (Exception ignored) { } }
        }
        return "sync file";
    }

    /** Keeps access to the chosen sync file across restarts. */
    private void rememberSyncFile(Intent data) {
        Uri uri = data.getData();
        int granted = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                       | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, granted);
        } catch (Exception ignored) {
            // Some providers refuse a persistable grant; it still works this run.
        }
        prefs().edit().putString(KEY_SYNC_URI, uri.toString()).commit();

        final String name = displayName(uri);
        web.evaluateJavascript(
                "window.onSyncFileChosen && window.onSyncFileChosen("
                        + JSONObject.quote(name) + ");", null);
    }

    private void doPrint() {
        try {
            PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (pm == null) {
                toast("Printing is not available on this device.");
                return;
            }
            String job = getString(R.string.print_job);
            PrintDocumentAdapter adapter = web.createPrintDocumentAdapter(job);
            PrintAttributes attrs = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build();
            pm.print(job, adapter, attrs);
        } catch (Exception e) {
            toast("Printing is not available on this device.");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == REQ_PICK_FILE) {
            Uri[] picked = null;
            if (result == RESULT_OK && data != null && data.getData() != null) {
                picked = new Uri[] { data.getData() };
            }
            if (fileCallback != null) {
                fileCallback.onReceiveValue(picked);
                fileCallback = null;
            }
            return;
        }

        if (request == REQ_SYNC_OPEN || request == REQ_SYNC_CREATE) {
            if (result == RESULT_OK && data != null && data.getData() != null) {
                rememberSyncFile(data);
            }
            return;
        }

        if (request == REQ_SAVE_FILE) {
            if (result == RESULT_OK && data != null && data.getData() != null
                    && pendingBytes != null) {
                OutputStream out = null;
                try {
                    out = getContentResolver().openOutputStream(data.getData());
                    out.write(pendingBytes);
                    out.flush();
                    toast("Saved " + pendingName);
                } catch (Exception e) {
                    toast("Could not save the file.");
                } finally {
                    if (out != null) {
                        try { out.close(); } catch (Exception ignored) { }
                    }
                }
            }
            pendingBytes = null;
            pendingName = null;
            return;
        }

        super.onActivityResult(request, result, data);
    }

    /**
     * Back closes an open form first, so a half-filled property entry is never
     * lost to a stray tap. Only then does back offer to leave the app.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            web.evaluateJavascript(
                "(function(){var o=document.getElementById('overlay');"
              + "if(o&&o.classList.contains('open')){o.classList.remove('open');"
              + "document.body.style.overflow='';return 'closed';}return 'none';})();",
                new ValueCallback<String>() {
                    public void onReceiveValue(String value) {
                        if (value == null || !value.contains("closed")) {
                            confirmExit();
                        }
                    }
                });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("Close register")
                .setMessage("Close the Evacuee Property Register?\n\n"
                        + "Saved records stay on this device.")
                .setNegativeButton("Stay", null)
                .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        finish();
                    }
                })
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
