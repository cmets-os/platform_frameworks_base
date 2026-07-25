package android.ext.integrity;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.GosPackageState;
import android.ext.PackageId;
import android.ext.settings.app.AswBlockPlayIntegrityApi;
import android.ext.settings.app.AswSpoofPlayIntegrity;
import android.ext.settings.app.AswSpoofTelephonyRegion;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Hot-updatable Play Integrity / telephony spoof store under {@code /data/misc/gms_attest_cfg}.
 * Props and telephony defaults are mirrored into {@link Settings.Global} for app-process reads.
 * Keybox material stays file-only for keystore2.
 *
 * @hide
 */
public final class IntegritySpoofStore {
    private static final String TAG = "IntegritySpoofStore";

    public static final String DIR = "/data/misc/gms_attest_cfg";
    public static final String KEYBOX_PATH = DIR + "/keybox.xml";
    public static final String PROPS_PATH = DIR + "/props.json";
    public static final String TELEPHONY_PATH = DIR + "/telephony.json";
    public static final String ENABLED_PACKAGES_PATH = DIR + "/enabled_packages";
    public static final String RELOAD_TOKEN_PATH = DIR + "/reload_token";

    private IntegritySpoofStore() {
    }

    public static void ensureDir() {
        File dir = new File(DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "failed to create " + DIR);
            return;
        }
        // Mode 0700 system; SELinux type gms_attest_cfg_file (system_server create
        // type_transitions the directory name from system_data_file).
        dir.setReadable(true, true);
        dir.setExecutable(true, true);
        dir.setWritable(true, true);
    }

    public static boolean isKeyboxPresent() {
        File f = new File(KEYBOX_PATH);
        return f.isFile() && f.length() > 0;
    }

    @Nullable
    public static IntegrityProps getProps(@NonNull Context ctx) {
        String json = Settings.Global.getString(ctx.getContentResolver(),
                Settings.Global.INTEGRITY_SPOOF_PROPS_JSON);
        if (json == null || json.isEmpty()) {
            json = readFileUtf8(PROPS_PATH);
        }
        return IntegrityProps.parse(json);
    }

    @Nullable
    public static TelephonySpoof getTelephony(@NonNull Context ctx) {
        String json = Settings.Global.getString(ctx.getContentResolver(),
                Settings.Global.INTEGRITY_SPOOF_TELEPHONY_JSON);
        if (json == null || json.isEmpty()) {
            json = readFileUtf8(TELEPHONY_PATH);
        }
        return TelephonySpoof.parse(json);
    }

    public static boolean writeAtomicUtf8(@NonNull String path, @NonNull String content) {
        ensureDir();
        File target = new File(path);
        File tmp = new File(path + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.getFD().sync();
        } catch (IOException e) {
            Log.e(TAG, "write failed: " + path, e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        if (!tmp.renameTo(target)) {
            Log.e(TAG, "rename failed: " + path);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        target.setReadable(true, true);
        //noinspection ResultOfMethodCallIgnored
        target.setWritable(true, true);
        return true;
    }

    public static boolean writeAtomicBytes(@NonNull String path, @NonNull byte[] content) {
        ensureDir();
        File target = new File(path);
        File tmp = new File(path + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(content);
            fos.getFD().sync();
        } catch (IOException e) {
            Log.e(TAG, "write failed: " + path, e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        if (!tmp.renameTo(target)) {
            Log.e(TAG, "rename failed: " + path);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        target.setReadable(true, true);
        //noinspection ResultOfMethodCallIgnored
        target.setWritable(true, true);
        return true;
    }

    public static boolean importKeybox(@NonNull byte[] xmlBytes) {
        String text = new String(xmlBytes, StandardCharsets.UTF_8);
        if (!text.contains("<AndroidAttestation>") || !text.contains("<PrivateKey")) {
            Log.e(TAG, "keybox rejected: missing AndroidAttestation/PrivateKey");
            return false;
        }
        if (!text.contains("<CertificateChain>") || !text.contains("<Certificate")) {
            Log.e(TAG, "keybox rejected: missing CertificateChain");
            return false;
        }
        // Require a PEM private key body so corrupt/incomplete imports keep the previous file.
        if (!text.contains("-----BEGIN") || !text.contains("PRIVATE KEY-----")) {
            Log.e(TAG, "keybox rejected: missing PEM private key");
            return false;
        }
        if (!text.contains("-----BEGIN CERTIFICATE-----")) {
            Log.e(TAG, "keybox rejected: missing PEM certificate");
            return false;
        }
        return writeAtomicBytes(KEYBOX_PATH, xmlBytes);
    }

    public static boolean importPropsJson(@NonNull Context ctx, @NonNull String json) {
        IntegrityProps props = IntegrityProps.parse(json);
        if (props == null || !props.isValid()) {
            Log.e(TAG, "props.json rejected");
            return false;
        }
        if (!writeAtomicUtf8(PROPS_PATH, json)) {
            return false;
        }
        Settings.Global.putString(ctx.getContentResolver(),
                Settings.Global.INTEGRITY_SPOOF_PROPS_JSON, json);
        return true;
    }

    public static boolean importTelephonyJson(@NonNull Context ctx, @NonNull String json) {
        TelephonySpoof tel = TelephonySpoof.parse(json);
        if (tel == null || !tel.isValid()) {
            Log.e(TAG, "telephony.json rejected");
            return false;
        }
        if (!writeAtomicUtf8(TELEPHONY_PATH, json)) {
            return false;
        }
        Settings.Global.putString(ctx.getContentResolver(),
                Settings.Global.INTEGRITY_SPOOF_TELEPHONY_JSON, json);
        return true;
    }

    public static boolean importTelephonyFields(@NonNull Context ctx,
            @NonNull String mcc, @NonNull String mnc,
            @NonNull String simCountryIso, @NonNull String networkCountryIso,
            @NonNull String operatorName) {
        try {
            JSONObject o = new JSONObject();
            o.put("mcc", mcc);
            o.put("mnc", mnc);
            o.put("sim_operator", mcc + mnc);
            o.put("network_operator", mcc + mnc);
            o.put("sim_country_iso", simCountryIso.toLowerCase());
            o.put("network_country_iso", networkCountryIso.toLowerCase());
            o.put("operator_name", operatorName);
            return importTelephonyJson(ctx, o.toString(2));
        } catch (JSONException e) {
            Log.e(TAG, "telephony fields encode failed", e);
            return false;
        }
    }

    /**
     * Rescan GosPackageState flags across users, refresh Global any-* markers and keystore
     * {@code enabled_packages} (effective PI spoof clients + GMS/Vending when any client is
     * enabled). Block PI wins: packages with block enabled are omitted from the keystore list
     * and do not set the any-PI marker.
     */
    public static void syncPolicy(@NonNull Context ctx) {
        ensureDir();
        boolean anyPi = false;
        boolean anyTel = false;
        List<String> enabled = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        for (int userId : getUserIds(ctx)) {
            List<ApplicationInfo> apps;
            try {
                apps = pm.getInstalledApplicationsAsUser(0, userId);
            } catch (Exception e) {
                Log.e(TAG, "package scan failed for user " + userId, e);
                continue;
            }
            for (ApplicationInfo ai : apps) {
                GosPackageState ps = GosPackageState.get(ai.packageName, userId);
                // Block wins: only effective spoof counts for markers and keystore injection.
                if (AswSpoofPlayIntegrity.I.get(ctx, userId, ai, ps)
                        && !AswBlockPlayIntegrityApi.I.get(ctx, userId, ai, ps)) {
                    anyPi = true;
                    if (!enabled.contains(ai.packageName)) {
                        enabled.add(ai.packageName);
                    }
                }
                if (AswSpoofTelephonyRegion.I.get(ctx, userId, ai, ps)) {
                    anyTel = true;
                }
            }
        }
        if (anyPi) {
            if (!enabled.contains(PackageId.GMS_CORE_NAME)) {
                enabled.add(PackageId.GMS_CORE_NAME);
            }
            if (!enabled.contains(PackageId.PLAY_STORE_NAME)) {
                enabled.add(PackageId.PLAY_STORE_NAME);
            }
        }
        Settings.Global.putInt(ctx.getContentResolver(),
                Settings.Global.INTEGRITY_SPOOF_ANY_PI, anyPi ? 1 : 0);
        Settings.Global.putInt(ctx.getContentResolver(),
                Settings.Global.INTEGRITY_SPOOF_ANY_TEL, anyTel ? 1 : 0);
        Settings.Global.putInt(ctx.getContentResolver(),
                Settings.Global.INTEGRITY_SPOOF_KEYBOX_PRESENT, isKeyboxPresent() ? 1 : 0);

        StringBuilder sb = new StringBuilder();
        for (String pkg : enabled) {
            sb.append(pkg).append('\n');
        }
        writeAtomicUtf8(ENABLED_PACKAGES_PATH, sb.toString());
    }

    /** Touch reload token and force-stop GMS + Play Store so next PI/attestation uses new config. */
    public static void applyNow(@NonNull Context ctx) {
        syncPolicy(ctx);
        writeAtomicUtf8(RELOAD_TOKEN_PATH, Long.toString(System.currentTimeMillis()));
        Settings.Global.putLong(ctx.getContentResolver(),
                Settings.Global.INTEGRITY_SPOOF_LAST_RELOAD_MS, System.currentTimeMillis());
        forceStopGmsAndVending(ctx);
    }

    public static void forceStopGmsAndVending(@NonNull Context ctx) {
        ActivityManager am = ctx.getSystemService(ActivityManager.class);
        if (am == null) {
            return;
        }
        for (int userId : getUserIds(ctx)) {
            try {
                am.forceStopPackageAsUser(PackageId.GMS_CORE_NAME, userId);
            } catch (Exception e) {
                Log.w(TAG, "forceStop GMS failed user=" + userId, e);
            }
            try {
                am.forceStopPackageAsUser(PackageId.PLAY_STORE_NAME, userId);
            } catch (Exception e) {
                Log.w(TAG, "forceStop Vending failed user=" + userId, e);
            }
        }
    }

    @NonNull
    private static int[] getUserIds(@NonNull Context ctx) {
        UserManager um = ctx.getSystemService(UserManager.class);
        if (um == null) {
            return new int[] { UserHandle.myUserId() };
        }
        try {
            List<UserHandle> handles = um.getUserHandles(true);
            if (handles == null || handles.isEmpty()) {
                return new int[] { UserHandle.myUserId() };
            }
            int[] ids = new int[handles.size()];
            for (int i = 0; i < handles.size(); i++) {
                ids[i] = handles.get(i).getIdentifier();
            }
            return ids;
        } catch (Exception e) {
            Log.w(TAG, "getUserHandles failed", e);
            return new int[] { UserHandle.myUserId() };
        }
    }

    @Nullable
    public static String readFileUtf8(@NonNull String path) {
        File f = new File(path);
        if (!f.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(f);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            Log.w(TAG, "read failed: " + path, e);
            return null;
        }
    }

    public static byte[] readStreamFully(@NonNull InputStream in) throws IOException {
        try (InputStream input = in; OutputStream ignored = OutputStream.nullOutputStream()) {
            return input.readAllBytes();
        }
    }

    /** @hide */
    public static final class IntegrityProps {
        public final String fingerprint;
        public final String manufacturer;
        public final String brand;
        public final String model;
        public final String device;
        public final String product;
        public final String securityPatch;
        public final String firstApiLevel;

        public IntegrityProps(String fingerprint, String manufacturer, String brand, String model,
                String device, String product, String securityPatch, String firstApiLevel) {
            this.fingerprint = fingerprint;
            this.manufacturer = manufacturer;
            this.brand = brand;
            this.model = model;
            this.device = device;
            this.product = product;
            this.securityPatch = securityPatch;
            this.firstApiLevel = firstApiLevel;
        }

        public boolean isValid() {
            return fingerprint != null && !fingerprint.isEmpty();
        }

        @Nullable
        public static IntegrityProps parse(@Nullable String json) {
            if (json == null || json.isEmpty()) {
                return null;
            }
            try {
                JSONObject o = new JSONObject(json);
                return new IntegrityProps(
                        o.optString("FINGERPRINT", o.optString("fingerprint", null)),
                        o.optString("MANUFACTURER", o.optString("manufacturer", null)),
                        o.optString("BRAND", o.optString("brand", null)),
                        o.optString("MODEL", o.optString("model", null)),
                        o.optString("DEVICE", o.optString("device", null)),
                        o.optString("PRODUCT", o.optString("product", null)),
                        o.optString("SECURITY_PATCH", o.optString("SECURITY_PATCH_LEVEL",
                                o.optString("security_patch", null))),
                        o.optString("FIRST_API_LEVEL", o.optString("first_api_level", null)));
            } catch (JSONException e) {
                Log.e(TAG, "props parse failed", e);
                return null;
            }
        }
    }

    /** @hide */
    public static final class TelephonySpoof {
        public final String mcc;
        public final String mnc;
        public final String simOperator;
        public final String networkOperator;
        public final String simCountryIso;
        public final String networkCountryIso;
        public final String operatorName;

        public TelephonySpoof(String mcc, String mnc, String simOperator, String networkOperator,
                String simCountryIso, String networkCountryIso, String operatorName) {
            this.mcc = mcc;
            this.mnc = mnc;
            this.simOperator = simOperator;
            this.networkOperator = networkOperator;
            this.simCountryIso = simCountryIso;
            this.networkCountryIso = networkCountryIso;
            this.operatorName = operatorName;
        }

        public boolean isValid() {
            return simOperator != null && !simOperator.isEmpty()
                    && simCountryIso != null && !simCountryIso.isEmpty();
        }

        @Nullable
        public static TelephonySpoof parse(@Nullable String json) {
            if (json == null || json.isEmpty()) {
                return null;
            }
            try {
                JSONObject o = new JSONObject(json);
                String mcc = o.optString("mcc", "");
                String mnc = o.optString("mnc", "");
                String simOp = o.optString("sim_operator", "");
                if (simOp.isEmpty() && !mcc.isEmpty() && !mnc.isEmpty()) {
                    simOp = mcc + mnc;
                }
                String netOp = o.optString("network_operator", simOp);
                return new TelephonySpoof(
                        mcc,
                        mnc,
                        simOp,
                        netOp,
                        o.optString("sim_country_iso", "").toLowerCase(),
                        o.optString("network_country_iso",
                                o.optString("sim_country_iso", "")).toLowerCase(),
                        o.optString("operator_name", ""));
            } catch (JSONException e) {
                Log.e(TAG, "telephony parse failed", e);
                return null;
            }
        }
    }
}
