package io.branch.referral;

import static io.branch.referral.BranchError.ERR_IMPROPER_REINITIALIZATION;
import static io.branch.referral.BranchPreinstall.getPreinstallSystemData;
import static io.branch.referral.BranchUtil.isTestModeEnabled;
import static io.branch.referral.PrefHelper.isValidBranchKey;
import static io.branch.referral.util.DependencyUtilsKt.billingGooglePlayClass;
import static io.branch.referral.util.DependencyUtilsKt.classExists;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.android.billingclient.api.Purchase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.branch.indexing.BranchUniversalObject;
import io.branch.interfaces.IBranchLoggingCallbacks;
import io.branch.referral.Defines.PreinstallKey;
import io.branch.referral.ServerRequestGetLATD.BranchLastAttributedTouchDataListener;
import io.branch.referral.network.BranchRemoteInterface;
import io.branch.referral.network.BranchRemoteInterfaceUrlConnection;
import io.branch.referral.util.BRANCH_STANDARD_EVENT;
import io.branch.referral.util.BranchEvent;
import io.branch.referral.util.LinkProperties;
import io.branch.utilities.LinkParsingKt;
import io.branch.utilities.UniversalResourceAnalyser;

/**
 * <p>
 * The core object required when using Branch SDK. You should declare an object of this type at
 * the class-level of each Activity or Fragment that you wish to use Branch functionality within.
 * </p>
 * <p>
 * Normal instantiation of this object would look like this:
 * </p>
 * <!--
 * <pre style="background:#fff;padding:10px;border:2px solid silver;">
 * Branch.getInstance(this.getApplicationContext()) // from an Activity
 * Branch.getInstance(getActivity().getApplicationContext())    // from a Fragment
 * </pre>
 * -->
 */

public class Branch {

    private static final String BRANCH_LIBRARY_VERSION = "io.branch.sdk.android:library:" + Branch.getSdkVersionNumber();
    private static final String GOOGLE_VERSION_TAG = "!SDK-VERSION-STRING!" + ":" + BRANCH_LIBRARY_VERSION;

    /**
     * Hard-coded {@link String} that denotes a {@link BranchLinkData}; applies to links that
     * are shared with others directly as a user action, via social media for instance.
     */
    public static final String FEATURE_TAG_SHARE = "share";

    /**
     * The redirect URL provided when the link is handled by a desktop client.
     */
    public static final String REDIRECT_DESKTOP_URL = "$desktop_url";
    
    /**
     * The redirect URL provided when the link is handled by an Android device.
     */
    public static final String REDIRECT_ANDROID_URL = "$android_url";
    
    /**
     * The redirect URL provided when the link is handled by an iOS device.
     */
    public static final String REDIRECT_IOS_URL = "$ios_url";
    
    /**
     * The redirect URL provided when the link is handled by a large form-factor iOS device such as
     * an iPad.
     */
    public static final String REDIRECT_IPAD_URL = "$ipad_url";
    
    /**
     * The redirect URL provided when the link is handled by an Amazon Fire device.
     */
    public static final String REDIRECT_FIRE_URL = "$fire_url";
    
    /**
     * The redirect URL provided when the link is handled by a Blackberry device.
     */
    public static final String REDIRECT_BLACKBERRY_URL = "$blackberry_url";
    
    /**
     * The redirect URL provided when the link is handled by a Windows Phone device.
     */
    public static final String REDIRECT_WINDOWS_PHONE_URL = "$windows_phone_url";
    
    /**
     * Open Graph: The title of your object as it should appear within the graph, e.g., "The Rock".
     *
     * @see <a href="http://ogp.me/#metadata">Open Graph - Basic Metadata</a>
     */
    public static final String OG_TITLE = "$og_title";
    
    /**
     * The description of the object to appear in social media feeds that use
     * Facebook's Open Graph specification.
     *
     * @see <a href="http://ogp.me/#metadata">Open Graph - Basic Metadata</a>
     */
    public static final String OG_DESC = "$og_description";
    
    /**
     * An image URL which should represent your object to appear in social media feeds that use
     * Facebook's Open Graph specification.
     *
     * @see <a href="http://ogp.me/#metadata">Open Graph - Basic Metadata</a>
     */
    public static final String OG_IMAGE_URL = "$og_image_url";
    
    /**
     * A URL to a video file that complements this object.
     *
     * @see <a href="http://ogp.me/#metadata">Open Graph - Basic Metadata</a>
     */
    public static final String OG_VIDEO = "$og_video";
    
    /**
     * The canonical URL of your object that will be used as its permanent ID in the graph.
     *
     * @see <a href="http://ogp.me/#metadata">Open Graph - Basic Metadata</a>
     */
    public static final String OG_URL = "$og_url";
    
    /**
     * Unique identifier for the app in use.
     */
    public static final String OG_APP_ID = "$og_app_id";
    
    /**
     * {@link String} value denoting the deep link path to override Branch's default one. By
     * default, Branch will use yourapp://open?link_click_id=12345. If you specify this key/value,
     * Branch will use yourapp://'$deeplink_path'?link_click_id=12345
     */
    public static final String DEEPLINK_PATH = "$deeplink_path";
    
    /**
     * {@link String} value indicating whether the link should always initiate a deep link action.
     * By default, unless overridden on the dashboard, Branch will only open the app if they are
     * 100% sure the app is installed. This setting will cause the link to always open the app.
     * Possible values are "true" or "false"
     */
    public static final String ALWAYS_DEEPLINK = "$always_deeplink";

    /**
     * An {@link Integer} value indicating the link type. In this case, the link can be used an
     * unlimited number of times.
     */
    public static final int LINK_TYPE_UNLIMITED_USE = 0;
    
    /**
     * An {@link Integer} value indicating the link type. In this case, the link can be used only
     * once. After initial use, subsequent attempts will not validate.
     */
    public static final int LINK_TYPE_ONE_TIME_USE = 1;

    /**
     * If true, instantiate a new webview instance ui thread to retrieve user agent string
     */
    static boolean userAgentSync;

    /**
     * Package private user agent string cached to save on repeated queries
     */
    public static String _userAgentString = "";

    /* Json object containing key-value pairs for debugging deep linking */
    private JSONObject deeplinkDebugParams_;
    
    private static boolean disableDeviceIDFetch_;

    static boolean bypassWaitingForIntent_ = false;
    
    private static boolean bypassCurrentActivityIntentState_ = false;

    static boolean disableAutoSessionInitialization;

    static boolean referringLinkAttributionForPreinstalledAppsEnabled = false;
    
    /**
     * <p>A {@link Branch} object that is instantiated on init and holds the singleton instance of
     * the class during application runtime.</p>
     */
    private static Branch branchReferral_;

    private BranchRemoteInterface branchRemoteInterface_;
    final PrefHelper prefHelper_;
    private final DeviceInfo deviceInfo_;
    private final BranchPluginSupport branchPluginSupport_;
    private final Context context_;

    private final BranchQRCodeCache branchQRCodeCache_;

    public final ServerRequestQueue requestQueue_;

    final ConcurrentHashMap<BranchLinkData, String> linkCache_ = new ConcurrentHashMap<>();

    /* Set to true when {@link Activity} life cycle callbacks are registered. */
    private static boolean isActivityLifeCycleCallbackRegistered_ = false;


    /* Enumeration for defining session initialisation state. */
    enum SESSION_STATE {
        INITIALISED, INITIALISING, UNINITIALISED
    }
    
    
    enum INTENT_STATE {
        PENDING,
        READY
    }

    /* Holds the current intent state. Default is set to PENDING. */
    private INTENT_STATE intentState_ = INTENT_STATE.PENDING;
    
    /* Holds the current Session state. Default is set to UNINITIALISED. */
    SESSION_STATE initState_ = SESSION_STATE.UNINITIALISED;

    /* */
    static boolean deferInitForPluginRuntime = false;

    /* Flag to indicate if the `v1/close` is expected by the server at the end of this session. */
    public boolean closeRequestNeeded = false;

    /* Instance  of share link manager to share links automatically with third party applications. */
    private ShareLinkManager shareLinkManager_;
    
    /* The current activity instance for the application.*/
    WeakReference<Activity> currentActivityReference_;
    
    /* Key for Auto Deep link param. The activities which need to automatically deep linked should define in this in the activity metadata. */
    private static final String AUTO_DEEP_LINK_KEY = "io.branch.sdk.auto_link_keys";
    
    /* Path for $deeplink_path or $android_deeplink_path to auto deep link. The activities which need to automatically deep linked should define in this in the activity metadata. */
    private static final String AUTO_DEEP_LINK_PATH = "io.branch.sdk.auto_link_path";
    
    /* Key for disabling auto deep link feature. Setting this to true in manifest will disable auto deep linking feature. */
    private static final String AUTO_DEEP_LINK_DISABLE = "io.branch.sdk.auto_link_disable";
    
    /*Key for defining a request code for an activity. should be added as a metadata for an activity. This is used as a request code for launching a an activity on auto deep link. */
    private static final String AUTO_DEEP_LINK_REQ_CODE = "io.branch.sdk.auto_link_request_code";
    
    /* Request code  used to launch and activity on auto deep linking unless DEF_AUTO_DEEP_LINK_REQ_CODE is not specified for teh activity in manifest.*/
    private static final int DEF_AUTO_DEEP_LINK_REQ_CODE = 1501;

    private static final int LATCH_WAIT_UNTIL = 2500; //used for getLatestReferringParamsSync and getFirstReferringParamsSync, fail after this many milliseconds

    public static String installDeveloperId = null;

    CountDownLatch getFirstReferringParamsLatch = null;
    CountDownLatch getLatestReferringParamsLatch = null;

    private boolean isInstantDeepLinkPossible = false;
    private BranchActivityLifecycleObserver activityLifeCycleObserver;
    /* Flag to turn on or off instant deeplinking feature. IDL is disabled by default */
    private static boolean enableInstantDeepLinking = false;
    private final TrackingController trackingController;

    // Variables for reporting plugin type and version, plus helps us make data driven decisions.
    private static String pluginVersion = null;
    private static String pluginName = null;

    private BranchReferralInitListener deferredCallback;
    private Uri deferredUri;
    private InitSessionBuilder deferredSessionBuilder;

    //Experimental
    private ServerRequestInitSession latestInitRequest;

    /**
     * <p>The main constructor of the Branch class is private because the class uses the Singleton
     * pattern.</p>
     * <p>Use {@link #getAutoInstance(Context)} method when instantiating.</p>
     *
     * @param context A {@link Context} from which this call was made.
     */
    private Branch(@NonNull Context context) {
        context_ = context;
        prefHelper_ = PrefHelper.getInstance(context);
        trackingController = new TrackingController(context);
        branchRemoteInterface_ = new BranchRemoteInterfaceUrlConnection(this);
        deviceInfo_ = new DeviceInfo(context);
        branchPluginSupport_ = new BranchPluginSupport(context);
        branchQRCodeCache_ = new BranchQRCodeCache(context);
        requestQueue_ = ServerRequestQueue.getInstance(context);
    }

    /**
     * <p>Singleton method to return the pre-initialised object of the type {@link Branch}.
     * Make sure your app is instantiating {@link BranchApp} before calling this method
     * or you have created an instance of Branch already by calling getInstance(Context ctx).</p>
     *
     * @return An initialised singleton {@link Branch} object
     */
    synchronized public static Branch getInstance() {
        if (branchReferral_ == null) {
            BranchLogger.v("Branch instance is not created yet. Make sure you call getAutoInstance(Context).");
        }
        return branchReferral_;
    }

    synchronized private static Branch initBranchSDK(@NonNull Context context, String branchKey) {
        if (branchReferral_ != null) {
            BranchLogger.w("Warning, attempted to reinitialize Branch SDK singleton!");
            return branchReferral_;
        }
        branchReferral_ = new Branch(context.getApplicationContext());

        if (TextUtils.isEmpty(branchKey)) {
            BranchLogger.w("Warning: Please enter your branch_key in your project's Manifest file!");
            branchReferral_.prefHelper_.setBranchKey(PrefHelper.NO_STRING_VALUE);
        } else {
            branchReferral_.prefHelper_.setBranchKey(branchKey);
        }

        /* If {@link Application} is instantiated register for activity life cycle events. */
        if (context instanceof Application) {
            branchReferral_.setActivityLifeCycleObserver((Application) context);
        }

        return branchReferral_;
    }

