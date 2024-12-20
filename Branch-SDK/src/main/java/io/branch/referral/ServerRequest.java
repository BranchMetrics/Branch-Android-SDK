package io.branch.referral;

import static io.branch.referral.ServerRequestInitSession.INITIATED_BY_CLIENT;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import java.util.UUID;

/**
 * Abstract class defining the structure of a Branch Server request.
 */
public abstract class ServerRequest {

    private static final Defines.RequestPath[] initializationAndEventRoutes = new Defines.RequestPath[]{
            Defines.RequestPath.RegisterInstall,
            Defines.RequestPath.RegisterOpen,
            Defines.RequestPath.ContentEvent,
            Defines.RequestPath.TrackStandardEvent,
            Defines.RequestPath.TrackCustomEvent};
    
    private static final String POST_KEY = "REQ_POST";
    private static final String POST_PATH_KEY = "REQ_POST_PATH";
    protected long creation_ts = 0;
    protected String uuid;

    private JSONObject params_;
    final Defines.RequestPath requestPath_;
    protected final PrefHelper prefHelper_;
    private long queueWaitTime_ = 0;
    private final Context context_;

    // Various process wait locks for Branch server request
    public enum PROCESS_WAIT_LOCK {
        SDK_INIT_WAIT_LOCK, GAID_FETCH_WAIT_LOCK, INTENT_PENDING_WAIT_LOCK, USER_SET_WAIT_LOCK, INSTALL_REFERRER_FETCH_WAIT_LOCK, USER_AGENT_STRING_LOCK
    }
    
    // Set for holding any active wait locks
    private final Set<PROCESS_WAIT_LOCK> locks_;
    
    /*True if there is an error in creating this request such as error with json parameters.*/
    public boolean constructError_ = false;
    
    public enum BRANCH_API_VERSION {
        V1,
        V1_LATD,
        V2
    }

    public int currentRetryCount = 0;

    /**
     * <p>Creates an instance of ServerRequest.</p>
     *
     * @param context     Application context.
     * @param requestPath Path to server for this request.
     */
    public ServerRequest(Context context, Defines.RequestPath requestPath) {
        this(requestPath, new JSONObject(), context);
    }
    
    /**
     * <p>Creates an instance of ServerRequest.</p>
     *
     * @param requestPath Path to server for this request.
     * @param post        A {@link JSONObject} containing the post data supplied with the current request
     *                    as key-value pairs.
     * @param context     Application context.
     */
    protected ServerRequest(Defines.RequestPath requestPath, JSONObject post, Context context) {
        BranchLogger.v("ServerRequest constructor");
        context_ = context;
        requestPath_ = requestPath;
        params_ = post;
        prefHelper_ = PrefHelper.getInstance(context);
        locks_ = new HashSet<>();

        creation_ts = System.currentTimeMillis();
        String creation_ts_date_formatted = formatUnixEpochToDateFormat(creation_ts);
        uuid =  getRequestUuid(creation_ts_date_formatted);
    }

