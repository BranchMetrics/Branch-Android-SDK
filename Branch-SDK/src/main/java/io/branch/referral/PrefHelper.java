package io.branch.referral;

import static io.branch.referral.BranchUtil.isTestModeEnabled;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.text.TextUtils;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>A class that uses the helper pattern to provide regularly referenced static values and
 * logging capabilities used in various other parts of the SDK, and that are related to globally set
 * preference values.</p>
 */
public class PrefHelper {
    private static final String TAG = "BranchSDK";

    /**
     * The base URL to use for all calls to the Branch API.
     */
    static final String BRANCH_BASE_URL_V2 = "https://api2.branch.io/";
    static final String BRANCH_BASE_URL_V1 = "https://api.branch.io/";
    static final String BRANCH_EU_BASE_URL_V3 = "https://api3-eu.branch.io/";

    /**
     * The base URL to use for all CDN calls.
     */
    static final String BRANCH_CDN_BASE_URL = "https://cdn.branch.io/";

    /**
     * A {@link String} value used where no string value is available.
     */
    public static final String NO_STRING_VALUE = "bnc_no_value";
    
    // We should keep this non-zero to give the connection time to recover after a failure
    private static final int INTERVAL_RETRY = 1000;
    
    /**
     * Number of times to reattempt connection to the Branch server before giving up and throwing an
     * exception.
     */
    private static final int MAX_RETRIES = 3; // Default retry count is 3

    static final int TIMEOUT = 5500; // Default timeout is 5.5 sec
    static final int CONNECT_TIMEOUT = 10000; // Default timeout is 10 seconds
    static final int TASK_TIMEOUT = TIMEOUT+CONNECT_TIMEOUT; // Default timeout is 15.5 seconds
    static final long DEFAULT_VALID_WINDOW_FOR_REFERRER_GCLID = 2592000000L; // Default expiration is 30 days, in milliseconds
    static final long MAX_VALID_WINDOW_FOR_REFERRER_GCLID = 100000000000L; // Arbitrary maximum window to prevent overflow, 3 years, in milliseconds
    static final long MIN_VALID_WINDOW_FOR_REFERRER_GCLID = 0L; // Don't allow time set in the past , in milliseconds
    static final int DEFAULT_NO_CONNECTION_RETRY_MAX = 3;

    private static final String SHARED_PREF_FILE = "branch_referral_shared_pref";
    
    private static final String KEY_BRANCH_KEY = "bnc_branch_key";
    private static final String KEY_APP_VERSION = "bnc_app_version";
    private static final String KEY_DEVICE_FINGERPRINT_ID = "bnc_device_fingerprint_id";
    private static final String KEY_RANDOMIZED_DEVICE_TOKEN = "bnc_randomized_device_token";
    private static final String KEY_SESSION_ID = "bnc_session_id";
    private static final String KEY_IDENTITY_ID = "bnc_identity_id";
    private static final String KEY_RANDOMIZED_BUNDLE_TOKEN = "bnc_randomized_bundle_token";
    private static final String KEY_IDENTITY = "bnc_identity";
    private static final String KEY_LINK_CLICK_ID = "bnc_link_click_id";
    private static final String KEY_LINK_CLICK_IDENTIFIER = "bnc_link_click_identifier";
    private static final String KEY_GOOGLE_SEARCH_INSTALL_IDENTIFIER = "bnc_google_search_install_identifier";
    private static final String KEY_GOOGLE_PLAY_INSTALL_REFERRER_EXTRA = "bnc_google_play_install_referrer_extras";
    private static final String KEY_APP_STORE_SOURCE = "bnc_app_store_source";
    private static final String KEY_GCLID_JSON_OBJECT = "bnc_gclid_json_object";
    private static final String KEY_GCLID_VALUE = "bnc_gclid_value";
    private static final String KEY_GCLID_EXPIRATION_DATE = "bnc_gclid_expiration_date";
    private static final String KEY_GCLID_VALID_FOR_WINDOW = "bnc_gclid_expiration_window";
    private static final String KEY_APP_LINK = "bnc_app_link";
    private static final String KEY_PUSH_IDENTIFIER = "bnc_push_identifier";
    private static final String KEY_SESSION_PARAMS = "bnc_session_params";
    private static final String KEY_INSTALL_PARAMS = "bnc_install_params";
    private static final String KEY_USER_URL = "bnc_user_url";
    private static final String KEY_LATD_ATTRIBUTION_WINDOW = "bnc_latd_attributon_window";
    private static final String KEY_INITIAL_REFERRER = "bnc_initial_referrer";
    private static final String KEY_ACTIONS = "bnc_actions";
    private static final String KEY_TOTAL_BASE = "bnc_total_base_";
    private static final String KEY_UNIQUE_BASE = "bnc_balance_base_";
    
    private static final String KEY_RETRY_COUNT = "bnc_retry_count";
    private static final String KEY_RETRY_INTERVAL = "bnc_retry_interval";
    private static final String KEY_TIMEOUT = "bnc_timeout";
    private static final String KEY_TASK_TIMEOUT = "bnc_task_timeout";
    private static final String KEY_CONNECT_TIMEOUT = "bnc_connect_timeout";
    private static final String KEY_NO_CONNECTION_RETRY_MAX = "bnc_no_connection_retry_max";

    private static final String KEY_LAST_READ_SYSTEM = "bnc_system_read_date";
    
    private static final String KEY_EXTERNAL_INTENT_URI = "bnc_external_intent_uri";
    private static final String KEY_EXTERNAL_INTENT_EXTRA = "bnc_external_intent_extra";

    private static final String KEY_BRANCH_VIEW_NUM_OF_USE = "bnc_branch_view_use";
    private static final String KEY_INSTALL_REFERRER = "bnc_install_referrer";
    private static final String KEY_IS_FULL_APP_CONVERSION = "bnc_is_full_app_conversion";
    private static final String KEY_LIMIT_FACEBOOK_TRACKING = "bnc_limit_facebook_tracking";
    private static final String KEY_DMA_EEA = "bnc_dma_eea";
    private static final String KEY_DMA_AD_PERSONALIZATION = "bnc_dma_ad_personalization";
    private static final String KEY_DMA_AD_USER_DATA = "bnc_dma_ad_user_data";
    private static final String KEY_LOG_IAP_AS_EVENTS = "bnc_log_iap_as_events";
    private static final String KEY_CONSUMER_PROTECTION_ATTRIBUTION_LEVEL = "bnc_consumer_protection_attribution_level";
    static final String KEY_ORIGINAL_INSTALL_TIME = "bnc_original_install_time";
    static final String KEY_LAST_KNOWN_UPDATE_TIME = "bnc_last_known_update_time";
    static final String KEY_PREVIOUS_UPDATE_TIME = "bnc_previous_update_time";
    static final String KEY_REFERRER_CLICK_TS = "bnc_referrer_click_ts";
    static final String KEY_INSTALL_BEGIN_TS = "bnc_install_begin_ts";
    static final String KEY_TRACKING_STATE = "bnc_tracking_state";
    static final String KEY_AD_NETWORK_CALLOUTS_DISABLED = "bnc_ad_network_callouts_disabled";

