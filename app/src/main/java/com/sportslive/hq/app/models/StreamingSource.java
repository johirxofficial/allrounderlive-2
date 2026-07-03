package com.sportslive.hq.app.models;

import com.google.gson.annotations.SerializedName;

public class StreamingSource {

    @SerializedName("server_name")
    public String serverName;

    /** Expected values: "hls" or "mpd" */
    public String type;

    public String url;

    @SerializedName("drm_protected")
    public boolean drmProtected;

    @SerializedName("drm_keys")
    public DrmKeys drmKeys;
}
