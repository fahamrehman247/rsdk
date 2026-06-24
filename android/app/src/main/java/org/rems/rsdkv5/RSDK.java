package org.rems.rsdkv5;

import static android.os.Build.VERSION.SDK_INT;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import com.google.androidgamesdk.GameActivity;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.documentfile.provider.DocumentFile;

public class RSDK extends GameActivity {
    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("RetroEngine");
    }

    private static Uri basePath = null;
    private static OutputStream log = null;
    private static String pathString = null;

    private static ContentProviderClient cpc = null;

    private LoadingIcon loadingIcon;

    public int pixWidth;
    public int pixHeight;

    public void setPixSize(int width, int height) {
        pixWidth = width;
        pixHeight = height;
    }

    private void hideSystemUI() {
        if (SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), decorView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.hide(WindowInsetsCompat.Type.displayCutout());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Automatically target the isolated app sandbox files directory
        java.io.File sandboxDir = this.getExternalFilesDir(null);
        if (sandboxDir != null && !sandboxDir.exists()) {
            sandboxDir.mkdirs();
        }
        
        // Convert the clean local sandbox path to a standard Android URI format
        basePath = Uri.fromFile(sandboxDir);
        pathString = sandboxDir.getAbsolutePath() + "/";

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        hideSystemUI();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cpc = getContentResolver().acquireContentProviderClient(basePath.getAuthority());

        try {
            ParcelFileDescriptor.adoptFd(getFD("log.txt".getBytes(), (byte)'a')).close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            DocumentFile docfile = DocumentFile.fromTreeUri(this, basePath).findFile("log.txt");
            if (docfile != null) {
                Uri uri = docfile.getUri();
                if (log == null)
                    log = getContentResolver().openOutputStream(uri, "wa");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static class RecursiveIterator {
        private static final HashMap<byte[], RecursiveIterator> iterators = new HashMap<>();
        private final Stack<Cursor> docs = new Stack<>();
        private final Uri base;
        private final String path;
        private final ContentResolver resolver;

        public RecursiveIterator(@NonNull ContentResolver resolver, String path, Uri uri) {
            this.path = path;
            base = uri;
            this.resolver = resolver;
            this.docs.add(resolver.query(
                    DocumentsContract.buildChildDocumentsUriUsingTree(base,
                            DocumentsContract.getDocumentId(base)
                    ), new String[]{
                            Document.COLUMN_DOCUMENT_ID,
                            Document.COLUMN_MIME_TYPE
                    }, null, null, null));
        }

        public static RecursiveIterator get(ContentResolver resolver, byte[] path, Uri uri) {
            if (iterators.get(path) != null)
                return iterators.get(path);
            RecursiveIterator iter = new RecursiveIterator(resolver, new String(path), uri);
            iterators.put(path, iter);
            return iter;
        }

        public String next() {
            Cursor c = null;
            try {
                c = docs.peek();
                if (!c.moveToNext()) throw new NullPointerException();
                Uri uri = DocumentsContract.buildDocumentUriUsingTree(base, c.getString(0));
                if (c.getString(1).equals(Document.MIME_TYPE_DIR)) {
                    docs.push(resolver.query(
                            DocumentsContract.buildChildDocumentsUriUsingTree(
                                    uri, DocumentsContract.getDocumentId(uri)
                            ), new String[]{
                                    Document.COLUMN_DOCUMENT_ID,
                                    Document.COLUMN_MIME_TYPE
                            }, null, null, null));
                    return next();
                }
                String seg = uri.getLastPathSegment();
                return seg.substring(seg.indexOf(path));
            } catch (EmptyStackException e) {
                iterators.remove(path.getBytes());
                return null;
            } catch (Exception e) {
                c.close();
                docs.pop();
                return next();
            }
        }
    }

    public String fsRecurseIter(byte[] path) {
        Uri uri = basePath.buildUpon().encodedPath(pathString + Uri.encode(new String(path))).build();
        RecursiveIterator iter = RecursiveIterator.get(getContentResolver(), path, uri);
        return iter.next();
    }

    public void showLoadingIcon() {
        loadingIcon.show();
    }

    public void hideLoadingIcon() {
        loadingIcon.hide();
    }
}
