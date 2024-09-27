package io.branch.referral.validators;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONObject;

public class DefaultDomainsCheck extends IntegrationValidatorCheck {

    String name = "Default Domains";
    String errorMessage = "Could not find any App Link hosts to support Android AppLinks. Please add intent filter for handling AppLinks in your Android Manifest file";
    String moreInfoLink = "<a href=\"https://help.branch.io/developers-hub/docs/android-basic-integration#4-configure-app:~:text=%3C!%2D%2D%20Branch%20App%20Links%20%2D%20Live,%3C/intent%2Dfilter%3E\">More info</a>";
    BranchIntegrationModel integrationModel;
    JSONObject branchAppConfig;

    public DefaultDomainsCheck(BranchIntegrationModel integrationModel, JSONObject branchAppConfig) {
        super.name = name;
        super.errorMessage = errorMessage;
        super.moreInfoLink = moreInfoLink;
        this.integrationModel = integrationModel;
        this.branchAppConfig = branchAppConfig;
    }

    @Override
    public boolean RunTests(Context context) {
        String defAppLinkDomain = branchAppConfig.optString("default_short_url_domain");
        return TextUtils.isEmpty(defAppLinkDomain) || checkIfIntentAddedForLinkDomain(defAppLinkDomain);
    }

    @Override
    public String GetOutput(Context context, boolean didTestSucceed) {
        didTestSucceed = RunTests(context);
        return super.GetOutput(context, didTestSucceed);
    }

    private boolean checkIfIntentAddedForLinkDomain(String domainName) {
        boolean foundIntentFilterMatchingDomainName = false;
        if (!TextUtils.isEmpty(domainName)) {
            for (String host : integrationModel.applinkScheme) {
                if (domainName.equals(host)) {
                    foundIntentFilterMatchingDomainName = true;
                    break;
                }
            }
        }
        return foundIntentFilterMatchingDomainName;
    }
}