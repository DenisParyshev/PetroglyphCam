package com.example.petroglyphcam;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.SearchView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GalleryActivity extends AppCompatActivity {
    private static final String TAG = "GalleryActivity";
    private RecyclerView recyclerView;
    private GalleryAdapter adapter;
    private List<PetroglyphItem> originalItems = new ArrayList<>();
    private List<PetroglyphItem> filteredItems = new ArrayList<>();
    private DatabaseHelper dbHelper;
    private static final int STORAGE_PERMISSION_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        dbHelper = new DatabaseHelper(this);

        // Настройка поиска
        SearchView searchView = findViewById(R.id.searchView);
        searchView.setQueryHint(getString(R.string.search_hint));

        // Настройка RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GalleryAdapter(this, filteredItems);
        recyclerView.setAdapter(adapter);

        // Обработчик поиска
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterItems(newText);
                return true;
            }
        });

        if (checkStoragePermission()) {
            loadImagesFromDatabase();
        } else {
            requestStoragePermission();
        }
    }

    private void filterItems(String query) {
        adapter.getFilter().filter(query);
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                    STORAGE_PERMISSION_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadImagesFromDatabase();
            } else {
                Toast.makeText(this,
                        getString(R.string.storage_permission_required),
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void loadImagesFromDatabase() {
        new Thread(() -> {
            try (Cursor dbCursor = dbHelper.getAllPetroglyphs()) {
                List<PetroglyphItem> loadedItems = new ArrayList<>();

                if (dbCursor != null && dbCursor.moveToFirst()) {
                    do {
                        String imageUri = dbCursor.getString(dbCursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_IMAGE_URI));
                        String description = dbCursor.getString(dbCursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_DESCRIPTION));
                        double latitude = dbCursor.getDouble(dbCursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_LATITUDE));
                        double longitude = dbCursor.getDouble(dbCursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_LONGITUDE));
                        double altitude = dbCursor.getDouble(dbCursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_ALTITUDE));
                        String preservation = dbCursor.getString(dbCursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_PRESERVATION));
                        long dateAdded = dbCursor.getLong(dbCursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_DATE_ADDED));

                        String date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                                .format(new Date(dateAdded * 1000));
                        String coordinates = String.format(Locale.getDefault(),
                                "%.6f, %.6f", latitude, longitude);
                        String altitudeText = String.format(Locale.getDefault(),
                                "%.1f м", altitude);

                        loadedItems.add(new PetroglyphItem(
                                imageUri,
                                description,
                                coordinates,
                                altitudeText,
                                preservation,
                                date
                        ));
                    } while (dbCursor.moveToNext());

                    runOnUiThread(() -> {
                        originalItems.clear();
                        originalItems.addAll(loadedItems);
                        adapter.updateData(originalItems); // Новый метод в адаптере
                        adapter.getFilter().filter(""); // Сбрасываем фильтр
                    });
                } else {
                    runOnUiThread(() -> {
                        originalItems.clear();
                        filteredItems.clear();
                        adapter.notifyDataSetChanged();
                        Toast.makeText(this,
                                getString(R.string.no_saved_images),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки данных", e);
                runOnUiThread(() ->
                        Toast.makeText(this,
                                getString(R.string.load_error),
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        dbHelper.close();
        super.onDestroy();
    }
}