    /**
     * Returns Unix epoch time converted to readable date format: year-month-dayOfMonth-hour with - prefix
     * Force Locale US character representation
     * @param creationTs
     * @return
     */
    private String formatUnixEpochToDateFormat(long creationTs) {
        SimpleDateFormat sdf = new SimpleDateFormat("-yyyyMMddHH", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        return sdf.format(creationTs);
    }

    /**
     * Appends formatted time stamp to randomly generated UUID
     * @param creationTsDateFormatted
     * @return
     */
    private String getRequestUuid(String creationTsDateFormatted) {
        return UUID.randomUUID().toString() + creationTsDateFormatted;
    }

    /**
     * <p>Should be implemented by the child class.Specifies any error associated with request.
     * If there are errors request will not be executed.</p>
     *
     * @param context Application context.
     * @return A {@link Boolean} which is set to true if there are errors with this request.
     * Child class is responsible for implementing its own logic for error check and reporting.
     */
    public abstract boolean handleErrors(Context context);
    
    /**
     * <p>Called when execution of this request to server succeeds. Child class should implement
     * its own logic for handling the post request execution.</p>
     *
     * @param response A {@link ServerResponse} object containing server response for this request.
     * @param branch   Current {@link Branch} instance
     */
    public abstract void onRequestSucceeded(ServerResponse response, Branch branch);
    
    /**
     * <p>Called when there is an error on executing this request. Child class should handle the failure
     * accordingly.</p>
     *
     * @param statusCode A {@link Integer} value specifying http return code or any branch specific error defined in {@link BranchError}.
     * @param causeMsg   A {@link String} value specifying cause for the error if any.
     */
    public abstract void handleFailure(int statusCode, String causeMsg);
    
    /**
     * Specify whether the request is a GET or POST. Child class has to implement accordingly.
     *
     * @return A {@link Boolean} value specifying if this request is a GET or not.
     */
    public abstract boolean isGetRequest();
    
    /**
     * Clears the callbacks associated to this request.
     */
    public abstract void clearCallbacks();
    
    /**
     * Specifies whether to retry this request on failure. By default request is not retried on fail.
     * Those request which need to retry on failure should override and handle accordingly
     *
     * @return A {@link Boolean} whose values is true if request needed to retry on failure.
     */
    public boolean shouldRetryOnFail() {
        return false;
    }

    /**
     * Specifies whether this request should add the limit app tracking value
     *
     * @return {@code true} to add the limit app tracking value to the request else false.
     * {@code false} by default. Should override for requests that need limited app tracking value.
     */
    protected boolean shouldUpdateLimitFacebookTracking() {
        return false;
    }

    /**
     * <p>
     * Specifies whether this request should have DMA params.
     * By default it will return false. Subclasses can override this function, if the corresponding request type
     * requires DMA params.
     * </p>
     *
     * @return A {@link Boolean} with value false if this request does NOT need DMA params.
     */
    protected boolean shouldAddDMAParams() {
        return false;
    }

    /**
     * Adds the google DMA Compliance parameters.
     */
    void addDMAParams() {
        if (prefHelper_.isDMAParamsInitialized()) {
            try {
                BRANCH_API_VERSION version = getBranchRemoteAPIVersion();
                if (version == BRANCH_API_VERSION.V1) {
                    params_.put(Defines.Jsonkey.DMA_EEA.getKey(), prefHelper_.getEEARegion());
                    params_.put(Defines.Jsonkey.DMA_Ad_Personalization.getKey(), prefHelper_.getAdPersonalizationConsent());
                    params_.put(Defines.Jsonkey.DMA_Ad_User_Data.getKey(), prefHelper_.getAdUserDataUsageConsent());
                } else {
                    JSONObject userDataObj = params_.optJSONObject(Defines.Jsonkey.UserData.getKey());
                    if (userDataObj != null) {
                        userDataObj.put(Defines.Jsonkey.DMA_EEA.getKey(), prefHelper_.getEEARegion());
                        userDataObj.put(Defines.Jsonkey.DMA_Ad_Personalization.getKey(), prefHelper_.getAdPersonalizationConsent());
                        userDataObj.put(Defines.Jsonkey.DMA_Ad_User_Data.getKey(), prefHelper_.getAdUserDataUsageConsent());
                    }
                }
            } catch (JSONException e) {
                BranchLogger.d(e.getMessage());
            }
        }
    }

    private void addConsumerProtectionAttributionLevel() {
        if (prefHelper_.isAttributionLevelInitialized()) {
            try {
                if (getBranchRemoteAPIVersion() == BRANCH_API_VERSION.V1) {
                    params_.put(Defines.Jsonkey.Consumer_Protection_Attribution_Level.getKey(), prefHelper_.getConsumerProtectionAttributionLevel().toString());
                } else {
                    JSONObject userDataObj = params_.optJSONObject(Defines.Jsonkey.UserData.getKey());
                    if (userDataObj != null) {
                        userDataObj.put(Defines.Jsonkey.Consumer_Protection_Attribution_Level.getKey(), prefHelper_.getConsumerProtectionAttributionLevel().toString());
                    }
                }
            } catch (JSONException e) {
                BranchLogger.d(e.getMessage());
            }
        }
    }

    /**
     * <p>Provides the path to server for this request.
     * see {@link Defines.RequestPath} <p>
     *
     * @return Path for this request.
     */
    public final String getRequestPath() {
        return requestPath_.getPath();
    }
    
    /**
     * <p>Provides the complete url for executing this request. URl consist of API base url and request
     * path. Child class need to extend this method if they need to add specific items to the url </p>
     *
     * @return A url for executing this request against the server.
     */
    public String getRequestUrl() {
        return prefHelper_.getAPIBaseUrl() + requestPath_.getPath();
    }
    
    /**
     * <p>Sets a {@link JSONObject} containing the post data supplied with the current request.</p>
     *
     * @param post A {@link JSONObject} containing the post data supplied with the current request
     *             as key-value pairs.
     */
    protected void setPost(JSONObject post) throws JSONException {
        BranchLogger.v("setPost start " + post  + " " + this);
        params_ = post;

        if (getBranchRemoteAPIVersion() == BRANCH_API_VERSION.V1) {
            BranchLogger.v("updateRequestWithV1Params " + this);
            DeviceInfo.getInstance().updateRequestWithV1Params(this, params_);
        }
        else {
            JSONObject userDataObj = new JSONObject();
            params_.put(Defines.Jsonkey.UserData.getKey(), userDataObj);
            BranchLogger.v("updateRequestWithV2Params " + this);
            DeviceInfo.getInstance().updateRequestWithV2Params(this, prefHelper_, userDataObj);
        }

        params_.put(Defines.Jsonkey.Debug.getKey(), Branch.isDeviceIDFetchDisabled());

        BranchLogger.v("setPost complete " + post  + " " + this);
    }
    
    /**
     * <p>Gets a {@link JSONObject} containing the post data supplied with the current request as
     * key-value pairs.</p>
     *
     * @return A {@link JSONObject} containing the post data supplied with the current request
     * as key-value pairs.
     */
    public JSONObject getPost() {
        return params_;
    }
    
    /**
     * <p>
     * Specifies whether this request need to be updated with Google Ads Id and LAT value
     * By default update GAds params update is turned on. Override this on request which need to have GAds params
     * </p>
     *
     * @return A {@link Boolean} with value true if this request need GAds params
     */
    public boolean isGAdsParamsRequired() {
        return true;
    }
    
    /**
     * <p>Gets a {@link JSONObject} containing the post data supplied with the current request as
     * key-value pairs appended with the instrumentation data.</p>
     * <p>
     * * @param instrumentationData {@link ConcurrentHashMap} with instrumentation values
     *
     * @return A {@link JSONObject} containing the post data supplied with the current request
     * as key-value pairs and the instrumentation meta data.
     */
    public JSONObject getPostWithInstrumentationValues(ConcurrentHashMap<String, String> instrumentationData) {
        JSONObject extendedPost = new JSONObject();
        try {
            //Add original parameters
            if (params_ != null) {
                JSONObject originalParams = new JSONObject(params_.toString());
                Iterator<String> keys = originalParams.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    extendedPost.put(key, originalParams.get(key));
                }
            }
            // Append instrumentation metadata
            if (instrumentationData.size() > 0) {
                JSONObject instrObj = new JSONObject();
                Set<String> keys = instrumentationData.keySet();
                try {
                    for (String key : keys) {
                        instrObj.put(key, instrumentationData.get(key));
                        instrumentationData.remove(key);
                    }
                    extendedPost.put(Defines.Jsonkey.Branch_Instrumentation.getKey(), instrObj);
                } catch (JSONException e) {
                    BranchLogger.w("Caught JSONException " + e.getMessage());
                }
            }
        } catch (JSONException e) {
            BranchLogger.d(e.getMessage());
        } catch (ConcurrentModificationException ex) {
            extendedPost = params_;
        }
        return extendedPost;
    }
    
