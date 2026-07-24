package android.ext.settings.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateFlag;

/** @hide */
public class AswSpoofPlayIntegrity extends AppSwitch {
    public static final AswSpoofPlayIntegrity I = new AswSpoofPlayIntegrity();

    private AswSpoofPlayIntegrity() {
        gosPsFlag = GosPackageStateFlag.SPOOF_PLAY_INTEGRITY;
    }

    @Override
    protected boolean getDefaultValueInner(Context ctx, int userId, ApplicationInfo appInfo,
                                           GosPackageState ps, StateInfo si) {
        return false;
    }
}
