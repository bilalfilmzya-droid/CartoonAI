package com.cartoonai.app;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cartoonai.app.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private Bitmap originalBitmap;
    private Bitmap processedBitmap;
    private String currentFilter = "";

    // ── Image Picker ──────────────────────────────────────────────────────────
    private final ActivityResultLauncher<String> pickImage =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            try {
                InputStream stream = getContentResolver().openInputStream(uri);
                originalBitmap = BitmapFactory.decodeStream(stream);
                originalBitmap = scaleBitmap(originalBitmap, 1200);
                processedBitmap = null;
                currentFilter = "";
                binding.imagePreview.setImageBitmap(originalBitmap);
                binding.layoutEditor.setVisibility(View.VISIBLE);
                binding.layoutUpload.setVisibility(View.GONE);
                binding.btnSave.setVisibility(View.GONE);
                binding.btnShare.setVisibility(View.GONE);
                clearFilterSelection();
                showToast("✅ Photo load ho gayi!");
            } catch (IOException e) {
                showToast("❌ Photo load karne mein masla hua");
            }
        });

    // ── Permission Launcher ───────────────────────────────────────────────────
    private final ActivityResultLauncher<String> requestPermission =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) saveToGallery();
            else showToast("Permission chahiye gallery mein save karne ke liye");
        });

    // ── onCreate ──────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnPickImage.setOnClickListener(v -> pickImage.launch("image/*"));
        binding.btnNewPhoto.setOnClickListener(v -> resetApp());
        binding.btnSave.setOnClickListener(v -> checkAndSave());
        binding.btnShare.setOnClickListener(v -> shareImage());

        // Filter buttons
        binding.btnComic.setOnClickListener(v -> applyFilter("comic"));
        binding.btnAnime.setOnClickListener(v -> applyFilter("anime"));
        binding.btnSketch.setOnClickListener(v -> applyFilter("sketch"));
        binding.btnWatercolor.setOnClickListener(v -> applyFilter("watercolor"));
        binding.btnNeon.setOnClickListener(v -> applyFilter("neon"));
        binding.btnRetro.setOnClickListener(v -> applyFilter("retro"));
    }

    // ── Filter Application ────────────────────────────────────────────────────
    private void applyFilter(String filterId) {
        if (originalBitmap == null) return;
        currentFilter = filterId;
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.imagePreview.setAlpha(0.5f);
        highlightFilter(filterId);

        new Thread(() -> {
            Bitmap result = applyFilterToBitmap(originalBitmap.copy(Bitmap.Config.ARGB_8888, true), filterId);
            runOnUiThread(() -> {
                processedBitmap = result;
                binding.imagePreview.setImageBitmap(processedBitmap);
                binding.imagePreview.setAlpha(1.0f);
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSave.setVisibility(View.VISIBLE);
                binding.btnShare.setVisibility(View.VISIBLE);
                showToast("🎨 " + getFilterName(filterId) + " filter lag gaya!");
            });
        }).start();
    }

    private Bitmap applyFilterToBitmap(Bitmap bmp, String filterId) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);

        switch (filterId) {
            case "comic":    applyComic(pixels, w, h);      break;
            case "anime":    applyAnime(pixels, w, h);       break;
            case "sketch":   applySketch(pixels, w, h);      break;
            case "watercolor": applyWatercolor(pixels, w, h); break;
            case "neon":     applyNeon(pixels, w, h);        break;
            case "retro":    applyRetro(pixels, w, h);       break;
        }

        bmp.setPixels(pixels, 0, w, 0, 0, w, h);
        return bmp;
    }

    // ── Comic: Posterize + Edge ───────────────────────────────────────────────
    private void applyComic(int[] pixels, int w, int h) {
        // Step 1: Posterize & saturate
        for (int i = 0; i < pixels.length; i++) {
            int r = Color.red(pixels[i]);
            int g = Color.green(pixels[i]);
            int b = Color.blue(pixels[i]);
            // Posterize to 4 levels
            r = (int)(Math.round(r / 63.75) * 63.75);
            g = (int)(Math.round(g / 63.75) * 63.75);
            b = (int)(Math.round(b / 63.75) * 63.75);
            // Boost saturation
            int avg = (r + g + b) / 3;
            r = clamp((int)(avg + (r - avg) * 1.8));
            g = clamp((int)(avg + (g - avg) * 1.8));
            b = clamp((int)(avg + (b - avg) * 1.8));
            pixels[i] = Color.rgb(r, g, b);
        }
        // Step 2: Edge detection overlay
        int[] copy = pixels.clone();
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                int gr = gray(copy[idx - w - 1]), gc = gray(copy[idx - w]), ge = gray(copy[idx - w + 1]);
                int ml = gray(copy[idx - 1]),                               mr = gray(copy[idx + 1]);
                int bl = gray(copy[idx + w - 1]), bc = gray(copy[idx + w]), br = gray(copy[idx + w + 1]);
                int gx = -gr - 2*ml - bl + ge + 2*mr + br;
                int gy = -gr - 2*gc - ge + bl + 2*bc + br;
                int mag = clamp((int)(Math.sqrt(gx*gx + gy*gy) * 1.2));
                if (mag > 40) {
                    int or_ = Color.red(pixels[idx]);
                    int og = Color.green(pixels[idx]);
                    int ob = Color.blue(pixels[idx]);
                    float edge = mag / 255f * 0.9f;
                    pixels[idx] = Color.rgb(
                        clamp((int)(or_ * (1 - edge))),
                        clamp((int)(og * (1 - edge))),
                        clamp((int)(ob * (1 - edge)))
                    );
                }
            }
        }
    }

    // ── Anime: Smooth + Bright + Edge ────────────────────────────────────────
    private void applyAnime(int[] pixels, int w, int h) {
        // Box blur first (smooth skin)
        int[] blurred = boxBlur(pixels, w, h, 1);
        // Posterize to 6 levels + high saturation
        for (int i = 0; i < blurred.length; i++) {
            int r = Color.red(blurred[i]);
            int g = Color.green(blurred[i]);
            int b = Color.blue(blurred[i]);
            r = (int)(Math.round(r / 42.5) * 42.5);
            g = (int)(Math.round(g / 42.5) * 42.5);
            b = (int)(Math.round(b / 42.5) * 42.5);
            int avg = (r + g + b) / 3;
            r = clamp((int)(avg + (r - avg) * 2.0));
            g = clamp((int)(avg + (g - avg) * 2.0));
            b = clamp((int)(avg + (b - avg) * 2.0));
            // Slight brightness boost
            r = clamp((int)(r * 1.1));
            g = clamp((int)(g * 1.1));
            b = clamp((int)(b * 1.15));
            blurred[i] = Color.rgb(r, g, b);
        }
        System.arraycopy(blurred, 0, pixels, 0, pixels.length);
        // Thin dark outlines
        int[] copy = pixels.clone();
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                int gx = gray(copy[idx-w-1]) * -1 + gray(copy[idx-w+1]) + gray(copy[idx-1]) * -2 + gray(copy[idx+1]) * 2 + gray(copy[idx+w-1]) * -1 + gray(copy[idx+w+1]);
                int gy = gray(copy[idx-w-1]) * -1 + gray(copy[idx-w]) * -2 + gray(copy[idx-w+1]) * -1 + gray(copy[idx+w-1]) + gray(copy[idx+w]) * 2 + gray(copy[idx+w+1]);
                int mag = clamp((int)Math.sqrt(gx*gx + gy*gy));
                if (mag > 60) pixels[idx] = Color.rgb(0, 0, 30);
            }
        }
    }

    // ── Sketch: Grayscale Edge Detection ─────────────────────────────────────
    private void applySketch(int[] pixels, int w, int h) {
        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) gray[i] = this.gray(pixels[i]);

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                int gx = -gray[idx-w-1] + gray[idx-w+1] - 2*gray[idx-1] + 2*gray[idx+1] - gray[idx+w-1] + gray[idx+w+1];
                int gy = -gray[idx-w-1] - 2*gray[idx-w] - gray[idx-w+1] + gray[idx+w-1] + 2*gray[idx+w] + gray[idx+w+1];
                int mag = clamp((int)(Math.sqrt(gx*gx + gy*gy) * 1.5));
                int val = clamp(255 - mag);
                // Slight warm tint
                pixels[idx] = Color.rgb(val, val, clamp((int)(val * 0.9)));
            }
        }
    }

    // ── Watercolor: Blur + Saturate ───────────────────────────────────────────
    private void applyWatercolor(int[] pixels, int w, int h) {
        int[] blurred = boxBlur(pixels, w, h, 2);
        for (int i = 0; i < blurred.length; i++) {
            int r = Color.red(blurred[i]);
            int g = Color.green(blurred[i]);
            int b = Color.blue(blurred[i]);
            int avg = (r + g + b) / 3;
            r = clamp((int)(avg + (r - avg) * 1.6));
            g = clamp((int)(avg + (g - avg) * 1.4));
            b = clamp((int)(avg + (b - avg) * 1.3));
            blurred[i] = Color.rgb(r, g, b);
        }
        System.arraycopy(blurred, 0, pixels, 0, pixels.length);
    }

    // ── Neon: Posterize + Vivid Color Boost ──────────────────────────────────
    private void applyNeon(int[] pixels, int w, int h) {
        for (int i = 0; i < pixels.length; i++) {
            int r = Color.red(pixels[i]);
            int g = Color.green(pixels[i]);
            int b = Color.blue(pixels[i]);
            // Posterize 3 levels
            r = (int)(Math.round(r / 85.0) * 85);
            g = (int)(Math.round(g / 85.0) * 85);
            b = (int)(Math.round(b / 85.0) * 85);
            // Find dominant channel and amplify
            int max = Math.max(r, Math.max(g, b));
            if (max == r) { r = clamp((int)(r * 1.8)); g = clamp((int)(g * 0.2)); b = clamp((int)(b * 1.5)); }
            else if (max == g) { r = clamp((int)(r * 0.2)); g = clamp((int)(g * 1.8)); b = clamp((int)(b * 0.5)); }
            else { r = clamp((int)(r * 0.3)); g = clamp((int)(g * 0.2)); b = clamp((int)(b * 2.0)); }
            pixels[i] = Color.rgb(r, g, b);
        }
    }

    // ── Retro: Limited Palette ────────────────────────────────────────────────
    private void applyRetro(int[] pixels, int w, int h) {
        int[][] palette = {
            {255, 80, 40}, {255, 200, 0}, {0, 160, 255},
            {80, 255, 120}, {255, 80, 200}, {255, 255, 255}, {20, 20, 40}, {255, 140, 0}
        };
        for (int i = 0; i < pixels.length; i++) {
            int r = Color.red(pixels[i]);
            int g = Color.green(pixels[i]);
            int b = Color.blue(pixels[i]);
            double minDist = Double.MAX_VALUE;
            int[] best = palette[0];
            for (int[] p : palette) {
                double dist = Math.sqrt(Math.pow(r-p[0],2)+Math.pow(g-p[1],2)+Math.pow(b-p[2],2));
                if (dist < minDist) { minDist = dist; best = p; }
            }
            pixels[i] = Color.rgb(best[0], best[1], best[2]);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private int gray(int pixel) {
        return (int)(0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel));
    }

    private int clamp(int val) { return Math.max(0, Math.min(255, val)); }

    private int[] boxBlur(int[] pixels, int w, int h, int radius) {
        int[] out = new int[pixels.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r=0,g=0,b=0,count=0;
                for (int dy=-radius; dy<=radius; dy++) {
                    for (int dx=-radius; dx<=radius; dx++) {
                        int ny = Math.max(0, Math.min(h-1, y+dy));
                        int nx = Math.max(0, Math.min(w-1, x+dx));
                        int p = pixels[ny*w+nx];
                        r+=Color.red(p); g+=Color.green(p); b+=Color.blue(p); count++;
                    }
                }
                out[y*w+x] = Color.rgb(r/count, g/count, b/count);
            }
        }
        return out;
    }

    private Bitmap scaleBitmap(Bitmap bmp, int maxSize) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        if (w <= maxSize && h <= maxSize) return bmp;
        float ratio = Math.min((float)maxSize/w, (float)maxSize/h);
        return Bitmap.createScaledBitmap(bmp, (int)(w*ratio), (int)(h*ratio), true);
    }

    // ── Save / Share ──────────────────────────────────────────────────────────
    private void checkAndSave() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToGallery();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                saveToGallery();
            } else {
                requestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    private void saveToGallery() {
        if (processedBitmap == null) return;
        try {
            String filename = "CartoonAI_" + System.currentTimeMillis() + ".jpg";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CartoonAI");
            }
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream out = getContentResolver().openOutputStream(uri);
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                if (out != null) out.close();
                showToast("✅ Gallery mein save ho gayi!");
            }
        } catch (Exception e) {
            showToast("❌ Save karne mein masla: " + e.getMessage());
        }
    }

    private void shareImage() {
        if (processedBitmap == null) return;
        try {
            String filename = "cartoon_" + System.currentTimeMillis() + ".jpg";
            java.io.File file = new java.io.File(getCacheDir(), filename);
            OutputStream out = new java.io.FileOutputStream(file);
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            out.close();
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/jpeg");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share CartoonAI Image"));
        } catch (Exception e) {
            showToast("❌ Share karne mein masla hua");
        }
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────
    private void highlightFilter(String filterId) {
        int active = getColor(R.color.filter_active);
        int inactive = getColor(R.color.filter_inactive);
        binding.btnComic.setBackgroundColor(filterId.equals("comic") ? active : inactive);
        binding.btnAnime.setBackgroundColor(filterId.equals("anime") ? active : inactive);
        binding.btnSketch.setBackgroundColor(filterId.equals("sketch") ? active : inactive);
        binding.btnWatercolor.setBackgroundColor(filterId.equals("watercolor") ? active : inactive);
        binding.btnNeon.setBackgroundColor(filterId.equals("neon") ? active : inactive);
        binding.btnRetro.setBackgroundColor(filterId.equals("retro") ? active : inactive);
    }

    private void clearFilterSelection() {
        int inactive = getColor(R.color.filter_inactive);
        binding.btnComic.setBackgroundColor(inactive);
        binding.btnAnime.setBackgroundColor(inactive);
        binding.btnSketch.setBackgroundColor(inactive);
        binding.btnWatercolor.setBackgroundColor(inactive);
        binding.btnNeon.setBackgroundColor(inactive);
        binding.btnRetro.setBackgroundColor(inactive);
    }

    private String getFilterName(String id) {
        switch(id) {
            case "comic": return "Comic Pop";
            case "anime": return "Anime";
            case "sketch": return "Sketch";
            case "watercolor": return "Watercolor";
            case "neon": return "Neon";
            case "retro": return "Retro";
            default: return id;
        }
    }

    private void resetApp() {
        originalBitmap = null;
        processedBitmap = null;
        currentFilter = "";
        binding.layoutEditor.setVisibility(View.GONE);
        binding.layoutUpload.setVisibility(View.VISIBLE);
        binding.btnSave.setVisibility(View.GONE);
        binding.btnShare.setVisibility(View.GONE);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
