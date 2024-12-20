package io.branch.utilities;

import android.content.Context;
import android.net.TrafficStats;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import io.branch.referral.BranchAsyncTask;
import io.branch.referral.BranchLogger;
import io.branch.referral.PrefHelper;

/**
 * Created by sojanpr on 2/8/18.
 * <p>
 * Class for analysing the URLs or URIs for any sensitive data.
 * This class features differentiating URLs URIs based on the black listed or white listed formats.
 * </p>
 */

public class UniversalResourceAnalyser {
    private static JSONObject skipURLFormats;
    private final ArrayList<String> acceptURLFormats;
    private static final String SKIP_URL_FORMATS_KEY = "skip_url_format_key";
    private static final String VERSION_KEY = "version";
    private static final String SKIP_LIST_KEY = "uri_skip_list";
    // This is the path for updating skip url list. Check for the next version of the file
    private static final String UPDATE_URL_PATH = "%sdk/uriskiplist_v#.json";
    
    private final JSONObject DEFAULT_SKIP_URL_LIST;

    private static UniversalResourceAnalyser instance;
    
    
    public static UniversalResourceAnalyser getInstance(Context context) {
        if (instance == null) {
            instance = new UniversalResourceAnalyser(context);
        }
        return instance;
    }
    
    private UniversalResourceAnalyser(Context context) {
        DEFAULT_SKIP_URL_LIST = new JSONObject();
        try {
            DEFAULT_SKIP_URL_LIST.putOpt("version", 0);
            JSONArray skipURIArray = new JSONArray();
            DEFAULT_SKIP_URL_LIST.putOpt("uri_skip_list", skipURIArray);
            skipURIArray.put("^fb\\d+:((?!campaign_ids).)*$");
            skipURIArray.put("^li\\d+:");
            skipURIArray.put("^pdk\\d+:");
            skipURIArray.put("^twitterkit-.*:");
            skipURIArray.put("^com\\.googleusercontent\\.apps\\.\\d+-.*:\\/oauth");
            skipURIArray.put("^(?i)(?!(http|https):).*(:|:.*\\b)(password|o?auth|o?auth.?token|access|access.?token)\\b");
            skipURIArray.put("^(?i)((http|https):\\/\\/).*[\\/|?|#].*\\b(password|o?auth|o?auth.?token|access|access.?token)\\b");
        } catch (JSONException e) {
            BranchLogger.d(e.getMessage());
        }
        skipURLFormats = retrieveSkipURLFormats(context);
        acceptURLFormats = new ArrayList<>();
    }
    
    private JSONObject retrieveSkipURLFormats(Context context) {
        PrefHelper prefHelper = PrefHelper.getInstance(context);
        JSONObject urlFormat = new JSONObject();
        String latestUrlFormats = prefHelper.getString(SKIP_URL_FORMATS_KEY);
        if (TextUtils.isEmpty(latestUrlFormats) || PrefHelper.NO_STRING_VALUE.equals(latestUrlFormats)) {
            urlFormat = DEFAULT_SKIP_URL_LIST;
        } else {
            try {
                urlFormat = new JSONObject(latestUrlFormats);
            } catch (JSONException e) {
                BranchLogger.d(e.getMessage());
            }
        }
        return urlFormat;
    }
    
    public void addToSkipURLFormats(String skipURLFormat) {
        JSONArray skipURLArray = skipURLFormats.optJSONArray(SKIP_LIST_KEY);
        try {
            if (skipURLArray == null) {
                skipURLArray = new JSONArray();
                skipURLFormats.put(SKIP_LIST_KEY, skipURLArray);
            }
            skipURLArray.put(skipURLFormat);
        } catch (Exception e) {
            BranchLogger.d(e.getMessage());
        }
    }
    
    public void addToAcceptURLFormats(String acceptUrl) {
        acceptURLFormats.add(acceptUrl);
    }
    
    public void addToAcceptURLFormats(List<String> acceptUrls) {
        acceptURLFormats.addAll(acceptUrls);
    }
    
    public void checkAndUpdateSkipURLFormats(Context context) {
        try {
            new UrlSkipListUpdateTask(context).executeTask();
        } catch (Exception e) {
            BranchLogger.d(e.getMessage());
        }
    }
    
    public String getStrippedURL(String url) {
        String strippedURL = null;
        try {
            JSONArray skipURLArray = skipURLFormats.optJSONArray(SKIP_LIST_KEY);
            if (skipURLArray != null) {
                for (int i = 0; i < skipURLArray.length(); i++) {
                    try {
                        String skipPattern = skipURLArray.getString(i);
                        Pattern p = Pattern.compile(skipPattern);
                        Matcher m = p.matcher(url);
                        if (m.find()) {
                            strippedURL = skipPattern;
                            break;
                        }
                        
                    } catch (JSONException e) {
                        BranchLogger.d(e.getMessage());
                    }
                }
            }
            if (strippedURL == null) {
                if (acceptURLFormats.size() > 0) {
                    for (String skipPattern : acceptURLFormats) {
                        if (url.matches(skipPattern)) {
                            strippedURL = url;
                            break;
                        }
                    }
                } else {
                    strippedURL = url;
                }
            }
        } catch (Exception ex) {
            strippedURL = url;
        }
        return strippedURL;
    }
    
    private static class UrlSkipListUpdateTask extends BranchAsyncTask<Void, Void, JSONObject> {
        private final PrefHelper prefHelper;
        private final int TIME_OUT = 1500;
        
        private UrlSkipListUpdateTask(Context context) {
            this.prefHelper = PrefHelper.getInstance(context);
        }
        
        @Override
        protected JSONObject doInBackground(Void... params) {
            TrafficStats.setThreadStatsTag(0);
            JSONObject respObject = new JSONObject();
            HttpsURLConnection connection = null;
            try {
                String update_url_path = UPDATE_URL_PATH.replace("%", PrefHelper.getCDNBaseUrl());
                URL urlObject = new URL(update_url_path.replace("#", Integer.toString(skipURLFormats.optInt(VERSION_KEY) + 1)));
                connection = (HttpsURLConnection) urlObject.openConnection();
                connection.setConnectTimeout(TIME_OUT);
                connection.setReadTimeout(TIME_OUT);
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    if (connection.getInputStream() != null) {
                        BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        respObject = new JSONObject(rd.readLine());
                    }
                }
            } catch (Exception e) {
                BranchLogger.d(e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return respObject;
        }
        
        @Override
        protected void onPostExecute(JSONObject updatedURLFormatsObj) {
            super.onPostExecute(updatedURLFormatsObj);
            if (updatedURLFormatsObj.optInt(VERSION_KEY) > skipURLFormats.optInt(VERSION_KEY)) {
                skipURLFormats = updatedURLFormatsObj;
                prefHelper.setString(SKIP_URL_FORMATS_KEY, skipURLFormats.toString());
            }
        }
    }
    
}
