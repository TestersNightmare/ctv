package com.dex7er.ctvplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages full-screen photo carousel from local storage (DCIM, Pictures, Screenshots).
 * Supports smart grouping: wide images shown alone, portrait images paired side-by-side.
 */
public class CarouselManager {

    private static final long AUTO_ADVANCE_MS = 6000L; // 6 seconds per slide

    // ─────────────────────────── interfaces ──────────────────────────────

    public interface OnCarouselEventListener {
        void onCarouselStopped();
    }

    // ─────────────────────────── data classes ────────────────────────────

    public static class ImageItem {
        public final Uri uri;
        public final int width;
        public final int height;

        public ImageItem(Uri uri, int width, int height) {
            this.uri = uri;
            this.width = width > 0 ? width : 1;
            this.height = height > 0 ? height : 1;
        }

        /** width / height */
        public float aspectRatio() {
            return (float) width / height;
        }
    }

    public static class SlideGroup {
        public final List<Integer> indices = new ArrayList<>();
    }

    // ─────────────────────────── state ───────────────────────────────────

    private final Context context;
    private final List<ImageItem> images = new ArrayList<>();
    private final List<SlideGroup> groups = new ArrayList<>();
    private int currentGroupIndex = 0;

    private ViewGroup containerView;
    private FrameLayout overlayView;
    private LinearLayout imageRow;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autoAdvanceRunnable;
    private boolean running = false;
    private OnCarouselEventListener eventListener;

    public CarouselManager(Context context) {
        this.context = context;
    }

    public void setEventListener(OnCarouselEventListener l) {
        this.eventListener = l;
    }

    // ─────────────────────────── scanning ────────────────────────────────

    /**
     * Scans MediaStore for images in target buckets across all storage volumes
     * (internal, SD card, USB). Must be called off the main thread.
     */
    public void scanImages() {
        images.clear();
        Uri queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };
        // Target bucket names (case-insensitive partial match)
        String[] targetBuckets = {
                "dcim", "screenshots", "pictures", "camera", "相机", "截图", "图片", "screenshot", "photo"
        };

