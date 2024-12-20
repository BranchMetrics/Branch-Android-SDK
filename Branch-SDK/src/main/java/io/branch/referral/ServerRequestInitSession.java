package io.branch.referral;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import io.branch.coroutines.DeviceSignalsKt;
import io.branch.referral.validators.DeepLinkRoutingValidator;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

/**
 * <p>
 * Abstract for Session init request. All request which do initialize session should extend from this.
 * </p>
 */
public abstract class ServerRequestInitSession extends ServerRequest {
    static final String ACTION_OPEN = "open";
    static final String ACTION_INSTALL = "install";
    private final Context context_;

    private static final int STATE_FRESH_INSTALL = 0;
    private static final int STATE_NO_CHANGE = 1;
    private static final int STATE_UPDATE = 2;

    static final String INITIATED_BY_CLIENT = "INITIATED_BY_CLIENT";

    Branch.BranchReferralInitListener callback_;
    boolean initiatedByClient;

    ServerRequestInitSession(Context context, Defines.RequestPath requestPath, boolean isAutoInitialization) {
        super(context, requestPath);
        context_ = context;
        initiatedByClient = !isAutoInitialization;
    }

    ServerRequestInitSession(Defines.RequestPath requestPath, JSONObject post, Context context, boolean isAutoInitialization) {
        super(requestPath, post, context);
        context_ = context;
        initiatedByClient = !isAutoInitialization;
    }

    @Override
    protected void setPost(JSONObject post) throws JSONException {
        super.setPost(post);
        prefHelper_.loadPartnerParams(post);

        String appVersion = DeviceInfo.getInstance().getAppVersion();
        if (!DeviceInfo.isNullOrEmptyOrBlank(appVersion)) {
            post.put(Defines.Jsonkey.AppVersion.getKey(), appVersion);
        }
        if(!TextUtils.isEmpty(prefHelper_.getInitialReferrer()) && !prefHelper_.getInitialReferrer().equals(PrefHelper.NO_STRING_VALUE)) {
            post.put(Defines.Jsonkey.InitialReferrer.getKey(), prefHelper_.getInitialReferrer());
        }

        updateInstallStateAndTimestamps(post);
        updateEnvironment(context_, post);

        String identity = Branch.installDeveloperId;

        if(!TextUtils.isEmpty(identity) && !identity.equals(PrefHelper.NO_STRING_VALUE)){
            post.put(Defines.Jsonkey.Identity.getKey(), identity);
        }
    }

    @Override
    protected boolean shouldUpdateLimitFacebookTracking() {
        return true;
    }

    @Override
    protected boolean shouldAddDMAParams() {
        return true;
    }

    public abstract String getRequestActionName();

    static boolean isInitSessionAction(String actionName) {
        boolean isInitSessionAction = false;
        if (actionName != null) {
            isInitSessionAction = (actionName.equalsIgnoreCase(ACTION_OPEN) || actionName.equalsIgnoreCase(ACTION_INSTALL));
        }
        return isInitSessionAction;
    }
    @Override
    public void onRequestSucceeded(ServerResponse response, Branch branch) {
        Branch.getInstance().unlockSDKInitWaitLock();
    }

    void onInitSessionCompleted(ServerResponse response, Branch branch) {
        DeepLinkRoutingValidator.validate(branch.currentActivityReference_);
        branch.updateSkipURLFormats();

        // Run this after session init, ahead of any V2 event, in the background.
        if (!Branch.userAgentSync && TextUtils.isEmpty(Branch._userAgentString)) {
            DeviceSignalsKt.getUserAgentAsync(branch.getApplicationContext(), new Continuation<String>() {
                @NonNull
                @Override
                public CoroutineContext getContext() {
                    return EmptyCoroutineContext.INSTANCE;
                }

                @Override
                public void resumeWith(@NonNull Object o) {
                    if (o != null) {
                        BranchLogger.v("onInitSessionCompleted resumeWith userAgent " + o + " on thread " + Thread.currentThread().getName());
                        Branch._userAgentString = (String) o;
                    }

                    Branch.getInstance().requestQueue_.unlockProcessWait(PROCESS_WAIT_LOCK.USER_AGENT_STRING_LOCK);
                    Branch.getInstance().requestQueue_.processNextQueueItem("getUserAgentAsync resumeWith");
                }
            });
        }
        else {
            BranchLogger.v("Deferring userAgent string call for sync retrieval");
        }

        BranchLogger.v("onInitSessionCompleted on thread " + Thread.currentThread().getName());
    }

