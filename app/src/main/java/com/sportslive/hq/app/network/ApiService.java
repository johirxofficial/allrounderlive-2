package com.sportslive.hq.app.network;

import com.sportslive.hq.app.models.AppData;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

public interface ApiService {

    /**
     * Fetches the full app configuration JSON from any absolute URL.
     * The URL is supplied at call time (see Constants.CONFIG_JSON_URL)
     * so the same client can point at any hosted JSON file.
     */
    @GET
    Call<AppData> getAppData(@Url String url);
}