    static final String KEY_RANDOMLY_GENERATED_UUID = "bnc_randomly_generated_uuid";

    static final String KEY_REFERRING_URL_QUERY_PARAMETERS = "bnc_referringUrlQueryParameters";
    static final String KEY_ANON_ID = "bnc_anon_id";

    static final String KEY_IS_META_CLICKTHROUGH = "bnc_is_meta_clickthrough";

    /**
     * Internal static variable of own type {@link PrefHelper}. This variable holds the single
     * instance used when the class is instantiated via the Singleton pattern.
     */
    private static PrefHelper prefHelper_;
    
    /**
     * A single variable that holds a reference to the application's {@link SharedPreferences}
     * object for use whenever {@link SharedPreferences} values are read or written via this helper
     * class.
     */
    private final SharedPreferences appSharedPrefs_;
    
    /**
     * A single variable that holds a reference to an {@link Editor} object that is used by the
     * helper class whenever the preferences for the application are changed.
     */
    private Editor prefsEditor_;
    
    /**
     * Arbitrary key values added to all requests.
     */
    private final JSONObject requestMetadata = new JSONObject();

    /**
     * Arbitrary key values added to Install requests.
     */
    private final JSONObject installMetadata = new JSONObject();

    /**
     * Module injected key values added to all requests.
     */
    private final JSONObject secondaryRequestMetadata = new JSONObject();

    /**
     * Branch Custom server url.  Used by clients that want to proxy all requests.
     */
    private static String customServerURL_ = null;

    /**
     * Branch Custom server url.  Used by clients that want to proxy all CDN requests.
     */
    private static String customCDNBaseURL_ = null;

    /**
     * Branch partner parameters.
     */
    final BranchPartnerParameters partnerParams_ = new BranchPartnerParameters();

    /**
     * <p>Constructor with context passed from calling {@link Activity}.</p>
     *
     * @param context A reference to the {@link Context} that the application is operating
     *                within. This is normally the base context of the application.
     */
    private PrefHelper(Context context) {
        this.appSharedPrefs_ = context.getSharedPreferences(SHARED_PREF_FILE, Context.MODE_PRIVATE);
        this.prefsEditor_ = this.appSharedPrefs_.edit();
    }
    
    /**
     * <p>Singleton method to return the pre-initialised, or newly initialise and return, a singleton
     * object of the type {@link PrefHelper}.</p>
     *
     * @param context The {@link Context} within which the object should be instantiated; this
     *                parameter is passed to the private {@link #PrefHelper(Context)}
     *                constructor method.
     * @return A {@link PrefHelper} object instance.
     */
    public static PrefHelper getInstance(Context context) {
        if (prefHelper_ == null) {
            prefHelper_ = new PrefHelper(context);
        }
        return prefHelper_;
    }

    // Package Private
    static void shutDown() {
        if (prefHelper_ != null) {
            prefHelper_.prefsEditor_ = null;
        }

        // Reset all of the statics.
        enableLogging_ = false;
        prefHelper_ = null;
        customCDNBaseURL_ = null;
        customServerURL_ = null;
        useEUEndpoint_ = false;
    }

    /**
     * <p>Sets a custom base URL for all calls to the Branch API.  Requires https.</p>
     * @param url The {@link String} URL base URL that the Branch API uses.
     */
    static void setAPIUrl(String url) {
        customServerURL_ = url;
    }

    /**
     * <p>Returns the base URL to use for all calls to the Branch API as a {@link String}.</p>
     * NOTE: Below API v20, TLS 1.2 does not work reliably, so we will fall back in that case.
     *
     * @return A {@link String} variable containing the hard-coded base URL that the Branch
     * API uses.
     */
    public String getAPIBaseUrl() {
        if (URLUtil.isHttpsUrl(customServerURL_)) {
            return customServerURL_;
        }

        if (useEUEndpoint_) {
            return BRANCH_EU_BASE_URL_V3;
        }

        if (Build.VERSION.SDK_INT >= 20) {
            return BRANCH_BASE_URL_V2;
        } else {
            return BRANCH_BASE_URL_V1;
        }
    }

    /**
     * <p>Sets a custom CDN base URL.</p>
     * @param url The {@link String} base URL for CDN endpoints.
     */
    static void setCDNBaseUrl(String url) {
        customCDNBaseURL_ = url;
    }

    /**
     * <p>Returns the CDN base URL.
     *
     * @return A {@link String} variable containing the hard-coded CDN base URL that Branch uses or
     * custom CDN base URL set by the user.
     */
    public static String getCDNBaseUrl() {
        if (!TextUtils.isEmpty(customCDNBaseURL_)) {
            return customCDNBaseURL_;
        }

        return BRANCH_CDN_BASE_URL;
    }
    
    /**
     * <p>Sets the duration in milliseconds to override the timeout value for calls to the Branch API.</p>
     *
     * @param timeout The {@link Integer} value of the timeout setting in milliseconds.
     */
    public void setTimeout(int timeout) {
        setInteger(KEY_TIMEOUT, timeout);
    }
    
    /**
     * <p>Returns the currently set timeout value for calls to the Branch API. This will be the default
     * SDK setting unless it has been overridden manually between Branch object instantiation and
     * this call.</p>
     *
     * @return An {@link Integer} value containing the currently set timeout value in
     * milliseconds.
     */
    public int getTimeout() {
        return getInteger(KEY_TIMEOUT, TIMEOUT);
    }

    /**
     * <p>Returns the computed value of the connect and read timeout for web requests</p>
     *
     * @return An {@link Integer} value containing the currently set timeout value in
     * milliseconds.
     */
    public int getTaskTimeout() {
        return getInteger(KEY_TIMEOUT, TIMEOUT) + getInteger(KEY_CONNECT_TIMEOUT, CONNECT_TIMEOUT);
    }

