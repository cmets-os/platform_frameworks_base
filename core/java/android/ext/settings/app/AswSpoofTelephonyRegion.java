package android.ext.settings.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateFlag;

/** @hide */
public class AswSpoofTelephonyRegion extends AppSwitch {
    public static final AswSpoofTelephonyRegion I = new AswSpoofTelephonyRegion();

    private AswSpoofTelephonyRegion() {
        gosPsFlag = GosPackageStateFlag.SPOOF_TELEPHONY_REGION;
    }

    @Override
    protected boolean getDefaultValueInner(Context ctx, int userId, ApplicationInfo appInfo,
                                           GosPackageState ps, StateInfo si) {
        return false;
    }
}
