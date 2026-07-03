package com.sportslive.hq.app.models;

import com.google.gson.annotations.SerializedName;

public class AppProfile {

    @SerializedName("app_name")
    public String appName;

    @SerializedName("package_name")
    public String packageName;

    @SerializedName("current_version")
    public String currentVersion;

    @SerializedName("developer_name")
    public String developerName;

    @SerializedName("about_us")
    public String aboutUs;

    @SerializedName("logo_url")
    public String logoUrl;

    @SerializedName("social_links")
    public SocialLinks socialLinks;
}
