package io.branch.referral;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import io.branch.channels.RequestChannelKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

/**
 * <p>The Branch SDK can queue up requests whilst it is waiting for initialization of a session to
 * complete. This allows you to start sending requests to the Branch API as soon as your app is
 * opened.</p>
 */
public class ServerRequestQueue {
    private static final String PREF_KEY = "BNCServerRequestQueue";
    private static final int MAX_ITEMS = 25;
    private static ServerRequestQueue SharedInstance;
    private SharedPreferences sharedPref;
    private SharedPreferences.Editor editor;
    public final List<ServerRequest> queue;
    //Object for synchronising operations on server request queue
    private static final Object reqQueueLockObject = new Object();

    /**
     * <p>Singleton method to return the pre-initialised, or newly initialise and return, a singleton
     * object of the type {@link ServerRequestQueue}.</p>
     *
     * @param c A {@link Context} from which this call was made.
     * @return An initialised {@link ServerRequestQueue} object, either fetched from a
     * pre-initialised instance within the singleton class, or a newly instantiated
     * object where one was not already requested during the current app lifecycle.
     */
    public static ServerRequestQueue getInstance(Context c) {
        if (SharedInstance == null) {
            synchronized (ServerRequestQueue.class) {
                if (SharedInstance == null) {
                    SharedInstance = new ServerRequestQueue(c);
                }
            }
        }
        return SharedInstance;
    }

    // Package Private
    static void shutDown() {
        synchronized (reqQueueLockObject) {
            SharedInstance = null;
        }
    }
    
    /**
     * <p>The main constructor of the ServerRequestQueue class is private because the class uses the
     * Singleton pattern.</p>
     *
     * @param c A {@link Context} from which this call was made.
     */
    @SuppressLint("CommitPrefEdits")
    private ServerRequestQueue(Context c) {
        sharedPref = c.getSharedPreferences("BNC_Server_Request_Queue", Context.MODE_PRIVATE);
        editor = sharedPref.edit();
        queue = retrieve(c);
    }
    
    private void persist() {
        try {
            JSONArray jsonArr = new JSONArray();
            synchronized (reqQueueLockObject) {
                for (ServerRequest aQueue : queue) {
                    if (aQueue.isPersistable()) {
                        JSONObject json = aQueue.toJSON();
                        if (json != null) {
                            jsonArr.put(json);
                        }
                    }
                }
            }

            editor.putString(PREF_KEY, jsonArr.toString()).apply();
        } catch (Exception ex) {
            String msg = ex.getMessage();
            BranchLogger.v("Failed to persist queue" + (msg == null ? "" : msg));
        }
    }
    
    private List<ServerRequest> retrieve(Context context) {
        String jsonStr = sharedPref.getString(PREF_KEY, null);
        List<ServerRequest> result = Collections.synchronizedList(new LinkedList<ServerRequest>());
        synchronized (reqQueueLockObject) {
            if (jsonStr != null) {
                try {
                    JSONArray jsonArr = new JSONArray(jsonStr);
                    for (int i = 0, size = Math.min(jsonArr.length(), MAX_ITEMS); i < size; i++) {
                        JSONObject json = jsonArr.getJSONObject(i);
                        ServerRequest req = ServerRequest.fromJSON(json, context);
                        if (req != null) {
                            result.add(req);
                        }
                    }
                } catch (JSONException e) {
                    BranchLogger.d(e.getMessage());
                }
            }
        }
        return result;
    }
    
    /**
     * <p>Gets the number of {@link ServerRequest} objects currently queued up for submission to
     * the Branch API.</p>
     *
     * @return An {@link Integer} value indicating the current size of the {@link List} object
     * that forms the logical queue for the class.
     */
    public int getSize() {
        synchronized (reqQueueLockObject) {
            return queue.size();
        }
    }
    
    /**
     * <p>Adds a {@link ServerRequest} object to the queue.</p>
     *
     * @param request The {@link ServerRequest} object to add to the queue.
     */
    void enqueue(ServerRequest request) {
        BranchLogger.v("ServerRequestQueue enqueue " + request);
        synchronized (reqQueueLockObject) {
            if (request != null) {
                queue.add(request);
                if (getSize() >= MAX_ITEMS) {
                    queue.remove(1);
                }
                persist();
            }
        }
    }
    