    /**
     * <p>Singleton method to return the pre-initialised, or newly initialise and return, a singleton
     * object of the type {@link Branch}.</p>
     * <p>Use this whenever you need to call a method directly on the {@link Branch} object.</p>
     *
     * @param context A {@link Context} from which this call was made.
     * @return An initialised {@link Branch} object, either fetched from a pre-initialised
     * instance within the singleton class, or a newly instantiated object where
     * one was not already requested during the current app lifecycle.
     */
    synchronized public static Branch getAutoInstance(@NonNull Context context) {
        if (branchReferral_ == null) {
            if(BranchUtil.getEnableLoggingConfig(context)){
                enableLogging();
            }

            // Should only be set in json config
            deferInitForPluginRuntime(BranchUtil.getDeferInitForPluginRuntimeConfig(context));

            BranchUtil.setAPIBaseUrlFromConfig(context);

            BranchUtil.setFbAppIdFromConfig(context);

            BranchUtil.setCPPLevel(context);

            BranchUtil.setTestMode(BranchUtil.checkTestMode(context));
            branchReferral_ = initBranchSDK(context, BranchUtil.readBranchKey(context));
            getPreinstallSystemData(branchReferral_, context);
        }
        return branchReferral_;
    }

    /**
     * <p>Singleton method to return the pre-initialised, or newly initialise and return, a singleton
     * object of the type {@link Branch}.</p>
     * <p>Use this whenever you need to call a method directly on the {@link Branch} object.</p>
     *
     * @param context   A {@link Context} from which this call was made.
     * @param branchKey A {@link String} value used to initialize Branch.
     * @return An initialised {@link Branch} object, either fetched from a pre-initialised
     * instance within the singleton class, or a newly instantiated object where
     * one was not already requested during the current app lifecycle.
     */
    public static Branch getAutoInstance(@NonNull Context context, @NonNull String branchKey) {
        if (branchReferral_ == null) {
            if(BranchUtil.getEnableLoggingConfig(context)){
                enableLogging();
            }

            // Should only be set in json config
            deferInitForPluginRuntime(BranchUtil.getDeferInitForPluginRuntimeConfig(context));

            BranchUtil.setAPIBaseUrlFromConfig(context);
            BranchUtil.setFbAppIdFromConfig(context);
            BranchUtil.setCPPLevel(context);
            BranchUtil.setTestMode(BranchUtil.checkTestMode(context));
            // If a Branch key is passed already use it. Else read the key
            if (!isValidBranchKey(branchKey)) {
                BranchLogger.w("Warning, Invalid branch key passed! Branch key will be read from manifest instead!");
                branchKey = BranchUtil.readBranchKey(context);
            }
            branchReferral_ = initBranchSDK(context, branchKey);
            getPreinstallSystemData(branchReferral_, context);
        }
        return branchReferral_;
    }

    public Context getApplicationContext() {
        return context_;
    }

    /**
     * Sets a custom Branch Remote interface for handling RESTful requests. Call this for implementing a custom network layer for handling communication between
     * Branch SDK and remote Branch server
     *
     * @param remoteInterface A instance of class extending {@link BranchRemoteInterface} with
     *                        implementation for abstract RESTful GET or POST methods, if null
     *                        is passed, the SDK will use its default.
     */
    public void setBranchRemoteInterface(BranchRemoteInterface remoteInterface) {
        if (remoteInterface == null) {
            branchRemoteInterface_ = new BranchRemoteInterfaceUrlConnection(this);
        } else {
            branchRemoteInterface_ = remoteInterface;
        }
    }

    public BranchRemoteInterface getBranchRemoteInterface() {
        return branchRemoteInterface_;
    }
    
    /**
     * <p>
     * Enables the test mode for the SDK. This will use the Branch Test Keys. This is same as setting
     * "io.branch.sdk.TestMode" to "True" in Manifest file.
     *
     * Note: As of v5.0.1, enableTestMode has been changed. It now uses the test key but will not log or randomize
     * the device IDs. If you wish to enable logging, please invoke enableLogging. If you wish to simulate
     * installs, please see add a Test Device (https://help.branch.io/using-branch/docs/adding-test-devices)
     * then reset your test device's data (https://help.branch.io/using-branch/docs/adding-test-devices#section-resetting-your-test-device-data).
     * </p>
     */
    public static void enableTestMode() {
        BranchUtil.setTestMode(true);
        BranchLogger.logAlways("enableTestMode has been changed. It now uses the test key but will not" +
                " log or randomize the device IDs. If you wish to enable logging, please invoke enableLogging." +
                " If you wish to simulate installs, please see add a Test Device (https://help.branch.io/using-branch/docs/adding-test-devices)" +
                " then reset your test device's data (https://help.branch.io/using-branch/docs/adding-test-devices#section-resetting-your-test-device-data).");
    }

    /**
     * <p>
     * Disables the test mode for the SDK.
     * </p>
     */
    public static void disableTestMode() {
        BranchUtil.setTestMode(false);
    }

    /**
     * Disable (or re-enable) ad network callouts. This setting is persistent.
     *
     * @param disabled (@link Boolean) whether ad network callouts should be disabled.
     */
    public void disableAdNetworkCallouts(boolean disabled) {
        PrefHelper.getInstance(context_).setAdNetworkCalloutsDisabled(disabled);
    }

    /**
     * Temporarily disables auto session initialization until user initializes themselves.
     *
     * Context: Branch expects session initialization to be started in LauncherActivity.onStart(),
     * if session initialization has not been started/completed by the time ANY Activity resumes,
     * Branch will auto-initialize. This allows Branch to keep an accurate count of all app sessions,
     * including instances when app is launched from a recent apps list and the first visible Activity
     * is not LauncherActivity.
     *
     * However, in certain scenarios users may need to delay session initialization (e.g. to asynchronously
     * retrieve some data that needs to be passed to Branch prior to session initialization). In those
     * cases, use expectDelayedSessionInitialization() to temporarily disable auto self initialization.
     * Once the user initializes the session themselves, the flag will be reset and auto session initialization
     * will be re-enabled.
     *
     * @param expectDelayedInit A {@link Boolean} to set the expectation flag.
     */
    public static void expectDelayedSessionInitialization(boolean expectDelayedInit) {
        disableAutoSessionInitialization = expectDelayedInit;
    }

    /**
     * <p>Sets a custom base URL for all calls to the Branch API.  Requires https.</p>
     * @param url The {@link String} URL base URL that the Branch API uses.
     */
    public static void setAPIUrl(String url) {
        if (!TextUtils.isEmpty(url)) {
            if (!url.endsWith("/")) {
                url = url + "/";
            }

            PrefHelper.setAPIUrl(url);
            BranchLogger.v("setAPIUrl: Branch API URL was set to " + url);
        } else {
            BranchLogger.w("setAPIUrl: URL cannot be empty or null");
        }
    }
    /**
     * <p>Sets a custom CDN base URL.</p>
     * @param url The {@link String} base URL for CDN endpoints.
     */
    public static void setCDNBaseUrl(String url) {
        PrefHelper.setCDNBaseUrl(url);
    }

    /**
     * Toggles the tracking state of the SDK. When tracking is disabled, the SDK will not track any user data or state,
     * and it will not initiate any network calls except for deep linking operations.
     * Re-enabling tracking will reinitialize the Branch session and resume normal SDK operations.
     * This method allows for optional callback specification to handle post-operation actions or state notifications.
     *
     * @param disableTracking A boolean value indicating whether tracking should be disabled ({@code true}) or enabled
     *                        ({@code false}).
     * @param callback An optional {@link TrackingStateCallback} instance for receiving callback notifications about
     *                 the change in tracking state. This parameter can be {@code null} if no callback actions are needed.
     * @deprecated Use {@link #setConsumerProtectionAttributionLevel(Defines.BranchAttributionLevel)}
     * with {@link Defines.BranchAttributionLevel#NONE} instead to disable tracking.
     * */
    @Deprecated public void disableTracking(boolean disableTracking, @Nullable TrackingStateCallback callback) {
        trackingController.disableTracking(context_, disableTracking, callback);
    }

    /**
     * Toggles the tracking state of the SDK. When tracking is disabled, the SDK will not track any user data or state,
     * and it will not initiate any network calls except for deep linking operations.
     * Re-enabling tracking will reinitialize the Branch session and resume normal SDK operations.
     *
     * @param disableTracking A boolean value indicating whether tracking should be disabled ({@code true}) or enabled
     *                        ({@code false}).
     * @deprecated Use {@link #setConsumerProtectionAttributionLevel(Defines.BranchAttributionLevel)}
     * with {@link Defines.BranchAttributionLevel#NONE} instead to disable tracking.
     * */
    @Deprecated public void disableTracking(boolean disableTracking) {
        disableTracking(disableTracking, null);
    }

    public interface TrackingStateCallback {
        void onTrackingStateChanged(boolean trackingDisabled, @Nullable JSONObject referringParams, @Nullable BranchError error);
    }
    
    /**
     * Checks if tracking is disabled. See {@link #disableTracking(boolean)}
     *
     * @return {@code true} if tracking is disabled
     */
    public boolean isTrackingDisabled() {
        return trackingController.isTrackingDisabled();
    }

    /**
     * <p>
     * Disables or enables the instant deep link functionality.
     * </p>
     *
     * @param disableIDL Value {@code true} disables the  instant deep linking. Value {@code false} enables the  instant deep linking.
     */
    public static void disableInstantDeepLinking(boolean disableIDL) {
        enableInstantDeepLinking = !disableIDL;
    }

    // Package Private
    // For Unit Testing, we need to reset the Branch state
    static void shutDown() {
        ServerRequestQueue.shutDown();
        PrefHelper.shutDown();
        BranchUtil.shutDown();

        // DeepLinkRoutingValidator.shutDown();
        // GooglePlayStoreAttribution.shutDown();
        // InstantAppUtil.shutDown();
        // IntegrationValidator.shutDown();
        // ShareLinkManager.shutDown();
        // UniversalResourceAnalyser.shutDown();

        // Release these contexts immediately.

        // Reset all of the statics.
        branchReferral_ = null;
        bypassCurrentActivityIntentState_ = false;
        enableInstantDeepLinking = false;
        isActivityLifeCycleCallbackRegistered_ = false;

        bypassWaitingForIntent_ = false;
    }


    /**
     * <p>Manually sets the {@link Boolean} value, that indicates that the Branch API connection has
     * been initialised, to false - forcing re-initialisation.</p>
     */
    public void resetUserSession() {
        setInitState(SESSION_STATE.UNINITIALISED);
    }
    
    /**
     * Sets the max number of times to re-attempt a timed-out request to the Branch API, before
     * considering the request to have failed entirely. Default to 3. Note that the the network
     * timeout, as set in {@link #setNetworkTimeout(int)}, together with the retry interval value from
     * {@link #setRetryInterval(int)} will determine if the max retry count will be attempted.
     *
     * @param retryCount An {@link Integer} specifying the number of times to retry before giving
     *                   up and declaring defeat.
     */
    public void setRetryCount(int retryCount) {
        if (prefHelper_ != null && retryCount >= 0) {
            prefHelper_.setRetryCount(retryCount);
        }
    }
    
    /**
     * Sets the amount of time in milliseconds to wait before re-attempting a timed-out request
     * to the Branch API. Default 1000 ms.
     *
     * @param retryInterval An {@link Integer} value specifying the number of milliseconds to
     *                      wait before re-attempting a timed-out request.
     */
    public void setRetryInterval(int retryInterval) {
        if (prefHelper_ != null && retryInterval > 0) {
            prefHelper_.setRetryInterval(retryInterval);
        }
    }
    
    /**
     * <p>Sets the duration in milliseconds that the system should wait for a response before timing
     * out any Branch API. Default 5500 ms. Note that this is the total time allocated for all request
     * retries as set in {@link #setRetryCount(int)}.
     *
     * @param timeout An {@link Integer} value specifying the number of milliseconds to wait before
     *                considering the request to have timed out.
     */
    public void setNetworkTimeout(int timeout) {
        if (prefHelper_ != null && timeout > 0) {
            prefHelper_.setTimeout(timeout);
        }
    }

    /**
     * <p>Sets the duration in milliseconds that the system should wait for initializing a network
     * * request.</p>
     *
     * @param connectTimeout An {@link Integer} value specifying the number of milliseconds to wait before
     *                considering the initialization to have timed out.
     */
    public void setNetworkConnectTimeout(int connectTimeout) {
        if (prefHelper_ != null && connectTimeout > 0) {
            prefHelper_.setConnectTimeout(connectTimeout);
        }
    }