    /**
     * <p>Sets the duration in milliseconds to override the timeout value for initiating requests.</p>
     *
     * @param connectTimeout The {@link Integer} value of the connect timeout setting in milliseconds.
     */
    public void setConnectTimeout(int connectTimeout) {
        setInteger(KEY_CONNECT_TIMEOUT, connectTimeout);
    }


    /**
     * <p>Returns the currently set timeout value for opening a communication channel with a remote
     * resource.</p>
     *
     * @return An {@link Integer} value containing the currently set timeout value in
     * milliseconds.
     */
    public int getConnectTimeout() {
        return getInteger(KEY_CONNECT_TIMEOUT, CONNECT_TIMEOUT);
    }
    
    /**
     * <p>Sets the value specifying the number of times that a Branch API call has been re-attempted.</p>
     * <p>
     * <p>This overrides the default retry value.</p>
     *
     * @param retry An {@link Integer} value specifying the value to be specified in preferences
     *              that determines the number of times that a Branch API call has been re-
     *              attempted.
     */
    public void setRetryCount(int retry) {
        setInteger(KEY_RETRY_COUNT, retry);
    }
    
    /**
     * <p>Gets the current count of the number of times that a Branch API call has been re-attempted.</p>
     *
     * @return An {@link Integer} value containing the current count of the number of times
     * that a Branch API call has been attempted.
     */
    public int getRetryCount() {
        return getInteger(KEY_RETRY_COUNT, MAX_RETRIES);
    }
    
    /**
     * <p>Sets the amount of time in milliseconds to wait before re-attempting a timed-out request
     * to the Branch API.</p>
     *
     * @param retryInt An {@link Integer} value specifying the number of milliseconds to wait
     *                 before re-attempting a timed-out request.
     */
    public void setRetryInterval(int retryInt) {
        setInteger(KEY_RETRY_INTERVAL, retryInt);
    }
    
    /**
     * <p>Gets the amount of time in milliseconds to wait before re-attempting a timed-out request
     * to the Branch API.</p>
     *
     * @return An {@link Integer} value containing the currently set retry interval in
     * milliseconds.
     */
    public int getRetryInterval() {
        return getInteger(KEY_RETRY_INTERVAL, INTERVAL_RETRY);
    }

    /**
     * In cases of persistent no internet connection or offline modes,
     * set a maximum number of attempts for the Branch Request to be tried.
     * @param retryInt
     */
    public void setNoConnectionRetryMax(int retryInt){
        setInteger(KEY_NO_CONNECTION_RETRY_MAX, retryInt);
    }

    /**
     * Returns the set retry count for Branch Requests
     * @return
     */
    public int getNoConnectionRetryMax(){
        return getInteger(KEY_NO_CONNECTION_RETRY_MAX, DEFAULT_NO_CONNECTION_RETRY_MAX);
    }
    
    /**
     * <p>Sets the value of {@link #KEY_APP_VERSION} in preferences.</p>
     *
     * @param version A {@link String} value containing the current app version.
     */
    public void setAppVersion(String version) {
        setString(KEY_APP_VERSION, version);
    }
    
    /**
     * <p>Returns the current value of {@link #KEY_APP_VERSION} as stored in preferences.</p>
     *
     * @return A {@link String} value containing the current app version.
     */
    public String getAppVersion() {
        return getString(KEY_APP_VERSION);
    }
    
    /**
     * Set the given Branch Key to preference. Clears the preference data if the key is a new key.
     *
     * @param key A {@link String} representing Branch Key.
     * @return A {@link Boolean} which is true if the key set is a new key. On Setting a new key need to clear all preference items.
     */
    public boolean setBranchKey(String key) {
        String currentBranchKey = getString(KEY_BRANCH_KEY);
        if (!currentBranchKey.equals(key)) {
            clearPrefOnBranchKeyChange();
            setString(KEY_BRANCH_KEY, key);

            // PrefHelper can be retrieved before Branch singleton is initialized
            if (Branch.getInstance() != null) {
                Branch.getInstance().linkCache_.clear();
                Branch.getInstance().requestQueue_.clear();
            }

            return true;
        }
        return false;
    }
    
    public String getBranchKey() {
        return getString(KEY_BRANCH_KEY);
    }
    
    /**
     * <p>Sets the randomized device token value of the current OS build, on the current device,
     * as a {@link String} in preferences.</p>
     *
     * @param randomized_device_token A {@link String} that uniquely identifies this build.
     */
    public void setRandomizedDeviceToken(String randomized_device_token) {
        setString(KEY_RANDOMIZED_DEVICE_TOKEN, randomized_device_token);
    }
    
    /**
     * <p>Gets the randomized device token value of the current OS build, on the current device,
     * as a {@link String} from preferences.</p>
     *
     * @return A {@link String} that uniquely identifies this build.
     */
    public String getRandomizedDeviceToken() {
        // If a newly (5.1.4+) set, valid, value exists, return it
        String rdt = getString(KEY_RANDOMIZED_DEVICE_TOKEN);
        if(!TextUtils.isEmpty(rdt) && !rdt.equals(NO_STRING_VALUE)){
            return rdt;
        }
        // Otherwise this call checks if we have the old "bnc_device_fingerprint_id",
        // or "bnc_no_value" if neither the new, or old value do not exist
        else {
            return getString(KEY_DEVICE_FINGERPRINT_ID);
        }
    }
    
    /**
     * <p>Sets the ID of the {@link #KEY_SESSION_ID} {@link String} value in preferences.</p>
     *
     * @param session_id A {@link String} value containing the session ID as returned by the
     *                   Branch API upon successful initialisation.
     */
    public void setSessionID(String session_id) {
        setString(KEY_SESSION_ID, session_id);
    }
    
    /**
     * <p>Gets the ID of the {@link #KEY_SESSION_ID} {@link String} value from preferences.</p>
     *
     * @return A {@link String} value containing the session ID as returned by the Branch
     * API upon successful initialisation.
     */
    public String getSessionID() {
        return getString(KEY_SESSION_ID);
    }
    