    /**
     * Returns a JsonObject with the parameters that needed to be set with the get request.
     *
     * @return A {@link JSONObject} representation of get request parameters.
     */
    public JSONObject getGetParams() {
        return params_;
    }
    
    /**
     * Adds a param and its value to the get request
     *
     * @param paramKey   A {@link String} value for the get param key
     * @param paramValue A {@link String} value for the get param value
     */
    protected void addGetParam(String paramKey, String paramValue) {
        try {
            params_.put(paramKey, paramValue);
        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException " + e.getMessage());
        }
    }
    
    /**
     * <p>Gets a {@link JSONObject} corresponding to the {@link ServerRequest} and
     * {@link ServerRequest#POST_KEY} as currently configured.</p>
     *
     * @return A {@link JSONObject} corresponding to the values of {@link ServerRequest} and
     * {@link ServerRequest#POST_KEY} as currently configured, or <i>null</i> if
     * one or both of those values have not yet been set.
     */
    @CallSuper
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put(POST_KEY, params_);
            json.put(POST_PATH_KEY, requestPath_.getPath());
        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException " + e.getMessage());
            return null;
        }
        return json;
    }

    // TODO: Replace with in-memory only ServerRequest objects.
    /**
     * <p>Converts a {@link JSONObject} object containing keys stored as key-value pairs into
     * a {@link ServerRequest}.</p>
     *
     * @param json    A {@link JSONObject} object containing post data stored as key-value pairs
     * @param context Application context.
     * @return A {@link ServerRequest} object with the {@link #POST_KEY}
     * values set if not null; this can be one or the other. If both values in the
     * supplied {@link JSONObject} are null, null is returned instead of an object.
     */
    public static ServerRequest fromJSON(JSONObject json, Context context) {
        JSONObject post = null;
        String requestPath = "";
        boolean initiatedByClient = true;
        try {
            if (json.has(POST_KEY)) {
                post = json.getJSONObject(POST_KEY);
            }
        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException " + e.getMessage());
            // it's OK for post to be null
        }
        
        try {
            if (json.has(POST_PATH_KEY)) {
                requestPath = json.getString(POST_PATH_KEY);
            }
        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException " + e.getMessage());
            // it's OK for post to be null
        }

        try {
            if (json.has(INITIATED_BY_CLIENT)) {
                initiatedByClient = json.getBoolean(INITIATED_BY_CLIENT);
            }
        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException " + e.getMessage());
        }

        if (!TextUtils.isEmpty(requestPath)) {
            return getExtendedServerRequest(requestPath, post, context, initiatedByClient);
        }
        return null;
    }

    // TODO: Replace with in-memory only ServerRequest objects.
    /**
     * <p>Factory method for creating the specific server requests objects. Creates requests according
     * to the request path.</p>
     *
     * @param requestPath Path for the server request. see {@link Defines.RequestPath}
     * @param post        A {@link JSONObject} object containing post data stored as key-value pairs.
     * @param context     Application context.
     * @return A {@link ServerRequest} object for the given Post data.
     */
    private static ServerRequest getExtendedServerRequest(String requestPath, JSONObject post, Context context, boolean initiatedByClient) {
        ServerRequest extendedReq = null;

        if (requestPath.equalsIgnoreCase(Defines.RequestPath.GetURL.getPath())) {
            extendedReq = new ServerRequestCreateUrl(Defines.RequestPath.GetURL, post, context);
        } else if (requestPath.equalsIgnoreCase(Defines.RequestPath.RegisterInstall.getPath())) {
            extendedReq = new ServerRequestRegisterInstall(Defines.RequestPath.RegisterInstall, post, context, initiatedByClient);
        } else if (requestPath.equalsIgnoreCase(Defines.RequestPath.RegisterOpen.getPath())) {
            extendedReq = new ServerRequestRegisterOpen(Defines.RequestPath.RegisterOpen, post, context, initiatedByClient);
        }
        return extendedReq;
    }

    /**
     * Updates the google ads parameters. This should be called only from a background thread since it involves GADS method invocation using reflection
     * Ensure that when there is a valid GAID/AID, remove the SSAID if it's being used
     * Otherwise we're good to send the generated UUID
     */
    void updateGAdsParams() {
        BRANCH_API_VERSION version = getBranchRemoteAPIVersion();
        int LATVal = DeviceInfo.getInstance().getSystemObserver().getLATVal();
        String gaid = DeviceInfo.getInstance().getSystemObserver().getAID();
        if (!TextUtils.isEmpty(gaid)) {
            if (prefHelper_.getConsumerProtectionAttributionLevel() == Defines.BranchAttributionLevel.FULL || !prefHelper_.isAttributionLevelInitialized()) {
                updateAdvertisingIdsObject(gaid);
                // gaid is put in the request body below, calling to remove hardware id from request now
                replaceHardwareIdOnValidAdvertisingId();
            }
        }
        try {
            if (version == BRANCH_API_VERSION.V1) {
                params_.put(Defines.Jsonkey.LATVal.getKey(), LATVal);
                if (!TextUtils.isEmpty(gaid)) {
                    if (!SystemObserver.isHuaweiMobileServicesAvailable(context_)) {
                        // Fire OS overloads ad id (representing it as Google ad id at the top level),
                        // HUAWEI only reports ad id in the advertising_ids object
                        if (prefHelper_.getConsumerProtectionAttributionLevel() == Defines.BranchAttributionLevel.FULL || !prefHelper_.isAttributionLevelInitialized()) {
                            params_.put(Defines.Jsonkey.GoogleAdvertisingID.getKey(), gaid);
                        }
                    }
                    params_.remove(Defines.Jsonkey.UnidentifiedDevice.getKey());
                } else if (!payloadContainsDeviceIdentifiers(params_) &&
                        !params_.optBoolean(Defines.Jsonkey.UnidentifiedDevice.getKey())) {
                    params_.put(Defines.Jsonkey.UnidentifiedDevice.getKey(), true);
                }
            } else {
                JSONObject userDataObj = params_.optJSONObject(Defines.Jsonkey.UserData.getKey());
                if (userDataObj != null) {
                    userDataObj.put(Defines.Jsonkey.LimitedAdTracking.getKey(), LATVal);
                    if (!TextUtils.isEmpty(gaid)) {
                        if (!SystemObserver.isHuaweiMobileServicesAvailable(context_)) {
                            // Fire OS overloads ad id (representing it as Google ad id at the top level),
                            // HUAWEI only reports ad id in the advertising_ids object
                            if (prefHelper_.getConsumerProtectionAttributionLevel() == Defines.BranchAttributionLevel.FULL || !prefHelper_.isAttributionLevelInitialized()) {
                                userDataObj.put(Defines.Jsonkey.AAID.getKey(), gaid);
                            }
                        }
                        userDataObj.remove(Defines.Jsonkey.UnidentifiedDevice.getKey());
                    } else if (!payloadContainsDeviceIdentifiers(userDataObj) &&
                            !userDataObj.optBoolean(Defines.Jsonkey.UnidentifiedDevice.getKey())) {
                        userDataObj.put(Defines.Jsonkey.UnidentifiedDevice.getKey(), true);
                    }
                }
            }
        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException " + e.getMessage());
        }
    }

    private void updateAdvertisingIdsObject(@NonNull String aid) {
        try {
            String key;
            if (SystemObserver.isFireOSDevice()) {
                key = Defines.Jsonkey.FireAdId.getKey();
            } else if (SystemObserver.isHuaweiMobileServicesAvailable(
                    Branch.getInstance().getApplicationContext())) {
                key = Defines.Jsonkey.OpenAdvertisingID.getKey();
            } else {
                key = Defines.Jsonkey.AAID.getKey();
            }

            JSONObject advertisingIdsObject = new JSONObject().put(key, aid);
            params_.put(Defines.Jsonkey.AdvertisingIDs.getKey(), advertisingIdsObject);
        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException " + e.getMessage());
        }
    }

    /**
     * Called when advertising ids are successfully set on the request body
     * Because params including hardware id are set on the request before the advertising ids are obtained,
     * remove the hardware ID and disable future calls from reading it
     */
    private void replaceHardwareIdOnValidAdvertisingId(){
        try {
            //v1
            SystemObserver.UniqueId generatedHardwareID = DeviceInfo.getInstance().getHardwareID();

            // Replace the hardware id with randomly generated UUID, generate new one if we haven't previously
            params_.put(Defines.Jsonkey.HardwareID.getKey(), generatedHardwareID.getId());
            params_.put(Defines.Jsonkey.IsHardwareIDReal.getKey(), generatedHardwareID.isReal());

            //v2
            if(params_.has(Defines.Jsonkey.UserData.getKey())) {
                JSONObject userData = params_.getJSONObject(Defines.Jsonkey.UserData.getKey());
                if (userData.has(Defines.Jsonkey.AndroidID.getKey())) {
                    userData.put(Defines.Jsonkey.AndroidID.getKey(), generatedHardwareID.getId());
                }
            }
        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException " + e.getMessage());
        }
    }
    
    private boolean payloadContainsDeviceIdentifiers(JSONObject payload) {
        return payload.has(Defines.Jsonkey.AndroidID.getKey()) ||
                payload.has(Defines.Jsonkey.RandomizedDeviceToken.getKey());
    }
    
    private void updateDeviceInfo() {
        BRANCH_API_VERSION version = getBranchRemoteAPIVersion();
        if (version == BRANCH_API_VERSION.V2) {
            JSONObject userDataObj = params_.optJSONObject(Defines.Jsonkey.UserData.getKey());
            if (userDataObj != null) {
                try {
                    userDataObj.put(Defines.Jsonkey.DeveloperIdentity.getKey(), prefHelper_.getIdentity());
                    userDataObj.put(Defines.Jsonkey.RandomizedDeviceToken.getKey(), prefHelper_.getRandomizedDeviceToken());
                } catch (JSONException e) {
                    BranchLogger.w("Caught JSONException " + e.getMessage());
                }
            }
        }
    }
    
    
    /**
     * Update the additional metadata provided using {@link Branch#setRequestMetadata(String, String)} to the requests.
     */
    private void updateRequestMetadata() {
        // Take event level metadata, merge with top level metadata
        // event level metadata takes precedence
        try {
            JSONObject metadata = new JSONObject();
            Iterator<String> i = prefHelper_.getRequestMetadata().keys();
            while (i.hasNext()) {
                String k = i.next();
                metadata.put(k, prefHelper_.getRequestMetadata().get(k));
            }
            JSONObject originalMetadata = params_.optJSONObject(Defines.Jsonkey.Metadata.getKey());
            if (originalMetadata != null) {
                Iterator<String> postIter = originalMetadata.keys();
                while (postIter.hasNext()) {
                    String key = postIter.next();
                    // override keys from above
                    metadata.put(key, originalMetadata.get(key));
                }
            }
            // Install metadata need to be send only with Install request
            if ((this instanceof ServerRequestRegisterInstall) && prefHelper_.getInstallMetadata().length() > 0) {
                Iterator<String> postIterInstallMetaData = prefHelper_.getInstallMetadata().keys();
                while (postIterInstallMetaData.hasNext()) {
                    String key = postIterInstallMetaData.next();
                    // override keys from above
                    params_.putOpt(key, prefHelper_.getInstallMetadata().get(key));
                }
            }
            params_.put(Defines.Jsonkey.Metadata.getKey(), metadata);
        } catch (JSONException e) {
            BranchLogger.w("Caught JSONException. Could not merge metadata, ignoring user metadata. " + e.getMessage());
        }
    }
    
    /*
     * Update the the limit app tracking value to the request
     */
    private void updateLimitFacebookTracking() {
        JSONObject updateJson = getBranchRemoteAPIVersion() == BRANCH_API_VERSION.V1 ? params_ : params_.optJSONObject(Defines.Jsonkey.UserData.getKey());
        if (updateJson != null) {
            boolean isLimitFacebookTracking = prefHelper_.isAppTrackingLimited(); // Currently only FB app tracking
            if (isLimitFacebookTracking) {
                try {
                    updateJson.putOpt(Defines.Jsonkey.limitFacebookTracking.getKey(), isLimitFacebookTracking);
                } catch (JSONException e) {
                    BranchLogger.w("Caught JSONException " + e.getMessage());
                }
            }
        }
    }

    private void updateDisableAdNetworkCallouts() {
        JSONObject updateJson = getBranchRemoteAPIVersion() == BRANCH_API_VERSION.V1 ? params_ : params_.optJSONObject(Defines.Jsonkey.UserData.getKey());
        if (updateJson != null) {
            boolean disableAdNetworkCallouts = prefHelper_.getAdNetworkCalloutsDisabled();
            if (disableAdNetworkCallouts) {
                try {
                    updateJson.putOpt(Defines.Jsonkey.DisableAdNetworkCallouts.getKey(), disableAdNetworkCallouts);
                } catch (JSONException e) {
                    BranchLogger.w("Caught JSONException " + e.getMessage());
                }
            }
        }
    }

    private boolean prioritizeLinkAttribution(JSONObject params) {
        if (Branch.isReferringLinkAttributionForPreinstalledAppsEnabled()
                && params.has(Defines.Jsonkey.LinkIdentifier.getKey())) {
            return true;
        }
        return false;
    }

    private void removePreinstallData(JSONObject params) {
        params.remove(Defines.PreinstallKey.partner.getKey());
        params.remove(Defines.PreinstallKey.campaign.getKey());
        params.remove(Defines.Jsonkey.GooglePlayInstallReferrer.getKey());
    }
    
    void doFinalUpdateOnMainThread() {
        BranchLogger.v("doFinalUpdateOnMainThread");
        updateRequestMetadata();
        if (shouldUpdateLimitFacebookTracking()) {
            updateLimitFacebookTracking();
        }
        if (shouldAddDMAParams()) {
            addDMAParams();
        }

      addConsumerProtectionAttributionLevel();

        // Always add these fields
        addClientRequestParameters();
    }

    /**
     * Put request time stamp and uuid at top level of POST body
     */
    private void addClientRequestParameters() {
        if(prefHelper_ != null){
            try {
                params_.put(Defines.Jsonkey.Branch_Sdk_Request_Creation_Time_Stamp.getKey(), this.creation_ts);
                params_.put(Defines.Jsonkey.Branch_Sdk_Request_Uuid.getKey(), this.uuid);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void doFinalUpdateOnBackgroundThread() {
        BranchLogger.v("doFinalUpdateOnBackgroundThread");
        if (this instanceof ServerRequestInitSession) {
            ((ServerRequestInitSession) this).updateLinkReferrerParams();
            if (prioritizeLinkAttribution(this.params_)) {
                removePreinstallData(this.params_);
            }
        }
        
        // Update the dynamic device info params
        updateDeviceInfo();
        updateDisableAdNetworkCallouts();
        //Google ADs ID  and LAT value are updated using reflection. These method need background thread
        //So updating them for install and open on background thread.

        if (isGAdsParamsRequired()) {
            updateGAdsParams();
        }
    }
    
    /*
     * Checks if this Application has internet permissions.
     *
     * @param context Application context.
     *
     * @return True if application has internet permission.
     */
    
    protected boolean doesAppHasInternetPermission(Context context) {
        int result = context.checkCallingOrSelfPermission(Manifest.permission.INTERNET);
        boolean permissionGranted = (result == PackageManager.PERMISSION_GRANTED);

        if (!permissionGranted) {
            BranchLogger.v("Trouble executing your request. Please add 'android.permission.INTERNET' in your applications manifest file");
        }

        return result == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Called when request is added to teh queue
     */
    public void onRequestQueued() {
        queueWaitTime_ = System.currentTimeMillis();
    }
    
    /**
     * Returns the amount of time this request was in queque
     *
     * @return {@link Integer} with value of queued time in milli sec
     */
    public long getQueueWaitTime() {
        long waitTime = 0;
        if (queueWaitTime_ > 0) {
            waitTime = System.currentTimeMillis() - queueWaitTime_;
        }
        return waitTime;
    }
    
    /**
     * <p>
     * Set the specified process wait lock for this request. This request will not be blocked from
     * Execution until the waiting process finishes     *
     * </p>
     *
     * @param lock {@link PROCESS_WAIT_LOCK} type of lock
     */
    public void addProcessWaitLock(PROCESS_WAIT_LOCK lock) {
        if (lock != null) {
            locks_.add(lock);
        }
    }
    
    /**
     * Unlock the specified lock from the request. Call this when the locked process finishes
     *
     * @param lock {@link PROCESS_WAIT_LOCK} type of lock
     */
    public void removeProcessWaitLock(PROCESS_WAIT_LOCK lock) {
        locks_.remove(lock);
    }

    public String printWaitLocks(){
        return Arrays.toString(locks_.toArray());
    }
    
    
    /**
     * Check if this request is waiting on any operation to finish before processing
     *
     * @return True if this request if any pre processing operation pending
     */
    public boolean isWaitingOnProcessToFinish() {
        return locks_.size() > 0;
    }
    
    /**
     * Called on UI thread just before executing a request. Do any final updates to the request here.
     * Also attaches any required URL query parameters based on the request type.
     */
    public void onPreExecute() {
        BranchLogger.v("onPreExecute " + this);
        if (this instanceof ServerRequestRegisterOpen || this instanceof ServerRequestLogEvent) {
            try {
                ReferringUrlUtility utility = new ReferringUrlUtility(prefHelper_);
                String externalIntentUri = prefHelper_.getExternalIntentUri();
                utility.parseReferringURL(externalIntentUri);

                if (prefHelper_.getConsumerProtectionAttributionLevel() == Defines.BranchAttributionLevel.FULL || !prefHelper_.isAttributionLevelInitialized()) {
                    JSONObject urlQueryParams = utility.getURLQueryParamsForRequest(this);

                    for (Iterator<String> it = urlQueryParams.keys(); it.hasNext(); ) {
                        String key = it.next();
                        this.params_.put(key, urlQueryParams.get(key));
                    }
                }

            } catch (Exception e) {
                BranchLogger.e("Caught exception in onPreExecute: " + e.getMessage() + " stacktrace " + BranchLogger.stackTraceToString(e));
            }
        }
    }

    protected void updateEnvironment(Context context, JSONObject post) {
        try {
            String environment = DeviceInfo.getInstance().isPackageInstalled() ? Defines.Jsonkey.NativeApp.getKey() : Defines.Jsonkey.InstantApp.getKey();
            if (getBranchRemoteAPIVersion() == BRANCH_API_VERSION.V2) {
                JSONObject userData = post.optJSONObject(Defines.Jsonkey.UserData.getKey());
                if (userData != null) {
                    userData.put(Defines.Jsonkey.Environment.getKey(), environment);
                }
            } else {
                post.put(Defines.Jsonkey.Environment.getKey(), environment);
            }
        } catch (Exception e) {
            BranchLogger.d(e.getMessage());
        }
    }

    /**
     * Returns the Branch API version
     *
     * @return {@link BRANCH_API_VERSION} specifying remote Branch API version
     */
    public BRANCH_API_VERSION getBranchRemoteAPIVersion() {
        return BRANCH_API_VERSION.V1;  // Default is v1
    }
    
    /**
     * Method to notify that this request is being executed when tracking is disabled.
     * Remove all PII data from the request added to the request
     *
     * @return {@code true} if the request needed to be executed in tracking disabled mode
     */
    protected boolean prepareExecuteWithoutTracking() {
        // Default return false. Return true for request need to be executed when tracking is disabled
        return false;
    }

    // needed for adding SD fields for certain request (i.e. initialization and events)
    boolean isInitializationOrEventRequest() {
        for (Defines.RequestPath item : initializationAndEventRoutes) {
            if (item.equals(requestPath_)) return true;
        }
        return false;
    }
}
