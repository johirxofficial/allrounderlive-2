package com.sportslive.hq.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Root object matching the top level of the external JSON configuration file.
 */
public class AppData {

    @SerializedName("app_profile")
    public AppProfile appProfile;

    @SerializedName("categories")
    public List<Category> categories;

    @SerializedName("events")
    public List<Event> events;

    /** Optional. When present, PlayerActivity plays this as a skippable pre-roll ad. */
    @SerializedName("ads")
    public AdConfig ads;
}