        ContentResolver cr = context.getContentResolver();
        try (Cursor cursor = cr.query(
                queryUri, projection, null, null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC")) {
            if (cursor == null) return;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int wCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH);
            int hCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT);
            int bCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);

            while (cursor.moveToNext()) {
                String bucket = cursor.getString(bCol);
                if (bucket == null) continue;
                String bucketLow = bucket.toLowerCase();
                boolean target = false;
                for (String t : targetBuckets) {
                    if (bucketLow.contains(t)) { target = true; break; }
                }
                if (!target) continue;

                long id = cursor.getLong(idCol);
                int w  = cursor.getInt(wCol);
                int h  = cursor.getInt(hCol);
                Uri uri = Uri.withAppendedPath(queryUri, String.valueOf(id));
                images.add(new ImageItem(uri, w, h));
            }
        } catch (Exception ignored) {}

        buildGroups();
    }

    /**
     * Groups images so that each slide fills ~100% of screen width.
     * Wide (landscape) images are shown alone; portrait images are paired
     * or tripled until the combined width covers the screen.
     */
    private void buildGroups() {
        groups.clear();
        if (images.isEmpty()) return;

        int sw = context.getResources().getDisplayMetrics().widthPixels;
        int sh = context.getResources().getDisplayMetrics().heightPixels;
        if (sh == 0) sh = 1;

        int pos = 0;
        while (pos < images.size()) {
            SlideGroup g = new SlideGroup();
            g.indices.add(pos);

            // Display width if this image fills screen height
            float displayW0 = images.get(pos).aspectRatio() * sh;

            if (displayW0 < sw * 0.85f) {
                // Portrait/narrow image – try to fill remaining width with next images
                float total = displayW0;
                int next = pos + 1;
                while (next < images.size() && g.indices.size() < 3) {
                    float wn = images.get(next).aspectRatio() * sh;
                    if (wn >= sw * 0.85f) break; // next is wide, don't merge
                    if (total + wn <= sw * 1.15f) {
                        g.indices.add(next);
                        total += wn;
                        next++;
                        if (total >= sw * 0.85f) break; // filled enough
                    } else {
                        break;
                    }
                }
            }

            groups.add(g);
            pos += g.indices.size();
        }
    }

    // ─────────────────────────── control ─────────────────────────────────

    /**
     * Start the carousel inside {@code container}, beginning from {@code startImageIndex}.
     * Must be called on the main thread. Images must already be scanned.
     */
    public void start(ViewGroup container, int startImageIndex) {
        if (images.isEmpty()) return;
        containerView = container;
        running = true;

        // Find the group that contains startImageIndex
        currentGroupIndex = 0;
        if (startImageIndex >= 0) {
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).indices.contains(startImageIndex)) {
                    currentGroupIndex = i;
                    break;
                }
            }
        }

        // Build overlay
        overlayView = new FrameLayout(context);
        overlayView.setBackgroundColor(Color.BLACK);
        overlayView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        imageRow = new LinearLayout(context);
        imageRow.setOrientation(LinearLayout.HORIZONTAL);
        imageRow.setGravity(Gravity.CENTER);
        imageRow.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlayView.addView(imageRow);

        container.addView(overlayView);
        showCurrentGroup();
        scheduleAdvance();
    }

    /** Stop the carousel and remove the overlay. */
    public void stop() {
        running = false;
        cancelAdvance();
        if (overlayView != null && containerView != null) {
            containerView.removeView(overlayView);
        }
        overlayView = null;
        imageRow = null;
        containerView = null;
        if (eventListener != null) eventListener.onCarouselStopped();
    }

    public boolean isRunning() { return running; }

    /** Advance to the next slide. */
    public void next() {
        if (groups.isEmpty()) return;
        currentGroupIndex = (currentGroupIndex + 1) % groups.size();
        showCurrentGroup();
        resetAdvance();
    }

    /** Go back to the previous slide. */
    public void prev() {
        if (groups.isEmpty()) return;
        currentGroupIndex = (currentGroupIndex - 1 + groups.size()) % groups.size();
        showCurrentGroup();
        resetAdvance();
    }

    /** Jump directly to the slide that contains the given image index. */
    public void showImageFromIndex(int imageIndex) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).indices.contains(imageIndex)) {
                currentGroupIndex = i;
                showCurrentGroup();
                resetAdvance();
                return;
            }
        }
    }

    public int getCurrentGroupIndex() { return currentGroupIndex; }
    public int getGroupCount()        { return groups.size(); }
    public List<ImageItem> getImages(){ return images; }

    // ─────────────────────────── rendering ───────────────────────────────

    private void showCurrentGroup() {
        if (imageRow == null || groups.isEmpty()) return;
        imageRow.removeAllViews();

        SlideGroup g = groups.get(currentGroupIndex);
        int sh = context.getResources().getDisplayMetrics().heightPixels;
        int sw = context.getResources().getDisplayMetrics().widthPixels;

        // Sum of aspect ratios for proportional width splitting
        float totalAr = 0f;
        for (int idx : g.indices) totalAr += images.get(idx).aspectRatio();
        if (totalAr <= 0) totalAr = 1f;

        for (int idx : g.indices) {
            ImageItem img = images.get(idx);
            float ar = img.aspectRatio();

            int dw;
            if (g.indices.size() == 1) {
                dw = (int) Math.min(ar * sh, sw);
            } else {
                dw = (int) (sw * ar / totalAr);
            }

            ImageView iv = new ImageView(context);
            iv.setLayoutParams(new LinearLayout.LayoutParams(dw, sh));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Glide.with(context).load(img.uri).into(iv);
            imageRow.addView(iv);
        }
    }

    // ─────────────────────────── auto-advance ────────────────────────────

    private void scheduleAdvance() {
        autoAdvanceRunnable = this::next;
        handler.postDelayed(autoAdvanceRunnable, AUTO_ADVANCE_MS);
    }

    private void cancelAdvance() {
        if (autoAdvanceRunnable != null) {
            handler.removeCallbacks(autoAdvanceRunnable);
            autoAdvanceRunnable = null;
        }
    }

    private void resetAdvance() {
        cancelAdvance();
        scheduleAdvance();
    }
}