    /**
     * <p>Sets the {@link #KEY_RANDOMIZED_BUNDLE_TOKEN} {@link String} value that has been set via the Branch API.</p>
     * <p>
     * <p>This is used to identify a specific <b>user ID</b> and link that to a current session. Useful both
     * for analytics and debugging purposes.</p>
     * <p>
     * <p><b>Note: </b> Not to be confused with {@link #setIdentity(String)} - the name of the user</p>
     *
     * @param randomized_bundle_token A {@link String} value containing the currently configured identity
     *                    within preferences.
     */
    public void setRandomizedBundleToken(String randomized_bundle_token) {
        setString(KEY_RANDOMIZED_BUNDLE_TOKEN, randomized_bundle_token);
    }
    
    /**
     * <p>Gets the {@link #KEY_RANDOMIZED_BUNDLE_TOKEN} {@link String} value that has been set via the Branch API.</p>
     *
     * @return A {@link String} value containing the currently configured user id within
     * preferences.
     */
    public String getRandomizedBundleToken() {
        // If a newly (5.1.4+) set, valid, value exists, return it
        String rbt = getString(KEY_RANDOMIZED_BUNDLE_TOKEN);
        if(!TextUtils.isEmpty(rbt) && !rbt.equals(NO_STRING_VALUE)){
            return rbt;
        }
        // Otherwise this call checks if we have the old bnc_identity_id,
        // or "bnc_no_value" if neither the new, or old value do not exist
        else {
            return getString(KEY_IDENTITY_ID);
        }
    }
    
    /**
     * <p>Sets the {@link #KEY_IDENTITY} {@link String} value that has been set via the Branch API.</p>
     * <p>
     * <p>This is used to identify a specific <b>user identity</b> and link that to a current session. Useful both
     * for analytics and debugging purposes.</p>
     * <p>
     * <p><b>Note: </b> Not to be confused with {@link #setRandomizedBundleToken(String)} - the UID reference of the user</p>
     *
     * @param identity A {@link String} value containing the currently configured identity
     *                 within preferences.
     */
    public void setIdentity(String identity) {
        setString(KEY_IDENTITY, identity);
    }
    
    /**
     * <p>Gets the {@link #KEY_IDENTITY} {@link String} value that has been set via the Branch API.</p>
     * <p>
     * <p>This is used to identify a specific <b>user identity</b> and link that to a current session. Useful both
     * for analytics and debugging purposes.</p>
     *
     * @return A {@link String} value containing the username assigned to the currentuser ID.
     */
    public String getIdentity() {
        return getString(KEY_IDENTITY);
    }
    
    /**
     * <p>Sets the {@link #KEY_LINK_CLICK_ID} {@link String} value that has been set via the Branch API.</p>
     *
     * @param link_click_id A {@link String} value containing the identifier of the
     *                      associated link.
     */
    public void setLinkClickID(String link_click_id) {
        setString(KEY_LINK_CLICK_ID, link_click_id);
    }

    /**
     * Sets a new randomly generated UUID to be associated with the device.
     * @param uuid
     */
    public void setRandomlyGeneratedUuid(String uuid){
        setString(KEY_RANDOMLY_GENERATED_UUID, uuid);
    }

    /**
     * Returns our own randomly generated UUID associated with the device
     */
    public String getRandomlyGeneratedUuid() {
        return getString(KEY_RANDOMLY_GENERATED_UUID);
    }

    /**
     * Sets a new randomly generated UUID for some SAN APIs.
     * @param uuid
     */
    public void setAnonID(String uuid){
        setString(KEY_ANON_ID, uuid);
    }

    /**
     * Returns our own randomly generated UUID for some SAN APIs.
     */
    public String getAnonID() {
        return getString(KEY_ANON_ID);
    }
    
    /**
     * <p>Gets the {@link #KEY_LINK_CLICK_ID} {@link String} value that has been set via the Branch API.</p>
     *
     * @return A {@link String} value containing the identifier of the associated link.
     */
    public String getLinkClickID() {
        return getString(KEY_LINK_CLICK_ID);
    }

    /**
     * Specify whether ad network callouts should be disabled. By default, they are enabled.
     *
     * @param disabled (@link Boolean) whether ad network callouts should be disabled
     */
    public void setAdNetworkCalloutsDisabled(boolean disabled) {
        setBool(KEY_AD_NETWORK_CALLOUTS_DISABLED, disabled);
    }

    /**
     * Determine whether ad network callouts have been disabled.
     *
     * @return A (@link Boolean) indicating whether ad network callouts have been disabled.
     */
    public boolean getAdNetworkCalloutsDisabled() {
        return getBool(KEY_AD_NETWORK_CALLOUTS_DISABLED);
    }

    /**
     * <p>Sets the {@link #KEY_EXTERNAL_INTENT_URI} with value with given intent URI String.</p>
     *
     * @param uri A {@link String} value containing intent URI to set
     */
    public void setExternalIntentUri(String uri) {
        setString(KEY_EXTERNAL_INTENT_URI, uri);
    }
    
    /**
     * <p>Gets the {@link #KEY_EXTERNAL_INTENT_URI} {@link String} value that has been set via the Branch API.</p>
     *
     * @return A {@link String} value containing external URI set.
     */
    public String getExternalIntentUri() {
        return getString(KEY_EXTERNAL_INTENT_URI);
    }
    
    
    /**
     * <p>Sets the {@link #KEY_EXTERNAL_INTENT_EXTRA} with value with given intent extras in string format.</p>
     *
     * @param extras A {@link String} value containing intent URI extra to set
     */
    public void setExternalIntentExtra(String extras) {
        setString(KEY_EXTERNAL_INTENT_EXTRA, extras);
    }
    
    /**
     * <p>Gets the {@link #KEY_EXTERNAL_INTENT_EXTRA} {@link String} value that has been set via the Branch API.</p>
     *
     * @return A {@link String} value containing external intent extra set.
     */
    public String getExternalIntentExtra() {
        return getString(KEY_EXTERNAL_INTENT_EXTRA);
    }
    
    /**
     * <p>Sets the KEY_LINK_CLICK_IDENTIFIER {@link String} value that has been set via the Branch API.</p>
     *
     * @param identifier A {@link String} value containing the identifier of the associated
     *                   link.
     */
    public void setLinkClickIdentifier(String identifier) {
        setString(KEY_LINK_CLICK_IDENTIFIER, identifier);
    }
    
    
    /**
     * <p>Gets the KEY_LINK_CLICK_IDENTIFIER {@link String} value that has been set via the Branch API.</p>
     *
     * @return A {@link String} value containing the identifier of the associated link.
     */
    public String getLinkClickIdentifier() {
        return getString(KEY_LINK_CLICK_IDENTIFIER);
    }
    