    /**
     * In cases of persistent no internet connection or offline modes,
     * set a maximum number of attempts for the Branch Request to be tried.
     *
     * Must be greater than 0
     * Defaults to 3
     * @param retryMax
     */
    public void setNoConnectionRetryMax(int retryMax){
        if(prefHelper_ != null && retryMax > 0){
            prefHelper_.setNoConnectionRetryMax(retryMax);
        }
    }

    /**
     * Sets the window for the referrer GCLID field. The GCLID will be persisted locally from the
     * time it is set + window in milliseconds. Thereafter, it will be deleted.
     *
     * By default, the window is set to 30 days, or 2592000000L in millseconds
     * Minimum of 0 milliseconds
     * Maximum of 3 years
     * @param window A {@link Long} value specifying the number of milliseconds to wait before
     *               deleting the locally persisted GCLID value.
     */
    public void setReferrerGclidValidForWindow(long window){
        if(prefHelper_ != null){
            prefHelper_.setReferrerGclidValidForWindow(window);
        }
    }
    
    /**
     * Method to control reading Android ID from device. Set this to true to disable reading the device id.
     * This method should be called from your {@link Application#onCreate()} method before creating Branch auto instance by calling {@link Branch#getAutoInstance(Context)}
     *
     * @param deviceIdFetch {@link Boolean with value true to disable reading the Android id from device}
     */
    public static void disableDeviceIDFetch(Boolean deviceIdFetch) {
        disableDeviceIDFetch_ = deviceIdFetch;
    }
    
    /**
     * Returns true if reading device id is disabled
     *
     * @return {@link Boolean} with value true to disable reading Android ID
     */
    public static boolean isDeviceIDFetchDisabled() {
        return disableDeviceIDFetch_;
    }
    
    /**
     * Sets the key-value pairs for debugging the deep link. The key-value set in debug mode is given back with other deep link data on branch init session.
     * This method should be called from onCreate() of activity which listens to Branch Init Session callbacks
     *
     * @param debugParams A {@link JSONObject} containing key-value pairs for debugging branch deep linking
     */
    public void setDeepLinkDebugMode(JSONObject debugParams) {
        deeplinkDebugParams_ = debugParams;
    }
    
    /**
     * @deprecated Branch is not listing external apps any more from v2.11.0
     */
    public void disableAppList() {
        // Do nothing
    }

    /**
     * Enables or disables app tracking with Branch or any other third parties that Branch use internally
     *
     * @param isLimitFacebookTracking {@code true} to limit app tracking
     */
    public void setLimitFacebookTracking(boolean isLimitFacebookTracking) {
        prefHelper_.setLimitFacebookTracking(isLimitFacebookTracking);
    }

    /**
     * Sets the value of parameters required by Google Conversion APIs for DMA Compliance in EEA region.
     *
     * @param eeaRegion {@code true} If European regulations, including the DMA, apply to this user and conversion.
     * @param adPersonalizationConsent {@code true} If End user has granted/denied ads personalization consent.
     * @param adUserDataUsageConsent {@code true} If User has granted/denied consent for 3P transmission of user level data for ads.
     */
    public void setDMAParamsForEEA(boolean eeaRegion, boolean adPersonalizationConsent, boolean adUserDataUsageConsent) {
        prefHelper_.setEEARegion(eeaRegion);
        prefHelper_.setAdPersonalizationConsent(adPersonalizationConsent);
        prefHelper_.setAdUserDataUsageConsent(adUserDataUsageConsent);
    }

    /**
     * <p>Add key value pairs to all requests</p>
     */
    public void setRequestMetadata(@NonNull String key, @NonNull String value) {
        prefHelper_.setRequestMetadata(key, value);
    }

    /**
     * <p>
     * This API allows to tag the install with custom attribute. Add any key-values that qualify or distinguish an install here.
     * Please make sure this method is called before the Branch init, which is on the onStartMethod of first activity.
     * A better place to call this  method is right after Branch#getAutoInstance()
     * </p>
     */
    public Branch addInstallMetadata(@NonNull String key, @NonNull String value) {
        prefHelper_.addInstallMetadata(key, value);
        return this;
    }

    /**
     * <p>
     *   wrapper method to add the pre-install campaign analytics
     * </p>
     */
    public Branch setPreinstallCampaign(@NonNull String preInstallCampaign) {
        addInstallMetadata(PreinstallKey.campaign.getKey(), preInstallCampaign);
        return this;
    }

    /**
     * <p>
     *   wrapper method to add the pre-install campaign analytics
     * </p>
     */
    public Branch setPreinstallPartner(@NonNull String preInstallPartner) {
        addInstallMetadata(PreinstallKey.partner.getKey(), preInstallPartner);
        return this;
    }

    /**
     * Enables referring url attribution for preinstalled apps.
     *
     * By default, Branch prioritizes preinstall attribution on preinstalled apps.
     * Some clients prefer the referring link, when present, to be prioritized over preinstall attribution.
     */
    public static void setReferringLinkAttributionForPreinstalledAppsEnabled() {
        referringLinkAttributionForPreinstalledAppsEnabled = true;
    }

    public static boolean isReferringLinkAttributionForPreinstalledAppsEnabled() {
        return referringLinkAttributionForPreinstalledAppsEnabled;
    }

    public static void setIsUserAgentSync(boolean sync){
        userAgentSync = sync;
    }

    public static boolean getIsUserAgentSync(){
        return userAgentSync;
    }

    /*
     * <p>Closes the current session. Should be called by on getting the last actvity onStop() event.
     * </p>
     */
    void closeSessionInternal() {
        BranchLogger.v("closeSessionInternal");
        clearPartnerParameters();
        executeClose();
        prefHelper_.setSessionParams(PrefHelper.NO_STRING_VALUE);
        prefHelper_.setExternalIntentUri(null);
        trackingController.updateTrackingState(context_); // Update the tracking state for next cold start
    }
    
    /**
     * Clears all pending requests in the queue
     */
    void clearPendingRequests() {
        requestQueue_.clear();
    }
    
    /**
     * <p>Perform the state-safe actions required to terminate any open session, and report the
     * closed application event to the Branch API.</p>
     */
    private void executeClose() {
        if (initState_ != SESSION_STATE.UNINITIALISED) {
            setInitState(SESSION_STATE.UNINITIALISED);
        }
    }

    public static void registerPlugin(String name, String version) {
        pluginName = name;
        pluginVersion = version;
    }

    public static String getPluginVersion() {
        return pluginVersion;
    }

    static String getPluginName() {
        return pluginName;
    }

    private void readAndStripParam(Uri data, Activity activity, ServerRequestInitSession initRequest) {
        BranchLogger.v("Read params uri: " + data + " bypassCurrentActivityIntentState: " + bypassCurrentActivityIntentState_ + " intent state: " + intentState_);
        if (enableInstantDeepLinking) {

            // If activity is launched anew (i.e. not from stack), then its intent can be readily consumed.
            // Otherwise, we have to wait for onResume, which ensures that we will have the latest intent.
            // In the latter case, IDL works only partially because the callback is delayed until onResume.
            boolean activityHasValidIntent = intentState_ == INTENT_STATE.READY ||
                    !activityLifeCycleObserver.isCurrentActivityLaunchedFromStack();

            // Skip IDL if intent contains an unused Branch link.
            boolean noUnusedBranchLinkInIntent = !isRestartSessionRequested(activity != null ? activity.getIntent() : null);

            if (activityHasValidIntent && noUnusedBranchLinkInIntent) {
                extractSessionParamsForIDL(data, activity);
            }
        }

        if (bypassCurrentActivityIntentState_) {
            intentState_ = INTENT_STATE.READY;
        }

        //TODO: Fix this area
        if (intentState_ == INTENT_STATE.READY) {

            // Capture the intent URI and extra for analytics in case started by external intents such as google app search
            LinkParsingKt.extractExternalUriAndIntentExtras(data, activity, prefHelper_, initRequest);

            // if branch link is detected we don't need to look for click ID or app link anymore and can terminate early
            if (LinkParsingKt.extractBranchLinkFromIntentExtra(activity, prefHelper_, initRequest)) {
                return;
            }

            // Check for link click id or app link
            if (!isActivityLaunchedFromHistory(activity)) {
                // if click ID is detected we don't need to look for app link anymore and can terminate early
                if (LinkParsingKt.extractClickID(data, activity, prefHelper_, initRequest)) {
                    return;
                }

                // Check if the clicked url is an app link pointing to this app
                LinkParsingKt.extractAppLink(data, activity, prefHelper_, initRequest);
            }
        }
    }

    void unlockSDKInitWaitLock() {
        if (requestQueue_ == null) return;
        requestQueue_.postInitClear();
        requestQueue_.unlockProcessWait(ServerRequest.PROCESS_WAIT_LOCK.SDK_INIT_WAIT_LOCK);
        requestQueue_.processNextQueueItem("unlockSDKInitWaitLock");
    }
    
