package io.branch.referral;

import android.content.Context;

import org.json.JSONObject;

/**
 * <p>
 * Class for creating builder for getting a short url with Branch. This builder provide an easy and flexible way to configure and create
 * a short url Synchronously or asynchronously.
 * </p>
 */
public class BranchShortLinkBuilder extends BranchUrlBuilder<BranchShortLinkBuilder> {
    public BranchShortLinkBuilder(Context context) {
        super(context);
    }

    /**
     * <p> Sets the alias for this link. </p>
     *
     * @param alias Link 'alias' can be used to label the endpoint on the link.
     *              <p>
     *              For example:
     *              http://bnc.lt/AUSTIN28.
     *              Should not exceed 128 characters
     *              </p>
     * @return This Builder object to allow for chaining of calls to set methods.
     */
    public BranchShortLinkBuilder setAlias(String alias) {
        this.alias_ = alias;
        return this;
    }

    /**
     * <p> Sets the channel for this link. </p>
     *
     * @param channel A {@link String} denoting the channel that the link belongs to. Should not
     *                exceed 128 characters.
     * @return This Builder object to allow for chaining of calls to set methods.
     */
    public BranchShortLinkBuilder setChannel(String channel) {
        this.channel_ = channel;
        return this;
    }

    /**
     * <p> Sets the amount of time that Branch allows a click to remain outstanding.</p>
     *
     * @param duration A {@link Integer} value specifying the time that Branch allows a click to
     *                 remain outstanding and be eligible to be matched with a new app session.
     * @return This Builder object to allow for chaining of calls to set methods.
     */
    public BranchShortLinkBuilder setDuration(int duration) {
        this.duration_ = duration;
        return this;
    }

    /**
     * <p> Set a name that identifies the feature that the link makes use of.</p>
     *
     * @param feature A {@link String} value identifying the feature that the link makes use of.
     *                Should not exceed 128 characters.
     * @return This Builder object to allow for chaining of calls to set methods.
     */
    public BranchShortLinkBuilder setFeature(String feature) {
        this.feature_ = feature;
        return this;
    }

    /**
     * <p> Set the parameters associated with the link.</p>
     *
     * @param parameters A {@link JSONObject} value containing the deep linked params associated with
     *                   the link that will be passed into a new app session when clicked.
     *                   {@see addParameters} if you want to set parameters as individual key value.
     * @return This Builder object to allow for chaining of calls to set methods.
     */
    public BranchShortLinkBuilder setParameters(JSONObject parameters) {
        this.params_ = parameters;
        return this;
    }

    /**
     * <p>Set a name that identify the stage in an application or user flow process.</p>
     *
     * @param stage A {@link String} value identifying the stage in an application or user flow
     *              process. Should not exceed 128 characters.
     * @return This Builder object to allow for chaining of calls to set methods.
     */
    public BranchShortLinkBuilder setStage(String stage) {
        this.stage_ = stage;
        return this;
    }

    /**
     * <p>Sets an {@link int} that can be used for scenarios where you want the link to
     * only deep link the first time or unlimited times</p>
     *
     * @param type {@link int} that can be used for scenarios where you want the link to
     *             only deep link the first time.Possible values is {@link Branch#LINK_TYPE_ONE_TIME_USE} or {@link Branch#LINK_TYPE_UNLIMITED_USE}
     * @return This Builder object to allow for chaining of calls to set methods.
     */
    public BranchShortLinkBuilder setType(int type) {
        this.type_ = type;
        return this;
    }

    /**
     * Sets the content identifier associated with link content     *
     *
     * @param contentId Content identifier associated with this link
     * @return This Builder object to allow for chaining of calls to set methods.
     */
    public BranchShortLinkBuilder setContentId(String contentId) {
        this.contentId_ = contentId;
        return this;
    }


    //---------------Generate URL methods -----------------------//

    /**
     * <p>Configures and requests a short URL to be generated by the Branch servers, via a synchronous
     * call; with a duration specified within which an app session should be matched to the link.</p>
     *
     * @return A {@link String} containing the resulting short URL. Null is returned in case of an error or if Branch is not initialised.
     */
    public String getShortUrl() {
        return super.getUrl();
    }

    /**
     * <p>Configures and requests a short URL to be generated by the Branch servers, via a asynchronous
     * call; The {@link Branch.BranchLinkCreateListener} is called back with the url when the url is generated.</p>
     *
     * @param callback A {@link Branch.BranchLinkCreateListener} callback instance that will trigger
     */
    public void generateShortUrl(Branch.BranchLinkCreateListener callback) {
        super.generateUrl(callback);
    }
}