    /**
     * Sets the Google install referrer identifier to the pref
     *
     * @param identifier Google install referrer identifier
     */
    public void setGoogleSearchInstallIdentifier(String identifier) {
        setString(KEY_GOOGLE_SEARCH_INSTALL_IDENTIFIER, identifier);
    }
    
    /**
     * Gets the google install referrer identifier
     *
     * @return {@link String} google install referrer identifier
     */
    public String getGoogleSearchInstallIdentifier() {
        return getString(KEY_GOOGLE_SEARCH_INSTALL_IDENTIFIER);
    }
    
    /**
     * Sets the app store install referrer string
     *
     * @param referrer App store install referrer string
     */
    public void setAppStoreReferrer(String referrer) {
        setString(KEY_GOOGLE_PLAY_INSTALL_REFERRER_EXTRA, referrer);
    }
    
    /**
     * Gets the app store install referrer string
     *
     * @return {@link String}  App store install referrer string
     */
    public String getAppStoreReferrer() {
        return getString(KEY_GOOGLE_PLAY_INSTALL_REFERRER_EXTRA);
    }

    public void setAppStoreSource(String store){
        if(!TextUtils.isEmpty(store)) {
            setString(KEY_APP_STORE_SOURCE, store);
        }
    }

    public String getAppStoreSource(){
        return getString(KEY_APP_STORE_SOURCE);
    }

    public void setIsMetaClickThrough(boolean isMetaClickThrough) {
        setBool(KEY_IS_META_CLICKTHROUGH, isMetaClickThrough);
    }

    public boolean getIsMetaClickThrough() {
        return getBool(KEY_IS_META_CLICKTHROUGH);
    }

    /**
     * Sets the referring URL query parameters.
     * @param referringUrlQueryParameters
     */
    public void setReferringUrlQueryParameters(JSONObject referringUrlQueryParameters) {
        if (referringUrlQueryParameters == null || referringUrlQueryParameters.length() == 0) {
            setString(KEY_REFERRING_URL_QUERY_PARAMETERS, NO_STRING_VALUE);
        } else {
            setString(KEY_REFERRING_URL_QUERY_PARAMETERS, referringUrlQueryParameters.toString());
        }
    }

    /**
     * Returns the referring URL query parameters.
     * @return
     */
    public JSONObject getReferringURLQueryParameters()  {
        String string = getString(KEY_REFERRING_URL_QUERY_PARAMETERS);
        JSONObject params = new JSONObject();

        if (!TextUtils.isEmpty(string) && !PrefHelper.NO_STRING_VALUE.equals(string)) {
            try {
                params = new JSONObject(string);
            } catch (JSONException e) {
                BranchLogger.w("Unable to get URL query parameters as string: " + e);
            }
        }
        return params;
    }