    private boolean isActivityLaunchedFromHistory(Activity activity) {
        return activity != null && activity.getIntent() != null &&
                (activity.getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;
    }

    /**
     * Package Private.
     * @return the link which opened this application session if opened by a link click.
     */
    String getSessionReferredLink() {
        String link = prefHelper_.getExternalIntentUri();
        return (link.equals(PrefHelper.NO_STRING_VALUE) ? null : link);
    }

    /**
     * Branch collect the URLs in the incoming intent for better attribution. Branch SDK extensively check for any sensitive data in the URL and skip if exist.
     * However the following method provisions application to set SDK to collect only URLs in particular form. This method allow application to specify a set of regular expressions to white list the URL collection.
     * If whitelist is not empty SDK will collect only the URLs that matches the white list.
     * <p>
     * This method should be called immediately after calling {@link Branch#getAutoInstance(Context)}
     *
     * @param urlWhiteListPattern A regular expression with a URI white listing pattern
     * @return {@link Branch} instance for successive method calls
     */
    public Branch addWhiteListedScheme(String urlWhiteListPattern) {
        if (urlWhiteListPattern != null) {
            UniversalResourceAnalyser.getInstance(context_).addToAcceptURLFormats(urlWhiteListPattern);
        }
        return this;
    }
    
    /**
     * Branch collect the URLs in the incoming intent for better attribution. Branch SDK extensively check for any sensitive data in the URL and skip if exist.
     * However the following method provisions application to set SDK to collect only URLs in particular form. This method allow application to specify a set of regular expressions to white list the URL collection.
     * If whitelist is not empty SDK will collect only the URLs that matches the white list.
     * <p>
     * This method should be called immediately after calling {@link Branch#getAutoInstance(Context)}
     *
     * @param urlWhiteListPatternList {@link List} of regular expressions with URI white listing pattern
     * @return {@link Branch} instance for successive method calls
     */
    public Branch setWhiteListedSchemes(List<String> urlWhiteListPatternList) {
        if (urlWhiteListPatternList != null) {
            UniversalResourceAnalyser.getInstance(context_).addToAcceptURLFormats(urlWhiteListPatternList);
        }
        return this;
    }
    
    /**
     * Branch collect the URLs in the incoming intent for better attribution. Branch SDK extensively check for any sensitive data in the URL and skip if exist.
     * This method allows applications specify SDK to skip any additional URL patterns to be skipped
     * <p>
     * This method should be called immediately after calling {@link Branch#getAutoInstance(Context)}
     *
     * @param urlSkipPattern {@link String} A URL pattern that Branch SDK should skip from collecting data
     * @return {@link Branch} instance for successive method calls
     */
    public Branch addUriHostsToSkip(String urlSkipPattern) {
        if (!TextUtils.isEmpty(urlSkipPattern))
            UniversalResourceAnalyser.getInstance(context_).addToSkipURLFormats(urlSkipPattern);
        return this;
    }
    
    /**
     * Check and update the URL / URI Skip list in case an update is available.
     */
    void updateSkipURLFormats() {
        UniversalResourceAnalyser.getInstance(context_).checkAndUpdateSkipURLFormats(context_);
    }
    
    /**
     * <p>Identifies the current user to the Branch API by supplying a unique identifier as a
     * {@link String} value. No callback.</p>
     *
     * @param userId A {@link String} value containing the unique identifier of the user.
     */
    public void setIdentity(@NonNull String userId) {
        setIdentity(userId, null);
    }
    
    /**
     * <p>Identifies the current user to the Branch API by supplying a unique identifier as a
     * {@link String} value, with a callback specified to perform a defined action upon successful
     * response to request.</p>
     *
     * @param userId   A {@link String} value containing the unique identifier of the user.
     * @param callback A {@link BranchReferralInitListener} callback instance that will return
     *                 the data associated with the user id being assigned, if available.
     */
    public void setIdentity(@NonNull String userId, @Nullable BranchReferralInitListener
            callback) {
                if (userId != null && !userId.equals(prefHelper_.getIdentity())) {
                    installDeveloperId = userId;
                    prefHelper_.setIdentity(userId);
                }
                if (callback != null) {
                    callback.onInitFinished(getFirstReferringParams(), null);
                }
    }

    /**
     * Gets the available last attributed touch data. The attribution window is set to the value last
     * saved via PreferenceHelper.setLATDAttributionWindow(). If no value has been saved, Branch
     * defaults to a 30 day attribution window (SDK sends -1 to request the default from the server).
     *
     * @param callback An instance of {@link io.branch.referral.ServerRequestGetLATD.BranchLastAttributedTouchDataListener}
     *                 to callback with last attributed touch data
     *
     */
    public void getLastAttributedTouchData(@NonNull BranchLastAttributedTouchDataListener callback) {
        if (context_ != null) {
            requestQueue_.handleNewRequest(new ServerRequestGetLATD(context_, Defines.RequestPath.GetLATD, callback));
        }
    }

    /**
     * Gets the available last attributed touch data with a custom set attribution window.
     *
     * @param callback An instance of {@link io.branch.referral.ServerRequestGetLATD.BranchLastAttributedTouchDataListener}
     *                to callback with last attributed touch data
     * @param attributionWindow An {@link int} to bound the the window of time in days during which
     *                          the attribution data is considered valid. Note that, server side, the
     *                          maximum value is 90.
     *
     */
    public void getLastAttributedTouchData(BranchLastAttributedTouchDataListener callback, int attributionWindow) {
        if (context_ != null) {
            requestQueue_.handleNewRequest(new ServerRequestGetLATD(context_, Defines.RequestPath.GetLATD, callback, attributionWindow));
        }
    }

    /**
     * Indicates whether or not this user has a custom identity specified for them. Note that this is independent of installs.
     * If you call setIdentity, this device will have that identity associated with this user until logout is called.
     * This includes persisting through uninstalls, as we track device id.
     *
     * @return A {@link Boolean} value that will return <i>true</i> only if user already has an identity.
     */
    public boolean isUserIdentified() {
        return !prefHelper_.getIdentity().equals(PrefHelper.NO_STRING_VALUE);
    }

    /**
     * <p>This method should be called if you know that a different person is about to use the app. For example,
     * if you allow users to log out and let their friend use the app, you should call this to notify Branch
     * to create a new user for this device. This will clear the first and latest params, as a new session is created.</p>
     */
    public void logout() {
        logout(null);
    }

    /**
     * <p>This method should be called if you know that a different person is about to use the app. For example,
     * if you allow users to log out and let their friend use the app, you should call this to notify Branch
     * to create a new user for this device. This will clear the first and latest params, as a new session is created.</p>
     *
     * @param callback An instance of {@link io.branch.referral.Branch.LogoutStatusListener} to callback with the logout operation status.
     */
    public void logout(LogoutStatusListener callback) {
        prefHelper_.setIdentity(PrefHelper.NO_STRING_VALUE);
        prefHelper_.clearUserValues();
        //On Logout clear the link cache and all pending requests
        linkCache_.clear();
        requestQueue_.clear();
        if (callback != null) {
            callback.onLogoutFinished(true, null);
        }
    }

    /**
     * <p>Returns the parameters associated with the link that referred the user. This is only set once,
     * the first time the user is referred by a link. Think of this as the user referral parameters.
     * It is also only set if isReferrable is equal to true, which by default is only true
     * on a fresh install (not upgrade or reinstall). This will change on setIdentity (if the
     * user already exists from a previous device) and logout.</p>
     *
     * @return A {@link JSONObject} containing the install-time parameters as configured
     * locally.
     */
    public JSONObject getFirstReferringParams() {
        String storedParam = prefHelper_.getInstallParams();
        JSONObject firstReferringParams = convertParamsStringToDictionary(storedParam);
        firstReferringParams = appendDebugParams(firstReferringParams);
        return firstReferringParams;
    }

    @SuppressWarnings("WeakerAccess")
    public void removeSessionInitializationDelay() {
        requestQueue_.unlockProcessWait(ServerRequest.PROCESS_WAIT_LOCK.USER_SET_WAIT_LOCK);
        requestQueue_.processNextQueueItem("removeSessionInitializationDelay");
    }
    
    /**
     * <p>This function must be called from a non-UI thread! If Branch has no install link data,
     * and this func is called, it will return data upon initializing, or until LATCH_WAIT_UNTIL.
     * Returns the parameters associated with the link that referred the user. This is only set once,
     * the first time the user is referred by a link. Think of this as the user referral parameters.
     * It is also only set if isReferrable is equal to true, which by default is only true
     * on a fresh install (not upgrade or reinstall). This will change on setIdentity (if the
     * user already exists from a previous device) and logout.</p>
     *
     * @return A {@link JSONObject} containing the install-time parameters as configured
     * locally.
     */
    public JSONObject getFirstReferringParamsSync() {
        getFirstReferringParamsLatch = new CountDownLatch(1);
        if (prefHelper_.getInstallParams().equals(PrefHelper.NO_STRING_VALUE)) {
            try {
                getFirstReferringParamsLatch.await(LATCH_WAIT_UNTIL, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
        }
        String storedParam = prefHelper_.getInstallParams();
        JSONObject firstReferringParams = convertParamsStringToDictionary(storedParam);
        appendDebugParams(firstReferringParams);
        getFirstReferringParamsLatch = null;
        return firstReferringParams;
    }
    
    /**
     * <p>Returns the parameters associated with the link that referred the session. If a user
     * clicks a link, and then opens the app, initSession will return the parameters of the link
     * and then set them in as the latest parameters to be retrieved by this method. By default,
     * sessions persist for the duration of time that the app is in focus. For example, if you
     * minimize the app, these parameters will be cleared when closeSession is called.</p>
     *
     * @return A {@link JSONObject} containing the latest referring parameters as
     * configured locally.
     */
    public JSONObject getLatestReferringParams() {
        String storedParam = prefHelper_.getSessionParams();
        JSONObject latestParams = convertParamsStringToDictionary(storedParam);
        latestParams = appendDebugParams(latestParams);
        return latestParams;
    }
    
    /**
     * <p>This function must be called from a non-UI thread! If Branch has not been initialized
     * and this func is called, it will return data upon initialization, or until LATCH_WAIT_UNTIL.
     * Returns the parameters associated with the link that referred the session. If a user
     * clicks a link, and then opens the app, initSession will return the parameters of the link
     * and then set them in as the latest parameters to be retrieved by this method. By default,
     * sessions persist for the duration of time that the app is in focus. For example, if you
     * minimize the app, these parameters will be cleared when closeSession is called.</p>
     *
     * @return A {@link JSONObject} containing the latest referring parameters as
     * configured locally.
     */
    public JSONObject getLatestReferringParamsSync() {
        getLatestReferringParamsLatch = new CountDownLatch(1);
        try {
            if (initState_ != SESSION_STATE.INITIALISED) {
                getLatestReferringParamsLatch.await(LATCH_WAIT_UNTIL, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
        }
        String storedParam = prefHelper_.getSessionParams();
        JSONObject latestParams = convertParamsStringToDictionary(storedParam);
        latestParams = appendDebugParams(latestParams);
        getLatestReferringParamsLatch = null;
        return latestParams;
    }

    /**
     * Add a Partner Parameter for Facebook.
     * Once set, this parameter is attached to installs, opens and events until cleared or the app restarts.
     *
     * See Facebook's documentation for details on valid parameters
     */
    public void addFacebookPartnerParameterWithName(@NonNull String key, @NonNull String value) {
        if (!trackingController.isTrackingDisabled()) {
            prefHelper_.partnerParams_.addFacebookParameter(key, value);
        }
    }

    /**
     * Add a Partner Parameter for Snap.
     * Once set, this parameter is attached to installs, opens and events until cleared or the app restarts.
     *
     * See Snap's documentation for details on valid parameters
     */
    public void addSnapPartnerParameterWithName(@NonNull String key, @NonNull String value) {
        if (!trackingController.isTrackingDisabled()) {
            prefHelper_.partnerParams_.addSnapParameter(key, value);
        }
    }

    /**
     * Clears all Partner Parameters
     */
    public void clearPartnerParameters() {
        prefHelper_.partnerParams_.clearAllParameters();
    }
    
    /**
     * Append the deep link debug params to the original params
     *
     * @param originalParams A {@link JSONObject} original referrer parameters
     * @return A new {@link JSONObject} with debug params appended.
     */
    private JSONObject appendDebugParams(JSONObject originalParams) {
        try {
            if (originalParams != null && deeplinkDebugParams_ != null) {
                if (deeplinkDebugParams_.length() > 0) {
                    BranchLogger.v("You're currently in deep link debug mode. Please comment out 'setDeepLinkDebugMode' to receive the deep link parameters from a real Branch link");
                }
                Iterator<String> keys = deeplinkDebugParams_.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    originalParams.put(key, deeplinkDebugParams_.get(key));
                }
            }
        } catch (Exception e) {
            BranchLogger.d(e.getMessage());
        }
        return originalParams;
    }
    
    public JSONObject getDeeplinkDebugParams() {
        if (deeplinkDebugParams_ != null && deeplinkDebugParams_.length() > 0) {
            BranchLogger.v("You're currently in deep link debug mode. Please comment out 'setDeepLinkDebugMode' to receive the deep link parameters from a real Branch link");
        }
        return deeplinkDebugParams_;
    }
    
    
    //-----------------Generate Short URL      -------------------------------------------//
    
    /**
     * <p> Generates a shorl url for the given {@link ServerRequestCreateUrl} object </p>
     *
     * @param req An instance  of {@link ServerRequestCreateUrl} with parameters create the short link.
     * @return A url created with the given request if the request is synchronous else null.
     * Note : This method can be used only internally. Use {@link BranchUrlBuilder} for creating short urls.
     */
    String generateShortLinkInternal(ServerRequestCreateUrl req) {
        if (!req.constructError_ && !req.handleErrors(context_)) {
            if (linkCache_.containsKey(req.getLinkPost())) {
                String url = linkCache_.get(req.getLinkPost());
                req.onUrlAvailable(url);
                return url;
            }
            if (req.isAsync()) {
                requestQueue_.handleNewRequest(req);
            } else {
                return generateShortLinkSync(req);
            }
        }
        return null;
    }


    /**
     * <p>Creates a link with given attributes and shares with the
     * user selected clients using native android share sheet</p>
     *
     * @param activity          The {@link Activity} to show native share sheet chooser dialog.
     * @param buo               A {@link BranchUniversalObject} value containing the deep link params.
     * @param linkProperties    An object of {@link LinkProperties} specifying the properties of this link
     * @param callback          A {@link Branch.BranchNativeLinkShareListener } instance for getting sharing status.
     * @param title             A {@link String } for setting title in native chooser dialog.
     * @param subject           A {@link String } for setting subject in native chooser dialog.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public void share(@NonNull Activity activity, @NonNull BranchUniversalObject buo, @NonNull LinkProperties linkProperties, @Nullable BranchNativeLinkShareListener callback, String title, String subject){
            NativeShareLinkManager.getInstance().shareLink(activity, buo, linkProperties, callback, title, subject);
    }

    /**
     * <p>Creates options for sharing a link with other Applications. Creates a link with given attributes and shares with the
     * user selected clients.</p>
     *
     * @param builder A {@link BranchShareSheetBuilder} instance to build share link.
     */
    void shareLink(BranchShareSheetBuilder builder) {
        //Cancel any existing sharing in progress.
        if (shareLinkManager_ != null) {
            shareLinkManager_.cancelShareLinkDialog(true);
        }
        shareLinkManager_ = new ShareLinkManager();
        shareLinkManager_.shareLink(builder);
    }
    
    /**
     * <p>Cancel current share link operation and Application selector dialog. If your app is not using auto session management, make sure you are
     * calling this method before your activity finishes inorder to prevent any window leak. </p>
     *
     * @param animateClose A {@link Boolean} to specify whether to close the dialog with an animation.
     *                     A value of true will close the dialog with an animation. Setting this value
     *                     to false will close the Dialog immediately.
     */
    public void cancelShareLinkDialog(boolean animateClose) {
        if (shareLinkManager_ != null) {
            shareLinkManager_.cancelShareLinkDialog(animateClose);
        }
    }
    
    // PRIVATE FUNCTIONS
    
    private String generateShortLinkSync(ServerRequestCreateUrl req) {
        ServerResponse response = null;
        try {
            int timeOut = prefHelper_.getTimeout() + 2000; // Time out is set to slightly more than link creation time to prevent any edge case
            response = new GetShortLinkTask().execute(req).get(timeOut, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            BranchLogger.d(e.getMessage());
        }
        String url = null;
        if (req.isDefaultToLongUrl()) {
            url = req.getLongUrl();
        }
        if (response != null && response.getStatusCode() == HttpURLConnection.HTTP_OK) {
            try {
                url = response.getObject().getString("url");
                if (req.getLinkPost() != null) {
                    linkCache_.put(req.getLinkPost(), url);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return url;
    }
    
    private JSONObject convertParamsStringToDictionary(String paramString) {
        if (paramString.equals(PrefHelper.NO_STRING_VALUE)) {
            return new JSONObject();
        } else {
            try {
                return new JSONObject(paramString);
            } catch (JSONException e) {
                byte[] encodedArray = Base64.decode(paramString.getBytes(), Base64.NO_WRAP);
                try {
                    return new JSONObject(new String(encodedArray));
                } catch (JSONException ex) {
                    ex.printStackTrace();
                    return new JSONObject();
                }
            }
        }
    }

    public TrackingController getTrackingController() {
        return trackingController;
    }

    public DeviceInfo getDeviceInfo() {
        return deviceInfo_;
    }

    public BranchPluginSupport getBranchPluginSupport() {
        return branchPluginSupport_;
    }

    public BranchQRCodeCache getBranchQRCodeCache() {
        return branchQRCodeCache_;
    }

    PrefHelper getPrefHelper() {
        return prefHelper_;
    }

    ShareLinkManager getShareLinkManager() {
        return shareLinkManager_;
    }

    void setIntentState(INTENT_STATE intentState) {
        this.intentState_ = intentState;
    }

    void setInitState(SESSION_STATE initState) {
        this.initState_ = initState;
    }

    SESSION_STATE getInitState() {
        return initState_;
    }

    public void setInstantDeepLinkPossible(boolean instantDeepLinkPossible) {
        isInstantDeepLinkPossible = instantDeepLinkPossible;
    }

    public boolean isInstantDeepLinkPossible() {
        return isInstantDeepLinkPossible;
    }

    private void initializeSession(ServerRequestInitSession initRequest, int delay) {
        BranchLogger.v("initializeSession " + initRequest + " delay " + delay);
        if ((prefHelper_.getBranchKey() == null || prefHelper_.getBranchKey().equalsIgnoreCase(PrefHelper.NO_STRING_VALUE))) {
            setInitState(SESSION_STATE.UNINITIALISED);
            //Report Key error on callback
            if (initRequest.callback_ != null) {
                initRequest.callback_.onInitFinished(null, new BranchError("Trouble initializing Branch.", BranchError.ERR_BRANCH_KEY_INVALID));
            }
            BranchLogger.w("Warning: Please enter your branch_key in your project's manifest");
            return;
        } else if (isTestModeEnabled()) {
            BranchLogger.w("Warning: You are using your test app's Branch Key. Remember to change it to live Branch Key during deployment.");
        }

        if (delay > 0) {
            initRequest.addProcessWaitLock(ServerRequest.PROCESS_WAIT_LOCK.USER_SET_WAIT_LOCK);
            new Handler().postDelayed(new Runnable() {
                @Override public void run() {
                    removeSessionInitializationDelay();
                }
            }, delay);
        }

        // Re 'forceBranchSession':
        // Check if new session is being forced. There are two use cases for setting the ForceNewBranchSession to true:
        // 1. Launch an activity via a push notification while app is in foreground but does not have
        // the particular activity in the backstack, in such cases, users can't utilize reInitSession() because
        // it's called from onNewIntent() which is never invoked
        // todo: this is tricky for users, get rid of ForceNewBranchSession if possible. (if flag is not set, the content from Branch link is lost)
        // 2. Some users navigate their apps via Branch links so they would have to set ForceNewBranchSession to true
        // which will blow up the session count in analytics but does the job.
        Intent intent = getCurrentActivity() != null ? getCurrentActivity().getIntent() : null;
        boolean forceBranchSession = isRestartSessionRequested(intent);

        SESSION_STATE sessionState = getInitState();
        BranchLogger.v("Intent: " + intent + " forceBranchSession: " + forceBranchSession + " initState: " + sessionState);
        if (sessionState == SESSION_STATE.UNINITIALISED || forceBranchSession) {
            if (forceBranchSession && intent != null) {
                intent.removeExtra(Defines.IntentKeys.ForceNewBranchSession.getKey()); // SDK-881, avoid double initialization
            }
            registerAppInit(initRequest, forceBranchSession);
        } else if (initRequest.callback_ != null) {
            // Else, let the user know session initialization failed because it's already initialized.
            initRequest.callback_.onInitFinished(null, new BranchError("Warning.", BranchError.ERR_BRANCH_ALREADY_INITIALIZED));
        }
    }
    
    /**
     * Registers app init with params filtered from the intent. Unless ignoreIntent = true, this
     * will wait on the wait locks to complete any pending operations
     */
     void registerAppInit(@NonNull ServerRequestInitSession request, boolean forceBranchSession) {
         BranchLogger.v("registerAppInit " + request);
         setInitState(SESSION_STATE.INITIALISING);

         ServerRequestInitSession r = requestQueue_.getSelfInitRequest();
         BranchLogger.v("Ordering init calls");
         requestQueue_.printQueue();

         // if forceBranchSession aka reInit is true, we want to preserve the callback order in case
         // there is one still in flight
         if (r == null || forceBranchSession) {
             BranchLogger.v("Moving " + request + " " + " to front of the queue or behind network-in-progress request");
             requestQueue_.insertRequestAtFront(request);
         }
         else {
             // if false, maintain previous behavior
             BranchLogger.v("Retrieved " + r + " with callback " + r.callback_ + " in queue currently");
             r.callback_ = request.callback_;
             BranchLogger.v(r + " now has callback " + request.callback_);
         }
         BranchLogger.v("Finished ordering init calls");
         requestQueue_.printQueue();
         initTasks(request);

         requestQueue_.processNextQueueItem("registerAppInit");
     }

    private void initTasks(ServerRequest request) {
        BranchLogger.v("initTasks " + request);
        // Single top activities can be launched from stack and there may be a new intent provided with onNewIntent() call.
        // In this case need to wait till onResume to get the latest intent. Bypass this if bypassWaitingForIntent_ is true.
        if (intentState_ != INTENT_STATE.READY && isWaitingForIntent()) {
            request.addProcessWaitLock(ServerRequest.PROCESS_WAIT_LOCK.INTENT_PENDING_WAIT_LOCK);
            BranchLogger.v("Added INTENT_PENDING_WAIT_LOCK");
        }

        if (request instanceof ServerRequestRegisterInstall) {
            request.addProcessWaitLock(ServerRequest.PROCESS_WAIT_LOCK.INSTALL_REFERRER_FETCH_WAIT_LOCK);
            BranchLogger.v("Added INSTALL_REFERRER_FETCH_WAIT_LOCK");

            deviceInfo_.getSystemObserver().fetchInstallReferrer(context_, new SystemObserver.InstallReferrerFetchEvents() {
                @Override
                public void onInstallReferrersFinished() {
                    request.removeProcessWaitLock(ServerRequest.PROCESS_WAIT_LOCK.INSTALL_REFERRER_FETCH_WAIT_LOCK);
                    BranchLogger.v("INSTALL_REFERRER_FETCH_WAIT_LOCK removed");
                    requestQueue_.processNextQueueItem("onInstallReferrersFinished");
                }
            });
        }

        request.addProcessWaitLock(ServerRequest.PROCESS_WAIT_LOCK.GAID_FETCH_WAIT_LOCK);
        BranchLogger.v("Added GAID_FETCH_WAIT_LOCK");

        deviceInfo_.getSystemObserver().fetchAdId(context_, new SystemObserver.AdsParamsFetchEvents() {
            @Override
            public void onAdsParamsFetchFinished() {
                requestQueue_.unlockProcessWait(ServerRequest.PROCESS_WAIT_LOCK.GAID_FETCH_WAIT_LOCK);
                requestQueue_.processNextQueueItem("onAdsParamsFetchFinished");
            }
        });
    }

    ServerRequestInitSession getInstallOrOpenRequest(BranchReferralInitListener callback, boolean isAutoInitialization) {
        ServerRequestInitSession request;
        if (requestQueue_.hasUser()) {
            // If there is user this is open
            request = new ServerRequestRegisterOpen(context_, callback, isAutoInitialization);
        } else {
            // If no user this is an Install
            request = new ServerRequestRegisterInstall(context_, callback, isAutoInitialization);
        }
        return request;
    }
    
    void onIntentReady(@NonNull Activity activity) {
        setIntentState(Branch.INTENT_STATE.READY);
        requestQueue_.unlockProcessWait(ServerRequest.PROCESS_WAIT_LOCK.INTENT_PENDING_WAIT_LOCK);
        BranchLogger.v("onIntentReady " + activity + " removed INTENT_PENDING_WAIT_LOCK");

        Intent currentIntent = activity.getIntent();
        Branch.SESSION_STATE sessionState = getInitState();
        boolean grabIntentParams = currentIntent != null &&  getInitState() != Branch.SESSION_STATE.INITIALISED;

        BranchLogger.v("onIntentReady currentIntent " + currentIntent);
        BranchLogger.v("onIntentReady sessionState " + sessionState);
        BranchLogger.v("onIntentReady grabIntentParams " + grabIntentParams);
        if (grabIntentParams) {
            Uri intentData = activity.getIntent().getData();
            // Test with multiple opens queued up
            readAndStripParam(intentData, activity, latestInitRequest);
        }
        requestQueue_.processNextQueueItem("onIntentReady");
    }

    /**
     * Notify Branch when network is available in order to process the next request in the queue.
     */
    public void notifyNetworkAvailable() {
        requestQueue_.processNextQueueItem("notifyNetworkAvailable");
    }

    private void setActivityLifeCycleObserver(Application application) {
        try {
            activityLifeCycleObserver = new BranchActivityLifecycleObserver();
            /* Set an observer for activity life cycle events. */
            application.unregisterActivityLifecycleCallbacks(activityLifeCycleObserver);
            application.registerActivityLifecycleCallbacks(activityLifeCycleObserver);
            isActivityLifeCycleCallbackRegistered_ = true;
            
        } catch (NoSuchMethodError | NoClassDefFoundError Ex) {
            isActivityLifeCycleCallbackRegistered_ = false;
            /* LifeCycleEvents are  available only from API level 14. */
            BranchLogger.v(new BranchError("", BranchError.ERR_API_LVL_14_NEEDED).getMessage());
        }
    }

    /*
     * Check for forced session restart. The Branch session is restarted if the incoming intent has branch_force_new_session set to true.
     * This is for supporting opening a deep link path while app is already running in the foreground. Such as clicking push notification while app (namely, LauncherActivity) is in foreground.
     */
    boolean isRestartSessionRequested(Intent intent) {
        return checkIntentForSessionRestart(intent) || checkIntentForUnusedBranchLink(intent);
    }

    private boolean checkIntentForSessionRestart(Intent intent) {
        boolean forceSessionIntentKeyPresent = false;
        if (intent != null) {
            forceSessionIntentKeyPresent = intent.getBooleanExtra(Defines.IntentKeys.ForceNewBranchSession.getKey(), false);
        }
        return forceSessionIntentKeyPresent;
    }

    private boolean checkIntentForUnusedBranchLink(Intent intent) {
        boolean hasUnusedBranchLink = false;
        if (intent != null) {
            boolean hasBranchLink = intent.getStringExtra(Defines.IntentKeys.BranchURI.getKey()) != null;
            boolean branchLinkNotConsumedYet = !intent.getBooleanExtra(Defines.IntentKeys.BranchLinkUsed.getKey(), false);
            hasUnusedBranchLink = hasBranchLink && branchLinkNotConsumedYet;
        }
        return hasUnusedBranchLink;
    }
    
    /**
     * <p>An Interface class that is implemented by all classes that make use of
     * {@link BranchReferralInitListener}, defining a single method that takes a list of params in
     * {@link JSONObject} format, and an error message of {@link BranchError} format that will be
     * returned on failure of the request response.</p>
     *
     * @see JSONObject
     * @see BranchError
     */
    public interface BranchReferralInitListener {
        void onInitFinished(@Nullable JSONObject referringParams, @Nullable BranchError error);
    }
    
    /**
     * <p>An Interface class that is implemented by all classes that make use of
     * {@link BranchUniversalReferralInitListener}, defining a single method that provides
     * {@link BranchUniversalObject}, {@link LinkProperties} and an error message of {@link BranchError} format that will be
     * returned on failure of the request response.
     * In case of an error the value for {@link BranchUniversalObject} and {@link LinkProperties} are set to null.</p>
     *
     * @see BranchUniversalObject
     * @see LinkProperties
     * @see BranchError
     */
    public interface BranchUniversalReferralInitListener {
        void onInitFinished(@Nullable BranchUniversalObject branchUniversalObject, @Nullable LinkProperties linkProperties, @Nullable BranchError error);
    }
    
    
    /**
     * <p>An Interface class that is implemented by all classes that make use of
     * {@link BranchReferralStateChangedListener}, defining a single method that takes a value of
     * {@link Boolean} format, and an error message of {@link BranchError} format that will be
     * returned on failure of the request response.</p>
     *
     * @see Boolean
     * @see BranchError
     */
    public interface BranchReferralStateChangedListener {
        void onStateChanged(boolean changed, @Nullable BranchError error);
    }
    
    /**
     * <p>An Interface class that is implemented by all classes that make use of
     * {@link BranchLinkCreateListener}, defining a single method that takes a URL
     * {@link String} format, and an error message of {@link BranchError} format that will be
     * returned on failure of the request response.</p>
     *
     * @see String
     * @see BranchError
     */
    public interface BranchLinkCreateListener {
        void onLinkCreate(String url, BranchError error);
    }
    
    /**
     * <p>An Interface class that is implemented by all classes that make use of
     * {@link BranchLinkShareListener}, defining methods to listen for link sharing status.</p>
     */
    public interface BranchLinkShareListener {
        /**
         * <p> Callback method to update when share link dialog is launched.</p>
         */
        void onShareLinkDialogLaunched();
        
        /**
         * <p> Callback method to update when sharing dialog is dismissed.</p>
         */
        void onShareLinkDialogDismissed();
        
        /**
         * <p> Callback method to update the sharing status. Called on sharing completed or on error.</p>
         *
         * @param sharedLink    The link shared to the channel.
         * @param sharedChannel Channel selected for sharing.
         * @param error         A {@link BranchError} to update errors, if there is any.
         */
        void onLinkShareResponse(String sharedLink, String sharedChannel, BranchError error);
        
        /**
         * <p>Called when user select a channel for sharing a deep link.
         * Branch will create a deep link for the selected channel and share with it after calling this
         * method. On sharing complete, status is updated by onLinkShareResponse() callback. Consider
         * having a sharing in progress UI if you wish to prevent user activity in the window between selecting a channel
         * and sharing complete.</p>
         *
         * @param channelName Name of the selected application to share the link. An empty string is returned if unable to resolve selected client name.
         */
        void onChannelSelected(String channelName);
    }
    
    /**
     * <p>An extended version of {@link BranchLinkShareListener} with callback that supports updating link data or properties after user select a channel to share
     * This will provide the extended callback {@link #onChannelSelected(String, BranchUniversalObject, LinkProperties)} only when sharing a link using Branch Universal Object.</p>
     */
    public interface ExtendedBranchLinkShareListener extends BranchLinkShareListener {
        /**
         * <p>
         * Called when user select a channel for sharing a deep link.
         * This method allows modifying the link data and properties by providing the params  {@link BranchUniversalObject} and {@link LinkProperties}
         * </p>
         *
         * @param channelName    The name of the channel user selected for sharing a link
         * @param buo            {@link BranchUniversalObject} BUO used for sharing link for updating any params
         * @param linkProperties {@link LinkProperties} associated with the sharing link for updating the properties
         * @return Return {@code true} to create link with any updates added to the data ({@link BranchUniversalObject}) or to the properties ({@link LinkProperties}).
         * Return {@code false} otherwise.
         */
        boolean onChannelSelected(String channelName, BranchUniversalObject buo, LinkProperties linkProperties);
    }

    /**
     * <p>An Interface class that is implemented by all classes that make use of
     * {@link BranchNativeLinkShareListener}, defining methods to listen for link sharing status.</p>
     */
    public interface BranchNativeLinkShareListener {

        /**
         * <p> Callback method to report error/response.</p>
         *
         * @param sharedLink    The link shared to the channel.
         * @param error         A {@link BranchError} to update errors, if there is any.
         */
        void onLinkShareResponse(String sharedLink, BranchError error);

        /**
         * <p>Called when user select a channel for sharing a deep link.
         *
         * @param channelName Name of the selected application to share the link. An empty string is returned if unable to resolve selected client name.
         */
        void onChannelSelected(String channelName);
    }

    /**
     * <p>An interface class for customizing sharing properties with selected channel.</p>
     */
    public interface IChannelProperties {
        /**
         * @param channel The name of the channel selected for sharing.
         * @return {@link String} with value for the message title for sharing the link with the selected channel
         */
        String getSharingTitleForChannel(String channel);
        
        /**
         * @param channel The name of the channel selected for sharing.
         * @return {@link String} with value for the message body for sharing the link with the selected channel
         */
        String getSharingMessageForChannel(String channel);
    }
    
    /**
     * <p>An Interface class that is implemented by all classes that make use of
     * {@link BranchListResponseListener}, defining a single method that takes a list of
     * {@link JSONArray} format, and an error message of {@link BranchError} format that will be
     * returned on failure of the request response.</p>
     *
     * @see JSONArray
     * @see BranchError
     */
    public interface BranchListResponseListener {
        void onReceivingResponse(JSONArray list, BranchError error);
    }
    
    /**
     * <p>
     * Callback interface for listening logout status
     * </p>
     */
    public interface LogoutStatusListener {
        /**
         * Called on finishing the the logout process
         *
         * @param loggedOut A {@link Boolean} which is set to true if logout succeeded
         * @param error     An instance of {@link BranchError} to notify any error occurred during logout.
         *                  A null value is set if logout succeeded.
         */
        void onLogoutFinished(boolean loggedOut, BranchError error);
    }

    /**
     * Async Task to create  a short link for synchronous methods
     */
    private class GetShortLinkTask extends AsyncTask<ServerRequest, Void, ServerResponse> {
        @Override protected ServerResponse doInBackground(ServerRequest... serverRequests) {
            return branchRemoteInterface_.make_restful_post(serverRequests[0].getPost(),
                    prefHelper_.getAPIBaseUrl() + Defines.RequestPath.GetURL.getPath(),
                    Defines.RequestPath.GetURL.getPath(), prefHelper_.getBranchKey());
        }
    }

    //-------------------Auto deep link feature-------------------------------------------//
    
    /**
     * <p>Checks if an activity is launched by Branch auto deep link feature. Branch launches activity configured for auto deep link on seeing matching keys.
     * Keys for auto deep linking should be specified to each activity as a meta data in manifest.</p>
     * Configure your activity in your manifest to enable auto deep linking as follows
     * <!--
     * <activity android:name=".YourActivity">
     * <meta-data android:name="io.branch.sdk.auto_link" android:value="DeepLinkKey1","DeepLinkKey2" />
     * </activity>
     * -->
     *
     * @param activity Instance of activity to check if launched on auto deep link.
     * @return A {Boolean} value whose value is true if this activity is launched by Branch auto deeplink feature.
     */
    public static boolean isAutoDeepLinkLaunch(Activity activity) {
        return (activity.getIntent().getStringExtra(Defines.IntentKeys.AutoDeepLinked.getKey()) != null);
    }
    
    void checkForAutoDeepLinkConfiguration() {
        JSONObject latestParams = getLatestReferringParams();
        String deepLinkActivity = null;
        
        try {
            //Check if the application is launched by clicking a Branch link.
            if (!latestParams.has(Defines.Jsonkey.Clicked_Branch_Link.getKey())
                    || !latestParams.getBoolean(Defines.Jsonkey.Clicked_Branch_Link.getKey())) {
                BranchLogger.v("Does not have Clicked_Branch_Link or Clicked_Branch_Link is false, returning");
                return;
            }
            if (latestParams.length() > 0) {
                // Check if auto deep link is disabled.
                ApplicationInfo appInfo = context_.getPackageManager().getApplicationInfo(context_.getPackageName(), PackageManager.GET_META_DATA);
                if (appInfo.metaData != null && appInfo.metaData.getBoolean(AUTO_DEEP_LINK_DISABLE, false)) {
                    return;
                }
                PackageInfo info = context_.getPackageManager().getPackageInfo(context_.getPackageName(), PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);
                ActivityInfo[] activityInfos = info.activities;
                int deepLinkActivityReqCode = DEF_AUTO_DEEP_LINK_REQ_CODE;
                
                if (activityInfos != null) {
                    for (ActivityInfo activityInfo : activityInfos) {
                        if (activityInfo != null && activityInfo.metaData != null && (activityInfo.metaData.getString(AUTO_DEEP_LINK_KEY) != null || activityInfo.metaData.getString(AUTO_DEEP_LINK_PATH) != null)) {
                            if (checkForAutoDeepLinkKeys(latestParams, activityInfo) || checkForAutoDeepLinkPath(latestParams, activityInfo)) {
                                deepLinkActivity = activityInfo.name;
                                deepLinkActivityReqCode = activityInfo.metaData.getInt(AUTO_DEEP_LINK_REQ_CODE, DEF_AUTO_DEEP_LINK_REQ_CODE);
                                break;
                            }
                        }
                    }
                }
                if (deepLinkActivity != null && getCurrentActivity() != null) {
                    BranchLogger.v("deepLinkActivity " + deepLinkActivity + " getCurrentActivity " + getCurrentActivity());
                    Activity currentActivity = getCurrentActivity();

                    Intent intent = new Intent(currentActivity, Class.forName(deepLinkActivity));
                    intent.putExtra(Defines.IntentKeys.AutoDeepLinked.getKey(), "true");

                    // Put the raw JSON params as extra in case need to get the deep link params as JSON String
                    intent.putExtra(Defines.Jsonkey.ReferringData.getKey(), latestParams.toString());

                    // Add individual parameters in the data
                    Iterator<?> keys = latestParams.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        intent.putExtra(key, latestParams.getString(key));
                    }
                    currentActivity.startActivityForResult(intent, deepLinkActivityReqCode);
                } else {
                    // This case should not happen. Adding a safe handling for any corner case
                    BranchLogger.v("No activity reference to launch deep linked activity");
                }
            }
        } catch (final PackageManager.NameNotFoundException e) {
            BranchLogger.w("Warning: Please make sure Activity names set for auto deep link are correct!");
        } catch (ClassNotFoundException e) {
            BranchLogger.w("Warning: Please make sure Activity names set for auto deep link are correct! Error while looking for activity " + deepLinkActivity);
        } catch (Exception ignore) {
            // Can get TransactionTooLarge Exception here if the Application info exceeds 1mb binder data limit. Usually results with manifest merge from SDKs
        }
    }
    
    private boolean checkForAutoDeepLinkKeys(JSONObject params, ActivityInfo activityInfo) {
        if (activityInfo.metaData.getString(AUTO_DEEP_LINK_KEY) != null) {
            String[] activityLinkKeys = activityInfo.metaData.getString(AUTO_DEEP_LINK_KEY).split(",");
            for (String activityLinkKey : activityLinkKeys) {
                if (params.has(activityLinkKey)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean checkForAutoDeepLinkPath(JSONObject params, ActivityInfo activityInfo) {
        String deepLinkPath = null;
        try {
            if (params.has(Defines.Jsonkey.AndroidDeepLinkPath.getKey())) {
                deepLinkPath = params.getString(Defines.Jsonkey.AndroidDeepLinkPath.getKey());
            } else if (params.has(Defines.Jsonkey.DeepLinkPath.getKey())) {
                deepLinkPath = params.getString(Defines.Jsonkey.DeepLinkPath.getKey());
            }
        } catch (JSONException e) {
            BranchLogger.d(e.getMessage());
        }
        if (activityInfo.metaData.getString(AUTO_DEEP_LINK_PATH) != null && deepLinkPath != null) {
            String[] activityLinkPaths = activityInfo.metaData.getString(AUTO_DEEP_LINK_PATH).split(",");
            for (String activityLinkPath : activityLinkPaths) {
                if (pathMatch(activityLinkPath.trim(), deepLinkPath)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean pathMatch(String templatePath, String path) {
        boolean matched = true;
        String[] pathSegmentsTemplate = templatePath.split("\\?")[0].split("/");
        String[] pathSegmentsTarget = path.split("\\?")[0].split("/");
        if (pathSegmentsTemplate.length != pathSegmentsTarget.length) {
            return false;
        }
        for (int i = 0; i < pathSegmentsTemplate.length && i < pathSegmentsTarget.length; i++) {
            String pathSegmentTemplate = pathSegmentsTemplate[i];
            String pathSegmentTarget = pathSegmentsTarget[i];
            if (!pathSegmentTemplate.equals(pathSegmentTarget) && !pathSegmentTemplate.contains("*")) {
                matched = false;
                break;
            }
        }
        return matched;
    }

    /**
     * Enable logging with a specific log level, independent of Debug Mode.
     *
     * @param iBranchLogging Optional interface to receive logging from the SDK.
     * @param level The minimum log level for logging output.
     */
    private static void enableLogging(IBranchLoggingCallbacks iBranchLogging, BranchLogger.BranchLogLevel level) {
        BranchLogger.setLoggerCallback(iBranchLogging);
        BranchLogger.setLoggingLevel(level);
        BranchLogger.setLoggingEnabled(true);
        BranchLogger.logAlways(GOOGLE_VERSION_TAG);
    }

    /**
     * Enable Logging, independent of Debug Mode. Defaults to DEBUG level.
     */
    public static void enableLogging() {
        enableLogging(null, BranchLogger.BranchLogLevel.DEBUG);
    }

    /**
     * Enable Logging, independent of Debug Mode. Set to VERBOSE level.
     * Implement a callback to receive logging from the SDK directly to your
     * own logging solution. If null, and enabled, the default android.util.Log is used.
     *
     * @param iBranchLogging Optional interface to receive logging from the SDK.
     */
    public static void enableLogging(IBranchLoggingCallbacks iBranchLogging) {
        enableLogging(iBranchLogging, BranchLogger.BranchLogLevel.VERBOSE);
    }

    /**
     * Enable logging with a specific log level.
     *
     * @param level The minimum log level for logging output.
     */
    public static void enableLogging(BranchLogger.BranchLogLevel level) {
        enableLogging(null, level);

    }

    /**
     * Disable Logging, independent of Debug Mode.
     */
    public static void disableLogging() {
        BranchLogger.setLoggingEnabled(false);
        BranchLogger.setLoggerCallback(null);
    }

    /**
     * @deprecated use Branch.bypassWaitingForIntent(true)
     */
    @Deprecated
    public static void enableForcedSession() { bypassWaitingForIntent(true); }


    /**
     * <p> Use this method cautiously, it is meant to enable the ability to start a session before
     * the user opens the app.
     *
     * The use case explained:
     * Users are expected to initialize session from Activity.onStart. However, by default, Branch actually
     * waits until Activity.onResume to start session initialization, so as to ensure that the latest intent
     * data is available (e.g. when activity is launched from stack via onNewIntent). Setting this flag to true
     * will bypass waiting for intent, so session could technically be initialized from a background service
     * or otherwise before the application is even opened.
     *
     * Note however that if the flag is not reset during normal app boot up, the SDK behavior is undefined
     * in certain cases.</p>
     *
     * @param bypassIntent a {@link Boolean} indicating if SDK should wait for onResume in order to fire the
     *                     session initialization request.
     */
    @SuppressWarnings("WeakerAccess")
    public static void bypassWaitingForIntent(boolean bypassIntent) { bypassWaitingForIntent_ = bypassIntent; }

    /**
     * @deprecated use Branch.bypassWaitingForIntent(false)
     */
    @Deprecated
    public static void disableForcedSession() { bypassWaitingForIntent(false); }

    /**
     * Returns true if session initialization should bypass waiting for intent (retrieved after onResume).
     *
     * @return {@link Boolean} with value true to enable forced session
     *
     * @deprecated use Branch.isWaitingForIntent()
     */
    @Deprecated
    public static boolean isForceSessionEnabled() { return isWaitingForIntent(); }
    @SuppressWarnings("WeakerAccess")
    public static boolean isWaitingForIntent() { return !bypassWaitingForIntent_; }
    
    public static void enableBypassCurrentActivityIntentState() {
        bypassCurrentActivityIntentState_ = true;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean bypassCurrentActivityIntentState() {
        return bypassCurrentActivityIntentState_;
    }
    
    //------------------------ Content Indexing methods----------------------//
    
    public void registerView(BranchUniversalObject branchUniversalObject,
                             BranchUniversalObject.RegisterViewStatusListener callback) {
        if (context_ != null) {
            new BranchEvent(BRANCH_STANDARD_EVENT.VIEW_ITEM)
                    .addContentItems(branchUniversalObject)
                    .logEvent(context_);
        }
    }

    ///----------------- Instant App  support--------------------------//
    
    /**
     * Checks if this is an Instant app instance
     *
     * @param context Current {@link Context}
     * @return {@code true}  if current application is an instance of instant app
     */
    public static boolean isInstantApp(@NonNull Context context) {
        return InstantAppUtil.isInstantApp(context);
    }
    
    /**
     * Method shows play store install prompt for the full app. Thi passes the referrer to the installed application. The same deep link params as the instant app are provided to the
     * full app up on Branch#initSession()
     *
     * @param activity    Current activity
     * @param requestCode Request code for the activity to receive the result
     * @return {@code true} if install prompt is shown to user
     */
    public static boolean showInstallPrompt(@NonNull Activity activity, int requestCode) {
        String installReferrerString = "";
        if (Branch.getInstance() != null) {
            JSONObject latestReferringParams = Branch.getInstance().getLatestReferringParams();
            String referringLinkKey = "~" + Defines.Jsonkey.ReferringLink.getKey();
            if (latestReferringParams != null && latestReferringParams.has(referringLinkKey)) {
                String referringLink = "";
                try {
                    referringLink = latestReferringParams.getString(referringLinkKey);
                    // Considering the case that url may contain query params with `=` and `&` with it and may cause issue when parsing play store referrer
                    referringLink = URLEncoder.encode(referringLink, "UTF-8");
                } catch (JSONException | UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                if (!TextUtils.isEmpty(referringLink)) {
                    installReferrerString = Defines.Jsonkey.IsFullAppConv.getKey() + "=true&" + Defines.Jsonkey.ReferringLink.getKey() + "=" + referringLink;
                }
            }
        }
        return InstantAppUtil.doShowInstallPrompt(activity, requestCode, installReferrerString);
    }
    
    /**
     * Method shows play store install prompt for the full app. Use this method only if you have custom parameters to pass to the full app using referrer else use
     * {@link #showInstallPrompt(Activity, int)}
     *
     * @param activity    Current activity
     * @param requestCode Request code for the activity to receive the result
     * @param referrer    Any custom referrer string to pass to full app (must be of format "referrer_key1=referrer_value1%26referrer_key2=referrer_value2")
     * @return {@code true} if install prompt is shown to user
     */
    public static boolean showInstallPrompt(@NonNull Activity activity, int requestCode, @Nullable String referrer) {
        String installReferrerString = Defines.Jsonkey.IsFullAppConv.getKey() + "=true&" + referrer;
        return InstantAppUtil.doShowInstallPrompt(activity, requestCode, installReferrerString);
    }
    
    /**
     * Method shows play store install prompt for the full app. Use this method only if you want the full app to receive a custom {@link BranchUniversalObject} to do deferred deep link.
     * Please see {@link #showInstallPrompt(Activity, int)}
     * NOTE :
     * This method will do a synchronous generation of Branch short link for the BUO. So please consider calling this method on non UI thread
     * Please make sure your instant app and full ap are using same Branch key in order for the deferred deep link working
     *
     * @param activity    Current activity
     * @param requestCode Request code for the activity to receive the result
     * @param buo         {@link BranchUniversalObject} to pass to the full app up on install
     * @return {@code true} if install prompt is shown to user
     */
    public static boolean showInstallPrompt(@NonNull Activity activity, int requestCode, @NonNull BranchUniversalObject buo) {
        String shortUrl = buo.getShortUrl(activity, new LinkProperties());
        String installReferrerString = Defines.Jsonkey.ReferringLink.getKey() + "=" + shortUrl;
        if (!TextUtils.isEmpty(installReferrerString)) {
            return showInstallPrompt(activity, requestCode, installReferrerString);
        } else {
            return showInstallPrompt(activity, requestCode, "");
        }
    }

    //TODO: Mark as deprecated
    private void extractSessionParamsForIDL(Uri data, Activity activity) {
        if (activity == null || activity.getIntent() == null) return;

        Intent intent = activity.getIntent();
        try {
            if (data == null || LinkParsingKt.isIntentParamsAlreadyConsumed(activity)) {
                // Considering the case of a deferred install. In this case the app behaves like a cold
                // start but still Branch can do probabilistic match. So skipping instant deep link feature
                // until first Branch open happens.
                if (!prefHelper_.getInstallParams().equals(PrefHelper.NO_STRING_VALUE)) {
                    JSONObject nonLinkClickJson = new JSONObject();
                    nonLinkClickJson.put(Defines.Jsonkey.IsFirstSession.getKey(), false);
                    prefHelper_.setSessionParams(nonLinkClickJson.toString());
                    isInstantDeepLinkPossible = true;
                }
            } else if (!TextUtils.isEmpty(intent.getStringExtra(Defines.IntentKeys.BranchData.getKey()))) {
                // If not cold start, check the intent data to see if there are deep link params
                String rawBranchData = intent.getStringExtra(Defines.IntentKeys.BranchData.getKey());
                if (rawBranchData != null) {
                    // Make sure the data received is complete and in correct format
                    JSONObject branchDataJson = new JSONObject(rawBranchData);
                    branchDataJson.put(Defines.Jsonkey.Clicked_Branch_Link.getKey(), true);
                    prefHelper_.setSessionParams(branchDataJson.toString());
                    isInstantDeepLinkPossible = true;
                }

                // Remove Branch data from the intent once used
                intent.removeExtra(Defines.IntentKeys.BranchData.getKey());
                activity.setIntent(intent);
            } else if (data.isHierarchical() && Boolean.valueOf(data.getQueryParameter(Defines.Jsonkey.Instant.getKey()))) {
                // If instant key is true in query params, use them for instant deep linking
                JSONObject branchDataJson = new JSONObject();
                for (String key : data.getQueryParameterNames()) {
                    branchDataJson.put(key, data.getQueryParameter(key));
                }
                branchDataJson.put(Defines.Jsonkey.Clicked_Branch_Link.getKey(), true);
                prefHelper_.setSessionParams(branchDataJson.toString());
                isInstantDeepLinkPossible = true;
            }
        } catch (JSONException e) {
            BranchLogger.d(e.getMessage());
        }
    }

    @Nullable Activity getCurrentActivity() {
        if (currentActivityReference_ == null) {
            return null;
        }

        return currentActivityReference_.get();
    }

    public static class InitSessionBuilder {
        private BranchReferralInitListener callback;
        private boolean isAutoInitialization;
        private int delay;
        private Uri uri;
        private Boolean ignoreIntent;
        private boolean isReInitializing;
        private ServerRequestInitSession initSessionRequest;

        private InitSessionBuilder(Activity activity) {
            Branch branch = Branch.getInstance();
            if (activity != null && (branch.getCurrentActivity() == null ||
                    !branch.getCurrentActivity().getLocalClassName().equals(activity.getLocalClassName()))) {
                // currentActivityReference_ is set in onActivityCreated (before initSession), which should happen if
                // users follow Android guidelines and call super.onStart as the first thing in Activity.onStart,
                // however, if they don't, we try to set currentActivityReference_ here too.
                branch.currentActivityReference_ = new WeakReference<>(activity);
            }
        }

        /**
         * Helps differentiating between sdk session auto-initialization and client driven session
         * initialization. For internal SDK use only.
         */
        InitSessionBuilder isAutoInitialization(boolean isAuto) {
            this.isAutoInitialization = isAuto;
            return this;
        }

        /**
         * <p> Add callback to Branch initialization to retrieve referring params attached to the
         * Branch link via the dashboard. User eventually decides how to use the referring params but
         * they are primarily meant to be used for navigating to specific content within the app.
         * Use only one withCallback() method.</p>
         *
         * @param callback     A {@link BranchUniversalReferralInitListener} instance that will be called
         *                     following successful (or unsuccessful) initialisation of the session
         *                     with the Branch API.
         */
        @SuppressWarnings("WeakerAccess")
        public InitSessionBuilder withCallback(BranchUniversalReferralInitListener callback) {
            BranchLogger.v("InitSessionBuilder setting BranchUniversalReferralInitListener withCallback with " + callback);
            this.callback = new BranchUniversalReferralInitWrapper(callback);
            return this;
        }

        /**
         * <p> Delay session initialization by certain time (used when other async or otherwise time
         * consuming ops need to be completed prior to session initialization).</p>
         *
         * @param delayMillis  An {@link Integer} indicating the length of the delay in milliseconds.
         */
        @SuppressWarnings("WeakerAccess")
        public InitSessionBuilder withDelay(int delayMillis) {
            this.delay = delayMillis;
            return this;
        }

        /**
         * <p> Add callback to Branch initialization to retrieve referring params attached to the
         * Branch link via the dashboard. User eventually decides how to use the referring params but
         * they are primarily meant to be used for navigating to specific content within the app.
         * Use only one withCallback() method.</p>
         *
         * @param callback     A {@link BranchReferralInitListener} instance that will be called
         *                     following successful (or unsuccessful) initialisation of the session
         *                     with the Branch API.
         */
        @SuppressWarnings("WeakerAccess")
        public InitSessionBuilder withCallback(BranchReferralInitListener callback) {
            BranchLogger.v("InitSessionBuilder setting BranchReferralInitListener withCallback with " + callback);
            this.callback = callback;
            return this;
        }

        /**
         * <p> Specify a {@link Uri} variable containing the details of the source link that led to
         * this initialisation action.</p>
         *
         * @param uri A {@link  Uri} variable from the intent.
         */
        @SuppressWarnings("WeakerAccess")
        public InitSessionBuilder withData(Uri uri) {
            BranchLogger.v("InitSessionBuilder setting withData with " + uri);
            this.uri = uri;
            return this;
        }

        /** @deprecated */
        @SuppressWarnings("WeakerAccess")
        public InitSessionBuilder isReferrable(boolean isReferrable) {
            return this;
        }

        /**
         * <p> Use this method cautiously, it is meant to enable the ability to start a session before
         * the user even opens the app.
         *
         * The use case explained:
         * Users are expected to initialize session from Activity.onStart. However, by default, Branch actually
         * waits until Activity.onResume to start session initialization, so as to ensure that the latest intent
         * data is available (e.g. when activity is launched from stack via onNewIntent). Setting this flag to true
         * will bypass waiting for intent, so session could technically be initialized from a background service
         * or otherwise before the application is even opened.
         *
         * Note however that if the flag is not reset during normal app boot up, the SDK behavior is undefined
         * in certain cases. See also Branch.bypassWaitingForIntent(boolean). </p>
         *
         * @param ignore       a {@link Boolean} indicating if SDK should wait for onResume to retrieve
         *                     the most up recent intent data before firing the session initialization request.
         */
        @SuppressWarnings("WeakerAccess")
        public InitSessionBuilder ignoreIntent(boolean ignore) {
            ignoreIntent = ignore;
            return this;
        }

        /**
         * <p>Initialises a session with the Branch API, registers the passed in Activity, callback
         * and configuration variables, then initializes session.</p>
         */
        public void init() {
            BranchLogger.v("Beginning session initialization");
            BranchLogger.v("Session uri is " + uri);
            BranchLogger.v("Callback is " + callback);
            BranchLogger.v("Is auto init " + isAutoInitialization);
            BranchLogger.v("Will ignore intent " + ignoreIntent);
            BranchLogger.v("Is reinitializing " + isReInitializing);

            if(deferInitForPluginRuntime){
                BranchLogger.v("Session init is deferred until signaled by plugin.");
                cacheSessionBuilder(this);
                return;
            }

            final Branch branch = Branch.getInstance();
            if (branch == null) {
                BranchLogger.logAlways("Branch is not setup properly, make sure to call getAutoInstance" +
                        " in your application class or declare BranchApp in your manifest.");
                return;
            }
            if (ignoreIntent != null) {
                Branch.bypassWaitingForIntent(ignoreIntent);
            }

            Activity activity = branch.getCurrentActivity();
            Intent intent = activity != null ? activity.getIntent() : null;

            if (activity != null && intent != null && ActivityCompat.getReferrer(activity) != null) {
                PrefHelper.getInstance(activity).setInitialReferrer(ActivityCompat.getReferrer(activity).toString());
            }

            ServerRequestInitSession initRequest = branch.getInstallOrOpenRequest(callback, isAutoInitialization);
            branch.latestInitRequest = initRequest;
            BranchLogger.d("Creating " + initRequest + " from init on thread " + Thread.currentThread().getName());

            if (uri != null) {
                branch.readAndStripParam(uri, activity, initRequest);
            }
            else if (isReInitializing && branch.isRestartSessionRequested(intent)) {
                branch.readAndStripParam(intent != null ? intent.getData() : null, activity, initRequest);
            }
            else if (isReInitializing) {
                // User called reInit but isRestartSessionRequested = false, meaning the new intent was
                // not initiated by Branch and should not be considered a "new session", return early
                if (callback != null) {
                    callback.onInitFinished(null, new BranchError("", ERR_IMPROPER_REINITIALIZATION));
                }
                return;
            }

            BranchLogger.v("isInstantDeepLinkPossible " + branch.isInstantDeepLinkPossible);
            // readAndStripParams (above) may set isInstantDeepLinkPossible to true
            if (branch.isInstantDeepLinkPossible) {
                // reset state
                branch.isInstantDeepLinkPossible = false;
                // invoke callback returning LatestReferringParams, which were parsed out inside readAndStripParam
                // from either intent extra "branch_data", or as parameters attached to the referring app link
                if (callback != null) callback.onInitFinished(branch.getLatestReferringParams(), null);
                // mark this session as IDL session
                Branch.getInstance().requestQueue_.addExtraInstrumentationData(Defines.Jsonkey.InstantDeepLinkSession.getKey(), "true");
                // potentially routes the user to the Activity configured to consume this particular link
                branch.checkForAutoDeepLinkConfiguration();
                // we already invoked the callback for let's set it to null, we will still make the
                // init session request but for analytics purposes only
                callback = null;
            }

            if (delay > 0) {
                expectDelayedSessionInitialization(true);
            }

            branch.initializeSession(initRequest, delay);
        }

        private void cacheSessionBuilder(InitSessionBuilder initSessionBuilder) {
            Branch.getInstance().deferredSessionBuilder = this;
            BranchLogger.v("Session initialization deferred until plugin invokes notifyNativeToInit()" +
                    "\nCaching Session Builder " + Branch.getInstance().deferredSessionBuilder +
                    "\nuri: " + Branch.getInstance().deferredSessionBuilder.uri +
                    "\ncallback: " + Branch.getInstance().deferredSessionBuilder.callback +
                    "\nisReInitializing: " + Branch.getInstance().deferredSessionBuilder.isReInitializing +
                    "\ndelay: " + Branch.getInstance().deferredSessionBuilder.delay +
                    "\nisAutoInitialization: " + Branch.getInstance().deferredSessionBuilder.isAutoInitialization +
                    "\nignoreIntent: " + Branch.getInstance().deferredSessionBuilder.ignoreIntent
            );
        }

        /**
         * <p> Re-Initialize a session. Call from Activity.onNewIntent().
         * This solves a very specific use case, whereas the app is already in the foreground and a new
         * intent with a Uri is delivered to the foregrounded activity.
         *
         * Note that the Uri can also be stored as an extra in the field under the key `IntentKeys.BranchURI.getKey()` (i.e. "branch").
         *
         * Note also, that the since the method is expected to be called from Activity.onNewIntent(),
         * the implementation assumes the intent will be non-null and will contain a Branch link in
         * either the URI or in the the extra.</p>
         *
         */
        @SuppressWarnings("WeakerAccess")
        public void reInit() {
            isReInitializing = true;
            init();
        }
    }

    boolean isIDLSession() {
        return Boolean.parseBoolean(Branch.getInstance().requestQueue_.instrumentationExtraData_.get(Defines.Jsonkey.InstantDeepLinkSession.getKey()));
    }
    /**
     * <p> Create Branch session builder. Add configuration variables with the available methods
     * in the returned {@link InitSessionBuilder} class. Must be finished with init() or reInit(),
     * otherwise takes no effect.</p>
     *
     * @param activity     The calling {@link Activity} for context.
     */
    @SuppressWarnings("WeakerAccess")
    public static InitSessionBuilder sessionBuilder(Activity activity) {
        return new InitSessionBuilder(activity);
    }
    
    /**
     * Method will return the current Branch SDK version number
     * @return String value representing the current SDK version number (e.g. 4.3.2)
     */
    public static String getSdkVersionNumber() {
        return io.branch.referral.BuildConfig.VERSION_NAME;
    }


    /**
     * Scenario: Integrations using our plugin SDKs (React-Native, Capacitor, Unity, etc),
     * it is possible to have a race condition wherein the native layers finish their initialization
     * before the JS/C# layers have finished loaded and registering their receivers- dropping the
     * Branch parameters.
     *
     * Because these plugin delays are not deterministic, or consistent, a constant
     * offset to delay is not guaranteed to work in all cases, and possibly penalizes performant
     * devices.
     *
     * To solve, we wait for the plugin to signal when it is ready, and then begin native init
     *
     * Reusing disable autoinitialization to prevent uninitialization errors
     * @param isDeferred
     */
    static void deferInitForPluginRuntime(boolean isDeferred){
        BranchLogger.v("deferInitForPluginRuntime " + isDeferred);

        deferInitForPluginRuntime = isDeferred;
        if(isDeferred){
            expectDelayedSessionInitialization(isDeferred);
        }
    }

    /**
     * Method to be invoked from plugin to initialize the session originally built by the user
     * Only invokes the last session built
     */
    public static void notifyNativeToInit(){
        BranchLogger.v("notifyNativeToInit deferredSessionBuilder " + Branch.getInstance().deferredSessionBuilder);

        SESSION_STATE sessionState = Branch.getInstance().getInitState();
        if(sessionState == SESSION_STATE.UNINITIALISED) {
            deferInitForPluginRuntime = false;
            if (Branch.getInstance().deferredSessionBuilder != null) {
                Branch.getInstance().deferredSessionBuilder.init();
            }
        }
        else {
            BranchLogger.v("notifyNativeToInit session is not uninitialized. Session state is " + sessionState);
        }
    }

    public void logEventWithPurchase(@NonNull Context context, @NonNull Purchase purchase) {
        if (classExists(billingGooglePlayClass)) {
            BillingGooglePlay.Companion.getInstance().startBillingClient(succeeded -> {
                if (succeeded) {
                    BillingGooglePlay.Companion.getInstance().logEventWithPurchase(context, purchase);
                } else {
                    BranchLogger.e("Cannot log IAP event. Billing client setup failed");                }
                return null;
            });
        }
    }

    /**
     * Send requests to EU endpoints.
     * This feature must also be enabled on the server side, otherwise the server will drop requests. Contact your account manager for details.
     */
    public static void useEUEndpoint() {
        PrefHelper.useEUEndpoint(true);
    }

    /**
     * Sets the Facebook App ID for the Branch instance.
     *
     * @param fbAppID The Facebook App ID as a {@link String}.
     */
    public static void setFBAppID(String fbAppID) {
        if (!TextUtils.isEmpty(fbAppID)) {
            PrefHelper.fbAppId_ = fbAppID;
            BranchLogger.v("setFBAppID to " + fbAppID);
        } else {
            BranchLogger.w("setFBAppID: fbAppID cannot be empty or null");
        }
    }

    /**
     * Sets the consumer protection attribution level.
     *
     * @param level The consumer protection attribution level {@link Defines.BranchAttributionLevel}.
     */
    public void setConsumerProtectionAttributionLevel(Defines.BranchAttributionLevel level) {
        setConsumerProtectionAttributionLevel(level, null);
    }

    /**
     * Sets the consumer protection attribution level with an optional callback.
     *
     * @param level    The consumer protection attribution level {@link Defines.BranchAttributionLevel}.
     * @param callback An optional {@link TrackingStateCallback} for receiving notifications about
     *                 the change in tracking state. This parameter can be {@code null} if no callback actions are needed.
     */
    public void setConsumerProtectionAttributionLevel(Defines.BranchAttributionLevel level, @Nullable TrackingStateCallback callback) {
        prefHelper_.setConsumerProtectionAttributionLevel(level);
        BranchLogger.v("Set Consumer Protection Preference to " + level);

        if (level == Defines.BranchAttributionLevel.NONE) {
            trackingController.disableTracking(context_, true, callback);
        } else {
            if (trackingController.isTrackingDisabled()) {
                trackingController.disableTracking(context_, false, callback);
            }
        }
    }

}