    /**
     * <p>Gets the queued {@link ServerRequest} object at position with index 0 within the queue
     * without removing it.</p>
     *
     * @return The {@link ServerRequest} object at position with index 0 within the queue.
     */
    ServerRequest peek() {
        BranchLogger.v("peek " + Arrays.toString(queue.toArray()));
        ServerRequest req = null;
        synchronized (reqQueueLockObject) {
            try {
                req = queue.get(0);
            } catch (IndexOutOfBoundsException | NoSuchElementException e) {
                BranchLogger.d(e.getMessage());
            }
        }
        return req;
    }
    
    /**
     * <p>Gets the queued {@link ServerRequest} object at position with index specified in the supplied
     * parameter, within the queue. Like {@link #peek()}, the item is not removed from the queue.</p>
     *
     * @param index An {@link Integer} that specifies the position within the queue from which to
     *              pull the {@link ServerRequest} object.
     * @return The {@link ServerRequest} object at the specified index. Returns null if no
     * request exists at that position, or if the index supplied is not valid, for
     * instance if {@link #getSize()} is 6 and index 6 is called.
     */
    ServerRequest peekAt(int index) {
        ServerRequest req = null;
        synchronized (reqQueueLockObject) {
            try {
                req = queue.get(index);
                BranchLogger.v("ServerRequestQueue peakAt " + index + " returned " + req);
            } catch (IndexOutOfBoundsException | NoSuchElementException e) {
                BranchLogger.d(e.getMessage());
            }
        }
        return req;
    }
    
    /**
     * <p>As the method name implies, inserts a {@link ServerRequest} into the queue at the index
     * position specified.</p>
     *
     * @param request The {@link ServerRequest} to insert into the queue.
     * @param index   An {@link Integer} value specifying the index at which to insert the
     *                supplied {@link ServerRequest} object. Fails silently if the index
     *                supplied is invalid.
     */
    void insert(ServerRequest request, int index) {
        synchronized (reqQueueLockObject) {
            try {
                if (queue.size() < index) {
                    index = queue.size();
                }
                queue.add(index, request);
                persist();
            } catch (IndexOutOfBoundsException e) {
                BranchLogger.d(e.getMessage());
            }
        }
    }
    
    /**
     * <p>As the method name implies, removes the {@link ServerRequest} object, at the position
     * indicated by the {@link Integer} parameter supplied.</p>
     *
     * @param index An {@link Integer} value specifying the index at which to remove the
     *              {@link ServerRequest} object. Fails silently if the index
     *              supplied is invalid.
     * @return The {@link ServerRequest} object being removed.
     */
    @SuppressWarnings("unused")
    public ServerRequest removeAt(int index) {
        ServerRequest req = null;
        synchronized (reqQueueLockObject) {
            try {
                req = queue.remove(index);
                persist();
            } catch (IndexOutOfBoundsException e) {
                BranchLogger.d(e.getMessage());
            }
        }
        return req;
    }
    
    /**
     * <p>As the method name implies, removes {@link ServerRequest} supplied in the parameter if it
     * is present in the queue.</p>
     *
     * @param request The {@link ServerRequest} object to be removed from the queue.
     * @return A {@link Boolean} whose value is true if the object is removed.
     */
    public boolean remove(ServerRequest request) {
        boolean isRemoved = false;
        synchronized (reqQueueLockObject) {
            try {
                isRemoved = queue.remove(request);
                BranchLogger.v("ServerRequestQueue removed " + request + " " + isRemoved);
                persist();
            } catch (UnsupportedOperationException e) {
                BranchLogger.d(e.getMessage());
            }
        }
        return isRemoved;
    }
    
    /**
     * <p> Clears all pending requests in the queue </p>
     */
    void clear() {
        BranchLogger.v("ServerRequestQueue clear");
        synchronized (reqQueueLockObject) {
            try {
                queue.clear();
                persist();
            } catch (UnsupportedOperationException e) {
                BranchLogger.d(e.getMessage());
            }
        }
    }
    
