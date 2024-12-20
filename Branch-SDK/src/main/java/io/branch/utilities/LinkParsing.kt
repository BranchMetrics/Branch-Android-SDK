package io.branch.utilities

import android.app.Activity
import android.net.Uri
import android.text.TextUtils
import io.branch.referral.BranchLogger.d
import io.branch.referral.BranchLogger.v
import io.branch.referral.Defines
import io.branch.referral.PrefHelper
import io.branch.referral.ServerRequestInitSession
import io.branch.utilities.UniversalResourceAnalyser.*
import org.json.JSONObject

/* List of keys whose values are collected from the Intent Extra.*/
val EXTERNAL_INTENT_EXTRA_KEY_WHITE_LIST = arrayOf(
    "extra_launch_uri",  // Key for embedded uri in FB ads triggered intents
    "branch_intent" // A boolean that specifies if this intent is originated by Branch
)

fun extractExternalUriAndIntentExtras(
    data: Uri,
    activity: Activity,
    prefHelper: PrefHelper,
    initRequest: ServerRequestInitSession
) {
    v("extractExternalUriAndIntentExtras data: $data activity: $activity")
    try {
        if (!isIntentParamsAlreadyConsumed(activity)) {
            val strippedUrl =
                getInstance(activity.applicationContext).getStrippedURL(data.toString())
            v("setExternalIntentUri: $strippedUrl")
            prefHelper.externalIntentUri = strippedUrl

            if (strippedUrl == data.toString()) {
                val bundle = activity.intent.extras
                val extraKeys = bundle!!.keySet()
                if (extraKeys.isEmpty()) {
                    return
                }

                val extrasJson = JSONObject()
                for (key in EXTERNAL_INTENT_EXTRA_KEY_WHITE_LIST) {
                    if (extraKeys.contains(key)) {
                        extrasJson.put(key, bundle[key])
                    }
                }
                if (extrasJson.length() > 0) {
                    prefHelper.externalIntentExtra = extrasJson.toString()
                }
            }
        }
    } catch (e: Exception) {
        d(e.message)
    }
}

fun isIntentParamsAlreadyConsumed(activity: Activity?): Boolean {
    val result = activity != null && activity.intent != null &&
            activity.intent.getBooleanExtra(Defines.IntentKeys.BranchLinkUsed.key, false)
    v("isIntentParamsAlreadyConsumed $result")

    return result
}


fun extractClickID(
    data: Uri?,
    activity: Activity,
    prefHelper: PrefHelper,
    initRequest: ServerRequestInitSession
): Boolean {
    v("extractClickID data: $data activity: $activity")
    try {
        if (data == null || !data.isHierarchical) {
            return false
        }

        val linkClickID = data.getQueryParameter(Defines.Jsonkey.LinkClickID.key) ?: return false

        prefHelper.linkClickIdentifier = linkClickID
        var paramString = "link_click_id=$linkClickID"
        val uriString = data.toString()

        paramString = if (paramString == data.query) {
            "\\?$paramString"
        } else if ((uriString.length - paramString.length) == uriString.indexOf(paramString)) {
            "&$paramString"
        } else {
            "$paramString&"
        }

        val uriWithoutClickID = Uri.parse(uriString.replaceFirst(paramString.toRegex(), ""))
        activity.intent.setData(uriWithoutClickID)
        activity.intent.putExtra(Defines.IntentKeys.BranchLinkUsed.key, true)
        return true
    } catch (e: java.lang.Exception) {
        d(e.message)
        return false
    }
}

fun extractBranchLinkFromIntentExtra(
    activity: Activity?,
    prefHelper: PrefHelper,
    initRequest: ServerRequestInitSession
): Boolean {
    v("extractBranchLinkFromIntentExtra $activity")
    //Check for any push identifier in case app is launched by a push notification
    try {
        if (activity != null && activity.intent != null && activity.intent.extras != null) {
            if (!isIntentParamsAlreadyConsumed(activity)) {
                val `object` = activity.intent.extras!![Defines.IntentKeys.BranchURI.key]
                var branchLink: String? = null

                if (`object` is String) {
                    branchLink = `object`
                } else if (`object` is Uri) {
                    branchLink = `object`.toString()
                }

                if (!TextUtils.isEmpty(branchLink)) {
                    prefHelper.pushIdentifier = branchLink
                    val thisIntent = activity.intent
                    thisIntent.putExtra(Defines.IntentKeys.BranchLinkUsed.key, true)
                    activity.intent = thisIntent
                    return true
                }
            }
        }
    } catch (e: java.lang.Exception) {
        d(e.message)
    }
    return false
}

fun extractAppLink(
    data: Uri?,
    activity: Activity?,
    prefHelper: PrefHelper,
    initRequest: ServerRequestInitSession
) {
    v("extractAppLink data: $data activity: $activity")
    if (data == null || activity == null) {
        return
    }

    val scheme = data.scheme
    val intent = activity.intent
    if (scheme != null && intent != null &&
        (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) &&
        !TextUtils.isEmpty(data.host) &&
        !isIntentParamsAlreadyConsumed(activity)
    ) {
        val strippedUrl = getInstance(activity.applicationContext).getStrippedURL(data.toString())

        if (data.toString().equals(strippedUrl, ignoreCase = true)) {
            // Send app links only if URL is not skipped.
            prefHelper.appLink = data.toString()
        }
        intent.putExtra(Defines.IntentKeys.BranchLinkUsed.key, true)
        activity.intent = intent
    }
}