    /**
     * Update link referrer params.
     * For link clicked installs, link click id is updated via the Google Play Referrer lib.
     *
     * @see StoreReferrer
     * @see Branch#setPlayStoreReferrerCheckTimeout(long)
     */
    void updateLinkReferrerParams() {
        // Add link identifier if present

        //TODO: Remove this
        String linkIdentifier = prefHelper_.getLinkClickIdentifier();
        if (!linkIdentifier.equals(PrefHelper.NO_STRING_VALUE)) {
            try {
                getPost().put(Defines.Jsonkey.LinkIdentifier.getKey(), linkIdentifier);
            } catch (JSONException e) {
                BranchLogger.w("Caught JSONException " + e.getMessage());
            }
        }
        // Add Google search install referrer if present
        String googleSearchInstallIdentifier = prefHelper_.getGoogleSearchInstallIdentifier();
        if (!googleSearchInstallIdentifier.equals(PrefHelper.NO_STRING_VALUE)) {
            try {
                getPost().put(Defines.Jsonkey.GoogleSearchInstallReferrer.getKey(), googleSearchInstallIdentifier);
            } catch (JSONException e) {
                BranchLogger.w("Caught JSONException " + e.getMessage());
            }
        }
        // Add Google play raw referrer if present
        String googlePlayReferrer = prefHelper_.getAppStoreReferrer();
        if (!googlePlayReferrer.equals(PrefHelper.NO_STRING_VALUE)) {
            try {
                getPost().put(Defines.Jsonkey.GooglePlayInstallReferrer.getKey(), googlePlayReferrer);
            } catch (JSONException e) {
                BranchLogger.w("Caught JSONException " + e.getMessage());
            }
        }

        String appStore = prefHelper_.getAppStoreSource();
        if(!PrefHelper.NO_STRING_VALUE.equals(appStore)) {
            try {
                //Handle Meta Install Referrer by setting store as Google Play Store and adding is_meta_click_through
                if (appStore.equals(Defines.Jsonkey.Meta_Install_Referrer.getKey())) {
                    getPost().put(Defines.Jsonkey.App_Store.getKey(), Defines.Jsonkey.Google_Play_Store.getKey());
                    getPost().put(Defines.Jsonkey.Is_Meta_Click_Through.getKey(), prefHelper_.getIsMetaClickThrough());
                } else {
                    getPost().put(Defines.Jsonkey.App_Store.getKey(), appStore);
                }
            } catch (JSONException e) {
                BranchLogger.w("Caught JSONException " + e.getMessage());
            }
        }

        // Check for Conversion from instant app to full app
        if (prefHelper_.isFullAppConversion()) {
            try {
                getPost().put(Defines.Jsonkey.AndroidAppLinkURL.getKey(), prefHelper_.getAppLink());
                getPost().put(Defines.Jsonkey.IsFullAppConv.getKey(), true);
            } catch (JSONException e) {
                BranchLogger.w("Caught JSONException " + e.getMessage());
            }
        }
    }

    @Override
    public void onPreExecute() {
        super.onPreExecute();
        JSONObject post = getPost();
        try {
            if (!prefHelper_.getAppLink().equals(PrefHelper.NO_STRING_VALUE)) {
                post.put(Defines.Jsonkey.AndroidAppLinkURL.getKey(), prefHelper_.getAppLink());
            }
            if (!prefHelper_.getPushIdentifier().equals(PrefHelper.NO_STRING_VALUE)) {
                post.put(Defines.Jsonkey.AndroidPushIdentifier.getKey(), prefHelper_.getPushIdentifier());
            }
            // External URI or Extras if exist
            if (!prefHelper_.getExternalIntentUri().equals(PrefHelper.NO_STRING_VALUE)) {
                post.put(Defines.Jsonkey.External_Intent_URI.getKey(), prefHelper_.getExternalIntentUri());
            }
            if (!prefHelper_.getExternalIntentExtra().equals(PrefHelper.NO_STRING_VALUE)) {
                post.put(Defines.Jsonkey.External_Intent_Extra.getKey(), prefHelper_.getExternalIntentExtra());
            }

        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException " + e.getMessage());
        }

        // Re-enables auto session initialization, note that we don't care if the request succeeds
        Branch.expectDelayedSessionInitialization(false);
    }