    /**
     * <p>Determines whether the queue contains an install/register request.</p>
     *
     * @return A {@link Boolean} value indicating whether or not the queue contains an
     * install/register request. <i>True</i> if the queue contains a close request,
     * <i>False</i> if not.
     */
    ServerRequestInitSession getSelfInitRequest() {
        synchronized (reqQueueLockObject) {
            for (ServerRequest req : queue) {
                if (req instanceof ServerRequestInitSession) {
                    ServerRequestInitSession r = (ServerRequestInitSession) req;
                    if (r.initiatedByClient) {
                        return r;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Set Process wait lock to false for any open / install request in the queue
     */
    void unlockProcessWait(ServerRequest.PROCESS_WAIT_LOCK lock) {
        synchronized (reqQueueLockObject) {
            for (ServerRequest req : queue) {
                if (req != null) {
                    req.removeProcessWaitLock(lock);
                }
            }
        }
    }

    void insertRequestAtFront(ServerRequest req) {
        BranchLogger.v("ServerRequestQueue insertRequestAtFront " + req);
        this.insert(req, 0);
    }

    // Determine if a Request needs a Session to proceed.
    private boolean requestNeedsSession(ServerRequest request) {
        if (request instanceof ServerRequestInitSession) {
            return false;
        }
        else if (request instanceof ServerRequestCreateUrl) {
            return false;
        }

        // All other Request Types need a session.
        return true;
    }

    // Determine if a Session is available for a Request to proceed.
    //todo, check for this in handlenewrequest
    private boolean isSessionAvailableForRequest() {
        return (hasSession() && hasRandomizedDeviceToken());
    }

    private boolean hasSession() {
        return !Branch.getInstance().prefHelper_.getSessionID().equals(PrefHelper.NO_STRING_VALUE);
    }

    private boolean hasRandomizedDeviceToken() {
        return !Branch.getInstance().prefHelper_.getRandomizedDeviceToken().equals(PrefHelper.NO_STRING_VALUE);
    }

    boolean hasUser() {
        return !Branch.getInstance().prefHelper_.getRandomizedBundleToken().equals(PrefHelper.NO_STRING_VALUE);
    }

    void updateAllRequestsInQueue() {
        BranchLogger.v("ServerRequestQueue updateAllRequestsInQueue");
        try {
            for (int i = 0; i < this.getSize(); i++) {
                ServerRequest req = this.peekAt(i);
                if (req != null) {
                    JSONObject reqJson = req.getPost();
                    if (reqJson != null) {
                        if (reqJson.has(Defines.Jsonkey.SessionID.getKey())) {
                            req.getPost().put(Defines.Jsonkey.SessionID.getKey(), Branch.getInstance().prefHelper_.getSessionID());
                        }
                        if (reqJson.has(Defines.Jsonkey.RandomizedBundleToken.getKey())) {
                            req.getPost().put(Defines.Jsonkey.RandomizedBundleToken.getKey(), Branch.getInstance().prefHelper_.getRandomizedBundleToken());
                        }
                        if (reqJson.has(Defines.Jsonkey.RandomizedDeviceToken.getKey())) {
                            req.getPost().put(Defines.Jsonkey.RandomizedDeviceToken.getKey(), Branch.getInstance().prefHelper_.getRandomizedDeviceToken());
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles execution of a new request other than open or install.
     * Checks for the session initialisation and adds a install/Open request in front of this request
     * if the request need session to execute.
     *
     * @param req The {@link ServerRequest} to execute
     */
    public void handleNewRequest(ServerRequest req) {
        BranchLogger.d("handleNewRequest " + req);
        // If Tracking is disabled fail all messages with ERR_BRANCH_TRACKING_DISABLED
        if (Branch.getInstance().getTrackingController().isTrackingDisabled() && !req.prepareExecuteWithoutTracking()) {
            BranchLogger.d("Requested operation cannot be completed since tracking is disabled [" + req.requestPath_.getPath() + "]");
            req.handleFailure(BranchError.ERR_BRANCH_TRACKING_DISABLED, "");
            return;
        }
        //If not initialised put an open or install request in front of this request(only if this needs session)
        if (Branch.getInstance().initState_ != Branch.SESSION_STATE.INITIALISED && !(req instanceof ServerRequestInitSession)) {
            if ((req instanceof ServerRequestLogout)) {
                req.handleFailure(BranchError.ERR_NO_SESSION, "");
                BranchLogger.d("Branch is not initialized, cannot logout");
                return;
            }
            if (requestNeedsSession(req)) {
                BranchLogger.d("handleNewRequest " + req + " needs a session");
                req.addProcessWaitLock(ServerRequest.PROCESS_WAIT_LOCK.SDK_INIT_WAIT_LOCK);
            }
        }

        enqueue(req);

        if(req instanceof ServerRequestInitSession){
            startSessionQueue();
        }
        else if(Branch.getInstance().initState_ == Branch.SESSION_STATE.INITIALISED){
            executeRequest(req);
        }
    }

    public void startSessionQueue(){
        BranchLogger.v("ServerRequestQueue startSessionQueue " + Arrays.toString(queue.toArray()));
        synchronized (reqQueueLockObject) {
            for (ServerRequest req : queue) {
                executeRequest(req);
            }
        }
    }

    private void executeRequest(ServerRequest req) {
        RequestChannelKt.execute(req, new Continuation<ServerResponse>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object o) {
                BranchLogger.v("handleNewRequest resumeWith " + o + " " + Thread.currentThread().getName());
                if (o != null && o instanceof ServerResponse) {
                    ServerResponse serverResponse = (ServerResponse) o;
                    ServerRequestQueue.this.onPostExecuteInner(req, serverResponse);
                }
                else {
                    BranchLogger.v("Expected ServerResponse, was " + o);
                }
            }
        });
    }

    public void onPostExecuteInner(ServerRequest serverRequest, ServerResponse serverResponse) {
        BranchLogger.v("ServerRequestQueue onPostExecuteInner " + serverRequest + " " + serverResponse);
        if (serverResponse == null) {
            serverRequest.handleFailure(BranchError.ERR_BRANCH_INVALID_REQUEST, "Null response.");
            return;
        }

        int status = serverResponse.getStatusCode();
        if (status == 200) {
            onRequestSuccess(serverRequest, serverResponse);
        }
        else {
            onRequestFailed(serverRequest, serverResponse, status);
        }
    }

    private void onRequestSuccess(ServerRequest serverRequest, ServerResponse serverResponse) {
        BranchLogger.v("ServerRequestQueue onRequestSuccess " + serverRequest + " " + serverResponse);
        // If the request succeeded
        @Nullable final JSONObject respJson = serverResponse.getObject();
        if (respJson == null) {
            serverRequest.handleFailure(500, "Null response json.");
        }

        if (serverRequest instanceof ServerRequestCreateUrl && respJson != null) {
            try {
                // cache the link
                BranchLinkData postBody = ((ServerRequestCreateUrl) serverRequest).getLinkPost();
                final String url = respJson.getString("url");
                Branch.getInstance().linkCache_.put(postBody, url);
            }
            catch (JSONException ex) {
                ex.printStackTrace();
            }
        }
        else if (serverRequest instanceof ServerRequestLogout) {
            //On Logout clear the link cache and all pending requests
            Branch.getInstance().linkCache_.clear();
            //todo close and make a new channel
            ServerRequestQueue.this.clear();
        }

        if (serverRequest instanceof ServerRequestInitSession || serverRequest instanceof ServerRequestIdentifyUserRequest) {
            // If this request changes a session update the session-id to queued requests.
            boolean updateRequestsInQueue = false;
            if (!Branch.getInstance().isTrackingDisabled() && respJson != null) {
                // Update PII data only if tracking is disabled
                try {
                    if (respJson.has(Defines.Jsonkey.SessionID.getKey())) {
                        Branch.getInstance().prefHelper_.setSessionID(respJson.getString(Defines.Jsonkey.SessionID.getKey()));
                        updateRequestsInQueue = true;
                    }
                    if (respJson.has(Defines.Jsonkey.RandomizedBundleToken.getKey())) {
                        String new_Randomized_Bundle_Token = respJson.getString(Defines.Jsonkey.RandomizedBundleToken.getKey());
                        if (!Branch.getInstance().prefHelper_.getRandomizedBundleToken().equals(new_Randomized_Bundle_Token)) {
                            //On setting a new Randomized Bundle Token clear the link cache
                            Branch.getInstance().linkCache_.clear();
                            Branch.getInstance().prefHelper_.setRandomizedBundleToken(new_Randomized_Bundle_Token);
                            updateRequestsInQueue = true;
                        }
                    }
                    if (respJson.has(Defines.Jsonkey.RandomizedDeviceToken.getKey())) {
                        Branch.getInstance().prefHelper_.setRandomizedDeviceToken(respJson.getString(Defines.Jsonkey.RandomizedDeviceToken.getKey()));
                        updateRequestsInQueue = true;
                    }
                    if (updateRequestsInQueue) {
                        updateAllRequestsInQueue();
                    }
                } catch (JSONException ex) {
                    ex.printStackTrace();
                }
            }

            if (serverRequest instanceof ServerRequestInitSession) {
                Branch.getInstance().setInitState(Branch.SESSION_STATE.INITIALISED);

                // Count down the latch holding getLatestReferringParamsSync
                if (Branch.getInstance().getLatestReferringParamsLatch != null) {
                    Branch.getInstance().getLatestReferringParamsLatch.countDown();
                }
                // Count down the latch holding getFirstReferringParamsSync
                if (Branch.getInstance().getFirstReferringParamsLatch != null) {
                    Branch.getInstance().getFirstReferringParamsLatch.countDown();
                }
            }
        }

        if (respJson != null) {
            serverRequest.onRequestSucceeded(serverResponse, Branch.getInstance());
            ServerRequestQueue.this.remove(serverRequest);
        } else if (serverRequest.shouldRetryOnFail()) {
            // already called handleFailure above
            serverRequest.clearCallbacks();
        } else {
            ServerRequestQueue.this.remove(serverRequest);
        }
    }

    void onRequestFailed(ServerRequest serverRequest, ServerResponse serverResponse, int status) {
        BranchLogger.v("ServerRequestQueue onRequestFailed " + serverRequest + " " + serverResponse);
        // If failed request is an initialisation request (but not in the intra-app linking scenario) then mark session as not initialised
        if (serverRequest instanceof ServerRequestInitSession && PrefHelper.NO_STRING_VALUE.equals(Branch.getInstance().prefHelper_.getSessionParams())) {
            Branch.getInstance().setInitState(Branch.SESSION_STATE.UNINITIALISED);
        }

        // On a bad request or in case of a conflict notify with call back and remove the request.
        if ((status == 400 || status == 409) && serverRequest instanceof ServerRequestCreateUrl) {
            ((ServerRequestCreateUrl) serverRequest).handleDuplicateURLError();
        }
        else {
            //On Network error or Branch is down fail all the pending requests in the queue except
            //for request which need to be replayed on failure.
            //ServerRequestQueue.this.networkCount_ = 0;
            serverRequest.handleFailure(status, serverResponse.getFailReason());
        }

        boolean unretryableErrorCode = (400 <= status && status <= 451) || status == BranchError.ERR_BRANCH_TRACKING_DISABLED;
        // If it has an un-retryable error code, or it should not retry on fail, or the current retry count exceeds the max
        // remove it from the queue
        if (unretryableErrorCode || !serverRequest.shouldRetryOnFail() || (serverRequest.currentRetryCount >= Branch.getInstance().prefHelper_.getNoConnectionRetryMax())) {
            Branch.getInstance().requestQueue_.remove(serverRequest);
        } else {
            // failure has already been handled
            // todo does it make sense to retry the request without a callback? (e.g. CPID, LATD)
            serverRequest.clearCallbacks();
        }
    }
}
