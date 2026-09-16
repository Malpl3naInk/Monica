package takagi.ru.monica.credentialexchange;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A separate UID; plain Java because test APK processes do not include the app's Kotlin runtime. */
public final class TransferReceiverTestProvider extends ContentProvider {
    private static final String RECIPIENT = "takagi.ru.monica";
    private static final String AUTHORITY = "takagi.ru.monica.test.exchange-receiver";
    private static final int FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    @Override public boolean onCreate() { return true; }

    private void requireTestCaller() {
        try {
            int uid = Binder.getCallingUid();
            if (uid != Process.myUid() && uid != getContext().getPackageManager().getApplicationInfo(RECIPIENT, 0).uid) {
                throw new SecurityException("Unexpected test caller");
            }
        } catch (PackageManager.NameNotFoundException error) {
            throw new SecurityException(error);
        }
    }

    private File file(String token) {
        if (!UUID.fromString(token).toString().equals(token)) throw new IllegalArgumentException("Invalid test token");
        return new File(getContext().getCacheDir(), "exchange-receiver-" + token + ".json");
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        requireTestCaller();
        File file = file(arg);
        Uri uri = Uri.parse("content://" + AUTHORITY + "/" + arg);
        if ("create".equals(method)) {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write("untouched".getBytes(StandardCharsets.UTF_8));
            } catch (IOException error) { throw new IllegalStateException(error); }
            getContext().grantUriPermission(RECIPIENT, uri, FLAGS);
        } else if ("release".equals(method)) {
            getContext().revokeUriPermission(uri, FLAGS);
            file.delete();
        } else { throw new IllegalArgumentException("Unsupported test operation"); }
        return new Bundle();
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        requireTestCaller();
        if (!AUTHORITY.equals(uri.getAuthority()) || uri.getPathSegments().size() != 1) {
            throw new FileNotFoundException("Invalid test URI");
        }
        return ParcelFileDescriptor.open(file(uri.getLastPathSegment()), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
}
