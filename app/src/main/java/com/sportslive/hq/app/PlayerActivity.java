package com.sportslive.hq.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;
import com.sportslive.hq.app.models.AdConfig;
import com.sportslive.hq.app.models.DrmKeys;
import com.sportslive.hq.app.models.Event;
import com.sportslive.hq.app.models.StreamingSource;
import com.sportslive.hq.app.utils.Constants;
import com.sportslive.hq.app.utils.DateUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";

    // Some IPTV/streaming panels reject the default ExoPlayer user agent.
    private static final String USER_AGENT =
            "Mozilla/5.0 (SmartTV; Linux; Android 9) SportsLiveHQ/1.0.4";

    // Views
    private PlayerView playerView;
    private ProgressBar bufferingSpinner;
    private LinearLayout topOverlayBar;
    private ImageButton btnBack;
    private ImageButton btnServers;
    private TextView tvOverlayBadge;
    private TextView tvPlayerTitle;
    private TextView tvCountdown;
    private FrameLayout adOverlay;
    private TextView tvVisitSponsor;
    private TextView tvSkipAd;
    private LinearLayout errorOverlay;
    private TextView tvErrorMessage;
    private TextView btnRetry;
    private TextView btnChangeServer;

    private ExoPlayer exoPlayer;
    private DefaultHttpDataSource.Factory httpDataSourceFactory;

    private Event event;
    private AdConfig adConfig;
    private StreamingSource currentSource;
    private boolean isPlayingAd = false;

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;

    private final Handler adHandler = new Handler(Looper.getMainLooper());
    private Runnable adSkipRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        bindViews();
        applyFullScreen();
        parseIntentData();
        initPlayer();
        wireClicks();

        if (adConfig != null && adConfig.enabled
                && adConfig.videoUrl != null && !adConfig.videoUrl.trim().isEmpty()) {
            playAd();
        } else {
            startContentFlow();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyFullScreen();
    }

    private void applyFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void bindViews() {
        playerView = findViewById(R.id.playerView);
        bufferingSpinner = findViewById(R.id.bufferingSpinner);
        topOverlayBar = findViewById(R.id.topOverlayBar);
        btnBack = findViewById(R.id.btnBack);
        btnServers = findViewById(R.id.btnServers);
        tvOverlayBadge = findViewById(R.id.tvOverlayBadge);
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle);
        tvCountdown = findViewById(R.id.tvCountdown);
        adOverlay = findViewById(R.id.adOverlay);
        tvVisitSponsor = findViewById(R.id.tvVisitSponsor);
        tvSkipAd = findViewById(R.id.tvSkipAd);
        errorOverlay = findViewById(R.id.errorOverlay);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        btnRetry = findViewById(R.id.btnRetry);
        btnChangeServer = findViewById(R.id.btnChangeServer);
    }

    private void parseIntentData() {
        String eventJson = getIntent().getStringExtra(Constants.EXTRA_EVENT_JSON);
        if (eventJson == null) {
            finish();
            return;
        }
        event = new Gson().fromJson(eventJson, Event.class);
        tvPlayerTitle.setText(event.title);

        String adsJson = getIntent().getStringExtra(Constants.EXTRA_ADS_JSON);
        if (adsJson != null) {
            adConfig = new Gson().fromJson(adsJson, AdConfig.class);
        }
    }

    private void wireClicks() {
        btnBack.setOnClickListener(v -> finish());
        btnServers.setOnClickListener(v -> showServerBottomSheet());
        btnRetry.setOnClickListener(v -> {
            hideError();
            retryPlayback();
        });
        btnChangeServer.setOnClickListener(v -> {
            hideError();
            showServerBottomSheet();
        });
        tvSkipAd.setOnClickListener(v -> {
            if (tvSkipAd.isEnabled()) proceedFromAdToContent();
        });
        tvVisitSponsor.setOnClickListener(v -> {
            if (adConfig != null && adConfig.clickUrl != null && !adConfig.clickUrl.isEmpty()) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(adConfig.clickUrl)));
                } catch (Exception ignored) {
                    // no-op: don't crash the player over a bad sponsor link
                }
            }
        });
    }

    private void initPlayer() {
        httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);

        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(5000);
        playerView.setControllerHideOnTouch(true);
        playerView.showController();

        // Keep our custom overlay (back / title / servers) in sync with the
        // native controller's own show/hide timing (5s auto-hide, tap to show).
        playerView.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility ->
                        topOverlayBar.setVisibility(visibility));

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    bufferingSpinner.setVisibility(View.VISIBLE);
                } else {
                    bufferingSpinner.setVisibility(View.GONE);
                }
                if (playbackState == Player.STATE_ENDED && isPlayingAd) {
                    proceedFromAdToContent();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "Playback failed [" + error.getErrorCodeName() + "]: "
                        + error.getMessage(), error);
                showError(resolveFriendlyMessage(error) + "\n(" + error.getErrorCodeName() + ")");
            }
        });
    }

    // ---------------------------------------------------------------------
    // Ad pre-roll
    // ---------------------------------------------------------------------

    private void playAd() {
        isPlayingAd = true;
        adOverlay.setVisibility(View.VISIBLE);
        tvVisitSponsor.setVisibility(
                adConfig.clickUrl != null && !adConfig.clickUrl.isEmpty()
                        ? View.VISIBLE : View.GONE);

        int skipAfter = Math.max(adConfig.skipAfterSeconds, 0);
        tvSkipAd.setEnabled(false);
        tvSkipAd.setAlpha(0.6f);
        tvSkipAd.setText(getString(R.string.skip_ad_format, skipAfter));

        MediaItem mediaItem = new MediaItem.Builder().setUri(adConfig.videoUrl).build();
        DefaultDataSource.Factory dataSourceFactory =
                new DefaultDataSource.Factory(this, httpDataSourceFactory);
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem);

        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);

        startSkipCountdown(skipAfter);
    }

    private void startSkipCountdown(int seconds) {
        final int[] remaining = {seconds};
        adSkipRunnable = new Runnable() {
            @Override
            public void run() {
                if (remaining[0] <= 0) {
                    tvSkipAd.setText(R.string.skip_ad_ready);
                    tvSkipAd.setEnabled(true);
                    tvSkipAd.setAlpha(1f);
                    return;
                }
                tvSkipAd.setText(getString(R.string.skip_ad_format, remaining[0]));
                remaining[0]--;
                adHandler.postDelayed(this, 1000);
            }
        };
        adHandler.post(adSkipRunnable);
    }

    private void proceedFromAdToContent() {
        isPlayingAd = false;
        if (adSkipRunnable != null) adHandler.removeCallbacks(adSkipRunnable);
        adOverlay.setVisibility(View.GONE);
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
        startContentFlow();
    }

    // ---------------------------------------------------------------------
    // Live / Coming-soon content
    // ---------------------------------------------------------------------

    private void startContentFlow() {
        if (event.isLive()) {
            tvOverlayBadge.setText(R.string.badge_live);
            tvOverlayBadge.setBackgroundResource(R.drawable.bg_badge_live);
            tvCountdown.setVisibility(View.GONE);

            boolean hasSources = event.streamingSources != null && !event.streamingSources.isEmpty();
            btnServers.setVisibility(hasSources ? View.VISIBLE : View.GONE);

            if (hasSources) {
                playSource(event.streamingSources.get(0));
            } else {
                showError(getString(R.string.error_generic_friendly));
            }
        } else if (event.isComingSoon()) {
            tvOverlayBadge.setText(R.string.badge_coming_soon);
            tvOverlayBadge.setBackgroundResource(R.drawable.bg_badge_soon);
            btnServers.setVisibility(View.GONE);
            tvCountdown.setVisibility(View.VISIBLE);

            playComingSoonLoop();
            startCountdown();
        }
    }

    private void showServerBottomSheet() {
        if (event.streamingSources == null || event.streamingSources.isEmpty()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottomsheet_servers, null);
        dialog.setContentView(sheetView);

        LinearLayout container = sheetView.findViewById(R.id.serverListContainer);
        container.removeAllViews();

        for (StreamingSource source : event.streamingSources) {
            View row = getLayoutInflater().inflate(R.layout.item_server_row, container, false);
            TextView tvName = row.findViewById(R.id.tvServerRowName);
            TextView tvType = row.findViewById(R.id.tvServerRowType);
            ImageView imgActive = row.findViewById(R.id.imgServerActive);

            tvName.setText(source.serverName);
            tvType.setText(source.type != null ? source.type.toUpperCase(Locale.US) : "");
            imgActive.setVisibility(source == currentSource ? View.VISIBLE : View.INVISIBLE);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (source != currentSource) {
                    switchServer(source);
                }
            });
            container.addView(row);
        }
        dialog.show();
    }

    private void switchServer(StreamingSource source) {
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
        playSource(source);
    }

    private void retryPlayback() {
        if (isPlayingAd) {
            playAd();
        } else if (event.isComingSoon()) {
            playComingSoonLoop();
        } else if (currentSource != null) {
            playSource(currentSource);
        }
    }

    /**
     * Builds and prepares a MediaSource directly (HLS, DASH, or raw
     * MPEG-TS/progressive) so ClearKey playback can use a LocalMediaDrmCallback.
     */
    private void playSource(StreamingSource source) {
        if (source == null || source.url == null || source.url.trim().isEmpty()) {
            showError(getString(R.string.error_generic_friendly));
            return;
        }

        currentSource = source;
        hideError();

        Log.d(TAG, "Loading source: " + source.serverName + " -> " + source.url
                + " (type=" + source.type + ", drm=" + source.drmProtected + ")");

        MediaItem.Builder itemBuilder = new MediaItem.Builder().setUri(source.url);
        String mime = resolveMimeType(source.type);
        if (mime != null) itemBuilder.setMimeType(mime);

        DrmSessionManager drmSessionManager;
        if (source.drmProtected && source.drmKeys != null) {
            drmSessionManager = buildClearKeyDrmSessionManager(source.drmKeys);
            if (drmSessionManager == null) {
                showError(getString(R.string.error_drm_friendly));
                return;
            }
        } else {
            drmSessionManager = DrmSessionManager.DRM_UNSUPPORTED;
        }

        MediaSource mediaSource = buildMediaSource(source.type, itemBuilder.build(), drmSessionManager);

        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }

    private void playComingSoonLoop() {
        if (event.demoIntroUrl == null || event.demoIntroUrl.trim().isEmpty()) {
            showError(getString(R.string.error_generic_friendly));
            return;
        }

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(event.demoIntroUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build();

        DefaultDataSource.Factory dataSourceFactory =
                new DefaultDataSource.Factory(this, httpDataSourceFactory);
        MediaSource mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem);

        hideError();
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }

    /**
     * Builds the correct MediaSource for the given source type:
     *  - "mpd" -> DashMediaSource
     *  - "hls" -> HlsMediaSource
     *  - "ts" / anything else -> ProgressiveMediaSource (covers raw .ts,
     *    mpegts continuous streams, .mp4, etc. via ExoPlayer's built-in
     *    TsExtractor / DefaultExtractorsFactory auto-detection).
     */
    private MediaSource buildMediaSource(String type, MediaItem mediaItem,
                                          DrmSessionManager drmSessionManager) {
        DefaultDataSource.Factory dataSourceFactory =
                new DefaultDataSource.Factory(this, httpDataSourceFactory);
        String normalized = type == null ? "" : type.toLowerCase(Locale.US);

        switch (normalized) {
            case "mpd":
                return new DashMediaSource.Factory(dataSourceFactory)
                        .setDrmSessionManagerProvider(unused -> drmSessionManager)
                        .createMediaSource(mediaItem);
            case "hls":
                return new HlsMediaSource.Factory(dataSourceFactory)
                        .setDrmSessionManagerProvider(unused -> drmSessionManager)
                        .createMediaSource(mediaItem);
            case "ts":
            default:
                return new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .setDrmSessionManagerProvider(unused -> drmSessionManager)
                        .createMediaSource(mediaItem);
        }
    }

    private String resolveMimeType(String type) {
        if (type == null) return null;
        switch (type.toLowerCase(Locale.US)) {
            case "mpd":
                return MimeTypes.APPLICATION_MPD;
            case "hls":
                return MimeTypes.APPLICATION_M3U8;
            case "ts":
                return MimeTypes.VIDEO_MP2T;
            default:
                return null;
        }
    }

    /**
     * Builds a working ClearKey DrmSessionManager from a raw hex key_id/key pair
     * using LocalMediaDrmCallback, which hands the JWK Set response directly to
     * the platform CDM WITHOUT making any network request.
     */
    private DrmSessionManager buildClearKeyDrmSessionManager(DrmKeys drmKeys) {
        try {
            String base64UrlKeyId = hexToBase64Url(drmKeys.keyId);
            String base64UrlKey = hexToBase64Url(drmKeys.key);

            String jwkSetJson = "{\"keys\":[{\"kty\":\"oct\",\"kid\":\""
                    + base64UrlKeyId + "\",\"k\":\"" + base64UrlKey
                    + "\"}],\"type\":\"temporary\"}";

            LocalMediaDrmCallback localCallback =
                    new LocalMediaDrmCallback(jwkSetJson.getBytes(StandardCharsets.UTF_8));

            return new DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(localCallback);
        } catch (Exception e) {
            Log.e(TAG, "ClearKey DRM setup failed", e);
            return null;
        }
    }

    private String hexToBase64Url(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    // ---------------------------------------------------------------------
    // Error overlay
    // ---------------------------------------------------------------------

    private String resolveFriendlyMessage(PlaybackException error) {
        int bucket = error.errorCode / 1000;
        switch (bucket) {
            case 2: // ERROR_CODE_IO_* (network/host/timeout/HTTP)
                return getString(R.string.error_network_friendly);
            case 3: // ERROR_CODE_PARSING_* (bad/unsupported manifest or container)
                return getString(R.string.error_source_friendly);
            case 6: // ERROR_CODE_DRM_*
                return getString(R.string.error_drm_friendly);
            default:
                return getString(R.string.error_generic_friendly);
        }
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        errorOverlay.setVisibility(View.VISIBLE);
        bufferingSpinner.setVisibility(View.GONE);

        boolean canChangeServer = event != null && event.isLive()
                && event.streamingSources != null && event.streamingSources.size() > 1;
        btnChangeServer.setVisibility(canChangeServer ? View.VISIBLE : View.GONE);
    }

    private void hideError() {
        errorOverlay.setVisibility(View.GONE);
    }

    // ---------------------------------------------------------------------
    // Countdown (coming soon)
    // ---------------------------------------------------------------------

    private void startCountdown() {
        long targetMillis = DateUtils.parseIsoToMillis(event.startTime);

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (targetMillis <= 0) {
                    tvCountdown.setText(R.string.countdown_unavailable);
                    return;
                }
                long remaining = targetMillis - System.currentTimeMillis();
                if (remaining <= 0) {
                    tvCountdown.setText(R.string.countdown_starting_soon);
                    return;
                }
                tvCountdown.setText(getString(R.string.countdown_prefix,
                        DateUtils.formatCountdown(remaining)));
                countdownHandler.postDelayed(this, 1000);
            }
        };
        countdownHandler.post(countdownRunnable);
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    @Override
    protected void onStop() {
        super.onStop();
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countdownRunnable != null) countdownHandler.removeCallbacks(countdownRunnable);
        if (adSkipRunnable != null) adHandler.removeCallbacks(adSkipRunnable);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}
