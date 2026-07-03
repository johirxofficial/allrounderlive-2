package com.sportslive.hq.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Event {

    @SerializedName("event_id")
    public String eventId;

    @SerializedName("category_id")
    public String categoryId;

    /** Expected values: "live" or "coming_soon" */
    public String status;

    public String title;
    public String description;

    /** ISO-8601 UTC timestamp, e.g. 2026-07-05T19:45:00Z */
    @SerializedName("start_time")
    public String startTime;

    @SerializedName("team_one")
    public Team teamOne;

    @SerializedName("team_two")
    public Team teamTwo;

    @SerializedName("demo_intro_url")
    public String demoIntroUrl;

    @SerializedName("streaming_sources")
    public List<StreamingSource> streamingSources;

    public boolean isLive() {
        return "live".equalsIgnoreCase(status);
    }

    public boolean isComingSoon() {
        return "coming_soon".equalsIgnoreCase(status);
    }
}
