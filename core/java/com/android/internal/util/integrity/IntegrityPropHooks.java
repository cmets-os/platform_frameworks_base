package com.android.internal.util.integrity;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.ext.integrity.IntegritySpoofPolicy;
import android.ext.integrity.IntegritySpoofStore;
import android.ext.integrity.IntegritySpoofStore.IntegrityProps;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * LineageOS-style Build field imitation for processes in the PI spoof set.
 * v1 is Java/framework only (no bionic prop hooks).
 *
 * @hide
 */
public final class IntegrityPropHooks {
    private static final String TAG = "IntegrityPropHooks";

    private IntegrityPropHooks() {
    }

    public static void setProps(@NonNull Context context) {
        try {
            ApplicationInfo ai = context.getApplicationInfo();
            if (ai == null) {
                return;
            }
            if (!IntegritySpoofPolicy.isPlayIntegritySpoofEnabled(
                    context, ai.packageName, context.getUserId())) {
                return;
            }
            IntegrityProps props = IntegritySpoofStore.getProps(context);
            if (props == null || !props.isValid()) {
                return;
            }
            apply(props);
            Log.i(TAG, "applied integrity props for " + ai.packageName);
        } catch (Throwable t) {
            Log.w(TAG, "setProps failed", t);
        }
    }

    private static void apply(IntegrityProps p) {
        setBuildField("FINGERPRINT", p.fingerprint);
        setBuildField("MANUFACTURER", p.manufacturer);
        setBuildField("BRAND", p.brand);
        setBuildField("MODEL", p.model);
        setBuildField("DEVICE", p.device);
        setBuildField("PRODUCT", p.product);
        if (p.securityPatch != null && !p.securityPatch.isEmpty()) {
            setNestedBuildField(Build.VERSION.class, "SECURITY_PATCH", p.securityPatch);
        }
        if (p.firstApiLevel != null && !p.firstApiLevel.isEmpty()) {
            // Best-effort mirror for apps that read the prop from Java SystemProperties.
            try {
                SystemProperties.set("persist.sys.integrity.first_api_level", p.firstApiLevel);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void setBuildField(String name, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        setNestedBuildField(Build.class, name, value);
    }

    private static void setNestedBuildField(Class<?> clazz, String name, String value) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("accessFlags");
            // On ART, clearing FINAL via modifiers is not always available; try value set anyway.
            try {
                modifiers.setAccessible(true);
                modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (Throwable ignored) {
            }
            field.set(null, value);
        } catch (Throwable t) {
            Log.w(TAG, "failed to set " + clazz.getSimpleName() + "." + name, t);
        }
    }
}