    /*
     * Method to determine the install/update/no_change state along with the timestamps. Note that the
     * back end has its own logic to interpret these (that logic includes 'reinstall' state).
     * https://branch.atlassian.net/wiki/spaces/EN/pages/798786098/Open+Install+Reinstall+Logic+from+API+Open
     *
     * The Original install time will have a the very first install time only if the app allows preference back up.
     * Apps that need to distinguish between a fresh install and re-install need to allow backing up of preferences.
     *
     * @param post Post body for init request which need to be updated
     * @throws JSONException when there is any exception on adding time stamps or update state
     */
    private void updateInstallStateAndTimestamps(JSONObject post) throws JSONException {
        // Default, just a regular open
        int installOrUpdateState = STATE_NO_CHANGE;

        String currAppVersion = DeviceInfo.getInstance().getAppVersion();

        long updateBufferTime = (24 * 60 * 60 * 1000); // Update buffer time is a day.
        long firstInstallTime = DeviceInfo.getInstance().getFirstInstallTime();
        long lastUpdateTime = DeviceInfo.getInstance().getLastUpdateTime();

        if (PrefHelper.NO_STRING_VALUE.equals(prefHelper_.getAppVersion())) {
            // if no app version is in storage, this must be the first time Branch is here, register an install
            installOrUpdateState = STATE_FRESH_INSTALL;

            // However, if package info tells us that last update time is not the same as first install time
            // then, from the users perspective, this is an `update` version of the app that happens to have
            // Branch in it for the first time, so we record the session as 'update'.
            if ((lastUpdateTime - firstInstallTime) >= updateBufferTime) {
                installOrUpdateState = STATE_UPDATE;
            }
        } else if (!prefHelper_.getAppVersion().equals(currAppVersion)) {
            // if the current app version doesn't match the stored version, then it's an update
            installOrUpdateState = STATE_UPDATE;
        }

        post.put(Defines.Jsonkey.Update.getKey(), installOrUpdateState);
        post.put(Defines.Jsonkey.FirstInstallTime.getKey(), firstInstallTime);
        post.put(Defines.Jsonkey.LastUpdateTime.getKey(), lastUpdateTime);

        // only available when backing up of prefs is allowed (default on Android but users can override with allowBackup=false in manifest)
        long originalInstallTime = prefHelper_.getLong(PrefHelper.KEY_ORIGINAL_INSTALL_TIME);
        if (originalInstallTime == 0) {
            originalInstallTime = firstInstallTime;
            prefHelper_.setLong(PrefHelper.KEY_ORIGINAL_INSTALL_TIME, firstInstallTime);
        }
        post.put(Defines.Jsonkey.OriginalInstallTime.getKey(), originalInstallTime);

        long lastKnownUpdateTime = prefHelper_.getLong(PrefHelper.KEY_LAST_KNOWN_UPDATE_TIME);
        if (lastKnownUpdateTime < lastUpdateTime) {
            prefHelper_.setLong(PrefHelper.KEY_PREVIOUS_UPDATE_TIME, lastKnownUpdateTime);
            prefHelper_.setLong(PrefHelper.KEY_LAST_KNOWN_UPDATE_TIME, lastUpdateTime);
        }
        post.put(Defines.Jsonkey.PreviousUpdateTime.getKey(), prefHelper_.getLong(PrefHelper.KEY_PREVIOUS_UPDATE_TIME));
    }

    @Override
    protected boolean prepareExecuteWithoutTracking() {
        JSONObject post = getPost();
        if ((post.has(Defines.Jsonkey.AndroidAppLinkURL.getKey())
                || post.has(Defines.Jsonkey.AndroidPushIdentifier.getKey())
                || post.has(Defines.Jsonkey.LinkIdentifier.getKey()))) {

            post.remove(Defines.Jsonkey.RandomizedDeviceToken.getKey());
            post.remove(Defines.Jsonkey.RandomizedBundleToken.getKey());
            post.remove(Defines.Jsonkey.External_Intent_Extra.getKey());
            post.remove(Defines.Jsonkey.External_Intent_URI.getKey());
            post.remove(Defines.Jsonkey.FirstInstallTime.getKey());
            post.remove(Defines.Jsonkey.LastUpdateTime.getKey());
            post.remove(Defines.Jsonkey.OriginalInstallTime.getKey());
            post.remove(Defines.Jsonkey.PreviousUpdateTime.getKey());
            post.remove(Defines.Jsonkey.InstallBeginTimeStamp.getKey());
            post.remove(Defines.Jsonkey.ClickedReferrerTimeStamp.getKey());
            post.remove(Defines.Jsonkey.HardwareID.getKey());
            post.remove(Defines.Jsonkey.IsHardwareIDReal.getKey());
            post.remove(Defines.Jsonkey.LocalIP.getKey());
            post.remove(Defines.Jsonkey.ReferrerGclid.getKey());
            post.remove(Defines.Jsonkey.Identity.getKey());
            post.remove(Defines.Jsonkey.AnonID.getKey());
            try {
                post.put(Defines.Jsonkey.TrackingDisabled.getKey(), true);
            } catch (JSONException e) {
                BranchLogger.w("Caught JSONException " + e.getMessage());
            }
            return true;
        } else {
            return super.prepareExecuteWithoutTracking();
        }
    }

    @Override
    public JSONObject toJSON() {
        JSONObject r = super.toJSON();
        try {
            r.put(INITIATED_BY_CLIENT, initiatedByClient);
        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException " + e.getMessage());
        }
        return r;
    }
}