    /**
     * Sets the referrer Google Click ID with an expiration date computed by time set + expiration window
     * @param referrerGclid
     */
    public void setReferrerGclid(String referrerGclid){
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(KEY_GCLID_VALUE, referrerGclid);
            jsonObject.put(KEY_GCLID_EXPIRATION_DATE, System.currentTimeMillis() + getReferrerGclidValidForWindow());

            setString(KEY_GCLID_JSON_OBJECT, jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the persisted referrer Google Click ID
     * If the expiry date has passed, the entry is deleted and will return null
     * @return
     */
    public String getReferrerGclid(){
        String gclidObjectString = getString(KEY_GCLID_JSON_OBJECT);

        if(gclidObjectString.equals(NO_STRING_VALUE) ){
            return NO_STRING_VALUE;
        }

        String gclid = null;

        try {
            JSONObject gclidJsonObject = new JSONObject(gclidObjectString);
            long expiryDate = (long) gclidJsonObject.get(KEY_GCLID_EXPIRATION_DATE);

            // If expiry time has not elapsed, return it
            // No undefined behavior within bounds
            if(expiryDate - System.currentTimeMillis() > 0){
                gclid = gclidJsonObject.getString(KEY_GCLID_VALUE);
            }
            // Else delete it
            else{
                removePrefValue(KEY_GCLID_JSON_OBJECT);
            }
        } catch (JSONException e) {
            removePrefValue(KEY_GCLID_JSON_OBJECT);
            e.printStackTrace();
        }

        return gclid;
    }

    public void clearGclid() {
        removePrefValue(KEY_GCLID_JSON_OBJECT);
    }

    /**
     * Sets the GCLID expiration window in milliseconds
     * @param window
     */
    public void setReferrerGclidValidForWindow(long window){
        if (MAX_VALID_WINDOW_FOR_REFERRER_GCLID > window
                && window >= MIN_VALID_WINDOW_FOR_REFERRER_GCLID) {
            setLong(KEY_GCLID_VALID_FOR_WINDOW, window);
        }
    }

    /**
     * Gets the GCLID expiration window in milliseconds
     * @return
     */
    public long getReferrerGclidValidForWindow() {
        return getLong(KEY_GCLID_VALID_FOR_WINDOW, DEFAULT_VALID_WINDOW_FOR_REFERRER_GCLID);
    }

    /**
     * <p> Set the KEY_APP_LINK {@link String} values that has been started the application. </p>
     *
     * @param appLinkUrl The App link which started this application
     */
    public void setAppLink(String appLinkUrl) {
        setString(KEY_APP_LINK, appLinkUrl);
    }
    
    /**
     * <p> Get the App link which statrted the application.</p>
     *
     * @return A {@link String} value of App link url
     */
    public String getAppLink() {
        return getString(KEY_APP_LINK);
    }
    
    /**
     * Set the value for the full app conversion state. If set true indicate that this session is
     * initiated by a full app conversion flow
     *
     * @param isFullAppConversion {@link Boolean} with value for full app conversion state
     */
    public void setIsFullAppConversion(boolean isFullAppConversion) {
        setBool(KEY_IS_FULL_APP_CONVERSION, isFullAppConversion);
    }
    
    /**
     * Get the value for the full app conversion state.
     *
     * @return {@code true} if the session is initiated by a full app conversion flow
     */
    public boolean isFullAppConversion() {
        return getBool(KEY_IS_FULL_APP_CONVERSION);
    }
    
    /**
     * <p> Set the KEY_PUSH_IDENTIFIER {@link String} values that has been started the application. </p>
     *
     * @param pushIdentifier The Branch url with the push notification which started the app.
     */
    public void setPushIdentifier(String pushIdentifier) {
        setString(KEY_PUSH_IDENTIFIER, pushIdentifier);
    }
    
    /**
     * <p> Get the branch url in push payload which started the application.</p>
     *
     * @return A {@link String} value of push identifier
     */
    public String getPushIdentifier() {
        return getString(KEY_PUSH_IDENTIFIER);
    }
    
    /**
     * <p>Gets the session parameters as currently set in preferences.</p>
     * <p>
     * <p>Parameters are stored in JSON format, and must be parsed prior to access.</p>
     *
     * @return A {@link String} value containing the JSON-encoded structure of parameters for
     * the current session.
     */
    public String getSessionParams() {
        return getString(KEY_SESSION_PARAMS);
    }
    
    /**
     * <p>Sets the session parameters as currently set in preferences.</p>
     *
     * @param params A {@link String} value containing the JSON-encoded structure of
     *               parameters for the current session.
     */
    public void setSessionParams(String params) {
        setString(KEY_SESSION_PARAMS, params);
    }
    
    /**
     * <p>Gets the session parameters as originally set at time of app installation, in preferences.</p>
     *
     * @return A {@link String} value containing the JSON-encoded structure of parameters as
     * they were at the time of installation.
     */
    public String getInstallParams() {
        return getString(KEY_INSTALL_PARAMS);
    }
    
    /**
     * <p>Sets the session parameters as originally set at time of app installation, in preferences.</p>
     *
     * @param params A {@link String} value containing the JSON-encoded structure of
     *               parameters as they should be at the time of installation.
     */
    public void setInstallParams(String params) {
        setString(KEY_INSTALL_PARAMS, params);
    }
    
    public void setInstallReferrerParams(String params) {
        setString(KEY_INSTALL_REFERRER, params);
    }
    
    public String getInstallReferrerParams() {
        return getString(KEY_INSTALL_REFERRER);
    }
    
    /**
     * <p>Sets the user URL from preferences.</p>
     *
     * @param user_url A {@link String} value containing the current user URL.
     */
    public void setUserURL(String user_url) {
        setString(KEY_USER_URL, user_url);
    }
    
    /**
     * <p>Sets the user URL from preferences.</p>
     *
     * @return A {@link String} value containing the current user URL.
     */
    public String getUserURL() {
        return getString(KEY_USER_URL);
    }
    
    /**
     * <p>Resets the time that the system was last read. This is used to calculate how "stale" the
     * values are that are in use in preferences.</p>
     */
    public void clearSystemReadStatus() {
        Calendar c = Calendar.getInstance();
        setLong(KEY_LAST_READ_SYSTEM, c.getTimeInMillis() / 1000);
    }
    
    /*
     * Enables or disables FB app tracking.
     * @param isLimitFBAppTracking {@code true} to enable the app tracking.
     */
    void setLimitFacebookTracking(boolean isLimitFBAppTracking) {
        setBool(KEY_LIMIT_FACEBOOK_TRACKING, isLimitFBAppTracking);
    }
    
    /*
       Returns true if FB app tracking is enabled.
     */
    boolean isAppTrackingLimited() {
        return getBool(KEY_LIMIT_FACEBOOK_TRACKING);
    }

    /**
     * Returns true if DMA params - KEY_EEA_REGION value exist in pref helper.
     */
    boolean isDMAParamsInitialized() {
        if(hasPrefValue(KEY_DMA_EEA)){
            return true;
        }
        return false;
    }

    /**
     * <p>Internal setter/getter func for key eea_region </p>
     */
    void setEEARegion(boolean isEEARegion) {
        setBool(KEY_DMA_EEA, isEEARegion);
    }

    boolean getEEARegion() {
        return getBool(KEY_DMA_EEA);
    }

     /**
     * <p>Sets value of consent granted/denied by end user for ads personalization.</p>
     *  @param hasAdPersonalizationConsent {@code true} if user has given consent.
     */
    void setAdPersonalizationConsent(boolean hasAdPersonalizationConsent) {
        setBool(KEY_DMA_AD_PERSONALIZATION, hasAdPersonalizationConsent);
    }

    boolean getAdPersonalizationConsent() {
        return getBool(KEY_DMA_AD_PERSONALIZATION);
    }

    /**
     * <p>Sets value of consent granted/denied for 3P transmission of user level data for ads.</p>
     *  @param hasAdUserDataUsageConsent {@code true} if user has given consent.
     */
    void setAdUserDataUsageConsent(boolean hasAdUserDataUsageConsent) {
        setBool(KEY_DMA_AD_USER_DATA, hasAdUserDataUsageConsent);
    }

    boolean getAdUserDataUsageConsent() {
        return getBool(KEY_DMA_AD_USER_DATA);
    }


    /**
     * <p>Resets the user-related values that have been stored in preferences. This will cause a
     * sync to occur whenever a method reads any of the values and finds the value to be 0 or unset.</p>
     */
    public void clearUserValues() {
        ArrayList<String> actions = getActions();
        for (String action : actions) {
            setActionTotalCount(action, 0);
            setActionUniqueCount(action, 0);
        }
        setActions(new ArrayList<String>());
    }

    // EVENT REFERRAL INSTALL CALLS
    
    private ArrayList<String> getActions() {
        String actionList = getString(KEY_ACTIONS);
        if (actionList.equals(NO_STRING_VALUE)) {
            return new ArrayList<>();
        } else {
            return deserializeString(actionList);
        }
    }
    
    private void setActions(ArrayList<String> actions) {
        if (actions.size() == 0) {
            setString(KEY_ACTIONS, NO_STRING_VALUE);
        } else {
            setString(KEY_ACTIONS, serializeArrayList(actions));
        }
    }
    
    /**
     * <p>Sets the count of total number of times that the specified action has been carried out
     * during the current session, as defined in preferences.</p>
     *
     * @param action - A {@link String} value containing the name of the action to return the
     *               count for.
     * @param count  - An {@link Integer} value containing the total number of times that the
     *               specified action has been carried out during the current session.
     */
    public void setActionTotalCount(String action, int count) {
        ArrayList<String> actions = getActions();
        if (!actions.contains(action)) {
            actions.add(action);
            setActions(actions);
        }
        setInteger(KEY_TOTAL_BASE + action, count);
    }
    
    /**
     * <p>Sets the count of the unique number of times that the specified action has been carried
     * out during the current session, as defined in preferences.</p>
     *
     * @param action A {@link String} value containing the name of the action to return the
     *               count for.
     * @param count  An {@link Integer} value containing the total number of times that the
     *               specified action has been carried out during the current session.
     */
    public void setActionUniqueCount(String action, int count) {
        setInteger(KEY_UNIQUE_BASE + action, count);
    }
    
    /**
     * <p>Gets the count of total number of times that the specified action has been carried
     * out during the current session, as defined in preferences.</p>
     *
     * @param action A {@link String} value containing the name of the action to return the
     *               count for.
     * @return An {@link Integer} value containing the total number of times that the
     * specified action has been carried out during the current session.
     */
    public int getActionTotalCount(String action) {
        return getInteger(KEY_TOTAL_BASE + action);
    }
    
    /**
     * <p>Gets the count of the unique number of times that the specified action has been carried
     * out during the current session, as defined in preferences.</p>
     *
     * @param action A {@link String} value containing the name of the action to return the
     *               count for.
     * @return An {@link Integer} value containing the total number of times that the
     * specified action has been carried out during the current session.
     */
    public int getActionUniqueCount(String action) {
        return getInteger(KEY_UNIQUE_BASE + action);
    }

    /**
     * <p>Sets the latd attribution window, if attributionWindow is null, the saved latd attribution
     * window value will be deleted.</p>
     *
     * @param attributionWindow An {@link Integer} value containing the current attribution window passed
     */
    public void setLATDAttributionWindow(int attributionWindow){
        setInteger(KEY_LATD_ATTRIBUTION_WINDOW, attributionWindow);
    }

    /**
     * <p>Gets the latd attribution window</p>
     *
     * @return attributionWindow An {@link Integer} value containing the current attribution window or null
     */
    @SuppressWarnings("WeakerAccess")
    public int getLATDAttributionWindow(){
        return getInteger(KEY_LATD_ATTRIBUTION_WINDOW,
                ServerRequestGetLATD.defaultAttributionWindow);
    }

    /**
     * Persist the android.intent.extra.REFERRER value
     *
     * @param initialReferrer android.intent.extra.REFERRER
     */
    public void setInitialReferrer(String initialReferrer) {
        setString(KEY_INITIAL_REFERRER, initialReferrer);
    }

    /**
     * Get the persisted android.intent.extra.REFERRER value
     *
     * @return {@link String} android.intent.extra.REFERRER
     */
    public String getInitialReferrer() {
        return getString(KEY_INITIAL_REFERRER);
    }


    // ALL GENERIC CALLS
    
    private String serializeArrayList(ArrayList<String> strings) {
        String retString = "";
        for (String value : strings) {
            retString = retString + value + ",";
        }
        retString = retString.substring(0, retString.length() - 1);
        return retString;
    }
    
    private ArrayList<String> deserializeString(String list) {
        ArrayList<String> strings = new ArrayList<>();
        String[] stringArr = list.split(",");
        Collections.addAll(strings, stringArr);
        return strings;
    }

    /**
     * <p>A basic method that returns a {@link Boolean} indicating whether some preference exists.</p>
     *
     * @param key A {@link String} value containing the key to reference.
     * @return A {@link Boolean} indicating whether some preference exists.
     */
    public boolean hasPrefValue(String key) {
        return appSharedPrefs_.contains(key);
    }

    /**
     * <p>A basic method to remove some preference value.</p>
     *
     * @param key A {@link String} value containing the key to the value that's to be deleted.
     */
    public void removePrefValue(String key) {
        prefsEditor_.remove(key).apply();
    }
    
    /**
     * <p>A basic method that returns an integer value from a specified preferences Key.</p>
     *
     * @param key A {@link String} value containing the key to reference.
     * @return An {@link Integer} value of the specified key as stored in preferences.
     */
    public int getInteger(String key) {
        return getInteger(key, 0);
    }
    
    /**
     * <p>A basic method that returns an {@link Integer} value from a specified preferences Key, with a
     * default value supplied in case the value is null.</p>
     *
     * @param key          A {@link String} value containing the key to reference.
     * @param defaultValue An {@link Integer} specifying the value to use if the preferences value
     *                     is null.
     * @return An {@link Integer} value containing the value of the specified key, or the supplied
     * default value if null.
     */
    public int getInteger(String key, int defaultValue) {
        return appSharedPrefs_.getInt(key, defaultValue);
    }
    
    /**
     * <p>A basic method that returns a {@link Long} value from a specified preferences Key.</p>
     *
     * @param key A {@link String} value containing the key to reference.
     * @return A {@link Long} value of the specified key as stored in preferences.
     */
    public long getLong(String key) {
        return getLong(key, 0);
    }

    public long getLong(String key, long defaultValue) {
        return appSharedPrefs_.getLong(key, defaultValue);
    }
    
    /**
     * <p>A basic method that returns a {@link Float} value from a specified preferences Key.</p>
     *
     * @param key A {@link String} value containing the key to reference.
     * @return A {@link Float} value of the specified key as stored in preferences.
     */
    public float getFloat(String key) {
        return appSharedPrefs_.getFloat(key, 0);
    }
    
    /**
     * <p>A basic method that returns a {@link String} value from a specified preferences Key.</p>
     *
     * @param key A {@link String} value containing the key to reference.
     * @return A {@link String} value of the specified key as stored in preferences.
     */
    public String getString(String key) {
        return appSharedPrefs_.getString(key, NO_STRING_VALUE);
    }
    
    /**
     * <p>A basic method that returns a {@link Boolean} value from a specified preferences Key.</p>
     *
     * @param key A {@link String} value containing the key to reference.
     * @return An {@link Boolean} value of the specified key as stored in preferences.
     */
    public boolean getBool(String key) {
        return appSharedPrefs_.getBoolean(key, false);
    }
    
    /**
     * <p>Sets the value of the {@link String} key value supplied in preferences.</p>
     *
     * @param key   A {@link String} value containing the key to reference.
     * @param value An {@link Integer} value to set the preference record to.
     */
    public void setInteger(String key, int value) {
        prefsEditor_.putInt(key, value).apply();
    }
    
    /**
     * <p>Sets the value of the {@link String} key value supplied in preferences.</p>
     *
     * @param key   A {@link String} value containing the key to reference.
     * @param value A {@link Long} value to set the preference record to.
     */
    public void setLong(String key, long value) {
        prefsEditor_.putLong(key, value).apply();
    }
    
    /**
     * <p>Sets the value of the {@link String} key value supplied in preferences.</p>
     *
     * @param key   A {@link String} value containing the key to reference.
     * @param value A {@link Float} value to set the preference record to.
     */
    public void setFloat(String key, float value) {
        prefsEditor_.putFloat(key, value).apply();
    }
    
    /**
     * <p>Sets the value of the {@link String} key value supplied in preferences.</p>
     *
     * @param key   A {@link String} value containing the key to reference.
     * @param value A {@link String} value to set the preference record to.
     */
    public void setString(String key, String value) {
        prefsEditor_.putString(key, value).apply();
    }
    
    /**
     * <p>Sets the value of the {@link String} key value supplied in preferences.</p>
     *
     * @param key   A {@link String} value containing the key to reference.
     * @param value A {@link Boolean} value to set the preference record to.
     */
    public void setBool(String key, Boolean value) {
        prefsEditor_.putBoolean(key, value).apply();
    }

    /**
     * <p>Clears all the Branch referral shared preferences related to the current key.
     * Should be called before setting a new Branch-Key. </p>
     */
    //TODO: Ensure no object is being read here
    private void clearPrefOnBranchKeyChange() {
        // If stored key isn't the same as the current key, we need to clean up
        //TODO: Understand this note
        // Note: Link Click Identifier is not cleared because of the potential for that to mess up a deep link
        String linkClickID = getLinkClickID();
        String linkClickIdentifier = getLinkClickIdentifier();
        String appLink = getAppLink();
        String pushIdentifier = getPushIdentifier();
        prefsEditor_.clear();
        
        setLinkClickID(linkClickID);
        setLinkClickIdentifier(linkClickIdentifier);
        setAppLink(appLink);
        setPushIdentifier(pushIdentifier);
        prefsEditor_.apply();
    }
    
    public void setRequestMetadata(@NonNull String key, @NonNull String value) {
        if (key == null) {
            return;
        }

        if (this.requestMetadata.has(key) && value == null) {
            this.requestMetadata.remove(key);
        }

        try {
            this.requestMetadata.put(key, value);
        } catch (JSONException e) {
            // no-op
        }
    }
    
    public JSONObject getRequestMetadata() {
        return this.requestMetadata;
    }

    /**
     * adds the custom key-value pairs in the install request metadata
     *
     * @param key   A {@link String} value containing the key to reference.
     * @param value A {@link String} value of the specified key to be added in the request
     */
    void addInstallMetadata(String key, String value) {
        if (key == null) {
            return;
        }
        try {
            installMetadata.putOpt(key, value);
        } catch (JSONException e) {
            BranchLogger.d(e.getMessage());
        }
    }

    /**
     * gets the value for the specified key from the custom data from install request metadata
     *
     * @param key   A {@link String} value containing the key in the install meta data
     */
    String getInstallMetaData(String key) {
        if (key == null) {
            return null;
        }

        try {
           return this.installMetadata.get(key).toString();
        } catch (JSONException e) {
            BranchLogger.d(e.getMessage());
            return null;
        }
    }

    public JSONObject getInstallMetadata() {
        return installMetadata;
    }

    //TODO: Remove
    /**
     * helper method to check of the modules need to be added in the requests
     *
     * @return value A {@link Boolean} returns true if the module data is present else false
     */
    boolean shouldAddModules () {
        try {
            return secondaryRequestMetadata.length() != 0;
        } catch (Exception e) {
            BranchLogger.d(e.getMessage());
            return false;
        }
    }

    private static boolean enableLogging_ = false;
    private static boolean useEUEndpoint_ = false;

    public static String fbAppId_ = null;

    static void enableLogging(boolean fEnable) {
        enableLogging_ = fEnable;
    }

    static void useEUEndpoint(boolean enable) {
        useEUEndpoint_ = enable;
    }

    boolean hasValidBranchKey() {
        return isValidBranchKey(getBranchKey());
    }

    static boolean isValidBranchKey(String branchKey) {
        return branchKey != null && branchKey.startsWith(isTestModeEnabled() ? "key_test_" : "key_");
    }

    public void loadPartnerParams(JSONObject body) throws JSONException {
        loadPartnerParams(body, partnerParams_);
    }

    // package private loadPartnerParams(...) allows to unit test BranchPartnerParameters, besides tests, this should only be invoked from the public loadPartnerParams(...) method.
    static void loadPartnerParams(JSONObject body, BranchPartnerParameters partnerParams) throws JSONException {
        if (body == null) return;
        JSONObject partnerData = new JSONObject();
        for (Map.Entry<String, ConcurrentHashMap<String, String>> e : partnerParams.allParams().entrySet()) {
            JSONObject individualPartnerParams = new JSONObject();
            for (Map.Entry<String, String> p : e.getValue().entrySet()) {
                individualPartnerParams.put(p.getKey(), p.getValue());
            }
            partnerData.put(e.getKey(), individualPartnerParams);
        }
        body.put(Defines.Jsonkey.PartnerData.getKey(), partnerData);
    }

    /**
     * <p>Gets the {@link Defines.BranchAttributionLevel} value that has been set.</p>
     *
     * @return A {@link Defines.BranchAttributionLevel} value representing the attribution level set.
     */
    public Defines.BranchAttributionLevel getConsumerProtectionAttributionLevel() {
        String levelString = getString(KEY_CONSUMER_PROTECTION_ATTRIBUTION_LEVEL);
        if (levelString.equals(NO_STRING_VALUE)) {
            return Defines.BranchAttributionLevel.FULL;
        } else {
            return Defines.BranchAttributionLevel.valueOf(levelString);
        }
    }

    /**
     * <p>Sets the {@link Defines.BranchAttributionLevel} value via the Branch API.</p>
     *
     * @param preference A {@link Defines.BranchAttributionLevel} value containing the desired attribution level.
     */
    public void setConsumerProtectionAttributionLevel(Defines.BranchAttributionLevel preference) {
        setString(KEY_CONSUMER_PROTECTION_ATTRIBUTION_LEVEL, preference.toString());
    }

    /**
     * Returns true if Consumer Protection Attribution Level - KEY_CONSUMER_PROTECTION_ATTRIBUTION_LEVEL value exist in pref helper.
     */
    boolean isAttributionLevelInitialized() {
        return hasPrefValue(KEY_CONSUMER_PROTECTION_ATTRIBUTION_LEVEL);
    }
}
