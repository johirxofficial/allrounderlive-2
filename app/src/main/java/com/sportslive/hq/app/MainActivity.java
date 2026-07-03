package com.sportslive.hq.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;
import com.sportslive.hq.app.adapters.CategoryAdapter;
import com.sportslive.hq.app.adapters.EventAdapter;
import com.sportslive.hq.app.models.AppData;
import com.sportslive.hq.app.models.Category;
import com.sportslive.hq.app.models.Event;
import com.sportslive.hq.app.network.ApiClient;
import com.sportslive.hq.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ImageView imgLogo;
    private TextView tvAppName;
    private ImageButton btnInfo;
    private RecyclerView rvCategories;
    private RecyclerView rvEvents;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;

    private AppData appData;
    private CategoryAdapter categoryAdapter;
    private EventAdapter eventAdapter;
    private final List<Event> filteredEvents = new ArrayList<>();
    private String activeCategoryId = Constants.CATEGORY_ALL_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupEventsRecycler();

        btnInfo.setOnClickListener(v -> showInfoBottomSheet());
        swipeRefresh.setOnRefreshListener(this::loadAppData);

        loadAppData();
    }

    private void bindViews() {
        imgLogo = findViewById(R.id.imgAppLogo);
        tvAppName = findViewById(R.id.tvAppName);
        btnInfo = findViewById(R.id.btnInfo);
        rvCategories = findViewById(R.id.rvCategories);
        rvEvents = findViewById(R.id.rvEvents);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        rvCategories.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
    }

    private void setupEventsRecycler() {
        rvEvents.setLayoutManager(new LinearLayoutManager(this));
        eventAdapter = new EventAdapter(filteredEvents, this::openPlayer);
        rvEvents.setAdapter(eventAdapter);
    }

    private void loadAppData() {
        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getApiService().getAppData(Constants.CONFIG_JSON_URL)
                .enqueue(new Callback<AppData>() {
                    @Override
                    public void onResponse(Call<AppData> call, Response<AppData> response) {
                        progressBar.setVisibility(View.GONE);
                        swipeRefresh.setRefreshing(false);

                        if (response.isSuccessful() && response.body() != null) {
                            appData = response.body();
                            bindAppProfile();
                            bindCategories();
                            applyCategoryFilter(activeCategoryId);
                        } else {
                            Toast.makeText(MainActivity.this,
                                    R.string.error_loading_data, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AppData> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(MainActivity.this,
                                R.string.error_network, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void bindAppProfile() {
        if (appData.appProfile == null) return;
        tvAppName.setText(appData.appProfile.appName);
        Glide.with(this)
                .load(appData.appProfile.logoUrl)
                .placeholder(R.drawable.circle_placeholder)
                .error(R.drawable.circle_placeholder)
                .into(imgLogo);
    }

    private void bindCategories() {
        if (appData.categories == null) return;

        // Ensure an "All Sports" style entry always sits first if not already present.
        List<Category> categories = new ArrayList<>(appData.categories);

        categoryAdapter = new CategoryAdapter(categories, (category, position) -> {
            activeCategoryId = category.id;
            applyCategoryFilter(activeCategoryId);
        });
        rvCategories.setAdapter(categoryAdapter);
    }

    private void applyCategoryFilter(String categoryId) {
        filteredEvents.clear();
        if (appData != null && appData.events != null) {
            for (Event event : appData.events) {
                if (Constants.CATEGORY_ALL_ID.equals(categoryId)
                        || categoryId.equals(event.categoryId)) {
                    filteredEvents.add(event);
                }
            }
        }
        eventAdapter.notifyDataSetChanged();
    }

    private void openPlayer(Event event) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(Constants.EXTRA_EVENT_JSON, new Gson().toJson(event));
        if (appData != null && appData.ads != null) {
            intent.putExtra(Constants.EXTRA_ADS_JSON, new Gson().toJson(appData.ads));
        }
        startActivity(intent);
    }

    private void showInfoBottomSheet() {
        if (appData == null || appData.appProfile == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this)
                .inflate(R.layout.bottomsheet_info, null);
        dialog.setContentView(sheetView);

        ImageView imgSheetLogo = sheetView.findViewById(R.id.imgSheetLogo);
        TextView tvSheetAppName = sheetView.findViewById(R.id.tvSheetAppName);
        TextView tvSheetVersion = sheetView.findViewById(R.id.tvSheetVersion);
        TextView tvSheetDeveloper = sheetView.findViewById(R.id.tvSheetDeveloper);
        TextView tvSheetAbout = sheetView.findViewById(R.id.tvSheetAbout);
        ImageButton btnFacebook = sheetView.findViewById(R.id.btnFacebook);
        ImageButton btnTelegram = sheetView.findViewById(R.id.btnTelegram);
        ImageButton btnYoutube = sheetView.findViewById(R.id.btnYoutube);

        Glide.with(this)
                .load(appData.appProfile.logoUrl)
                .placeholder(R.drawable.circle_placeholder)
                .error(R.drawable.circle_placeholder)
                .into(imgSheetLogo);

        tvSheetAppName.setText(appData.appProfile.appName);
        tvSheetVersion.setText(getString(R.string.version_format, appData.appProfile.currentVersion));
        tvSheetDeveloper.setText(getString(R.string.developer_format, appData.appProfile.developerName));
        tvSheetAbout.setText(appData.appProfile.aboutUs);

        if (appData.appProfile.socialLinks != null) {
            btnFacebook.setOnClickListener(v ->
                    openUrl(appData.appProfile.socialLinks.facebook));
            btnTelegram.setOnClickListener(v ->
                    openUrl(appData.appProfile.socialLinks.telegram));
            btnYoutube.setOnClickListener(v ->
                    openUrl(appData.appProfile.socialLinks.youtube));
        }

        dialog.show();
    }

    private void openUrl(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_open_link, Toast.LENGTH_SHORT).show();
        }
    }
}
