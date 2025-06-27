package com.example.petroglyphcam;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.graphics.pdf.PdfDocument;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Color;
import java.io.FileOutputStream;
import java.io.IOException;

import com.example.petroglyphcam.DatabaseHelper;



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

        Button exportToPdfButton = findViewById(R.id.exportToPdfButton);
        exportToPdfButton.setOnClickListener(v -> exportToPdf());

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

    private void exportToPdf() {
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        Cursor cursor = dbHelper.getAllPetroglyphs();

        // Создаем PDF документ
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(12);

        int y = 50;
        int x = 50;

        // Заголовок с текущей датой
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        canvas.drawText("Petroglyphs Report (" + currentDate + ")", x, y, paint);
        y += 40;

        // Обрабатываем данные из Cursor
        if (cursor != null && cursor.moveToFirst()) {
            int recordCount = 1;
            SimpleDateFormat dbDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            do {
                // Разделитель между записями
                canvas.drawText("Record #" + (recordCount++), x, y, paint);
                y += 25;
                canvas.drawLine(x, y, pageInfo.getPageWidth() - x, y, paint);
                y += 20;

                // Получаем данные
                String imageUri = cursor.getString(cursor.getColumnIndexOrThrow("image_uri"));
                String description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
                String latitude = cursor.getString(cursor.getColumnIndexOrThrow("latitude"));
                String longitude = cursor.getString(cursor.getColumnIndexOrThrow("longitude"));
                String altitude = cursor.getString(cursor.getColumnIndexOrThrow("altitude"));
                String direction = cursor.getString(cursor.getColumnIndexOrThrow("direction"));
                String preservation = cursor.getString(cursor.getColumnIndexOrThrow("preservation"));
                String rockparam = cursor.getString(cursor.getColumnIndexOrThrow("rock_params"));
                long unixTime = cursor.getLong(cursor.getColumnIndexOrThrow("date_added"));
                String formattedDate = dbDateFormat.format(new Date(unixTime * 1000L));
                // Форматируем координаты
                String coordinates = String.format(Locale.US, "Lat: %.6f, Long: %.6f",
                        Double.parseDouble(latitude),
                        Double.parseDouble(longitude));

                // Добавляем данные в PDF
                addPdfText(canvas, paint, x, y, "Date formated:", formattedDate);
                y += 20;
                addPdfText(canvas, paint, x, y, "Image:", imageUri);
                y += 20;
                addPdfText(canvas, paint, x, y, "Description:", description);
                y += 20;
                addPdfText(canvas, paint, x, y, "Rock params:", rockparam);
                y += 20;
                addPdfText(canvas, paint, x, y, "Coordinates:", coordinates);
                y += 20;
                addPdfText(canvas, paint, x, y, "Direction:", direction);
                y += 20;
                addPdfText(canvas, paint, x, y, "Preservation:", preservation);
                y += 20;
                addPdfText(canvas, paint, x, y, "Altitude:", altitude + " m");
                y += 20;
                // Пытаемся добавить изображение

                try {
                    Bitmap originalBitmap = getBitmapFromUri(Uri.parse(imageUri));
                    if (originalBitmap != null) {
                        // Конвертируем hardware bitmap в software bitmap
                        Bitmap drawableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, false);
                        originalBitmap.recycle();

                        // Масштабирование
                        int maxWidth = 300;
                        float scale = (float) maxWidth / drawableBitmap.getWidth();
                        int scaledHeight = (int) (drawableBitmap.getHeight() * scale);
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                                drawableBitmap,
                                maxWidth,
                                scaledHeight,
                                true
                        );
                        drawableBitmap.recycle();

                        // Добавление в PDF
                        canvas.drawText("Image:", x, y, paint);
                        y += 20;
                        canvas.drawBitmap(scaledBitmap, x, y, paint);
                        y += scaledBitmap.getHeight() + 20;

                        scaledBitmap.recycle();
                    }
                } catch (Exception e) {
                    canvas.drawText("Image: [error: " + e.getMessage() + "]", x, y, paint);
                    y += 20;
                }
                y +=40;

                // Проверяем, не вышли ли за пределы страницы
                if (y > pageInfo.getPageHeight() - 100) {
                    document.finishPage(page);
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 50;
                }

            } while (cursor.moveToNext());
            cursor.close();
        } else {
            canvas.drawText("No data available", x, y, paint);
        }

        document.finishPage(page);

        // Сохраняем в папку Документы
        savePdfToDocuments(document, "PetroglyphsReport_" + currentDate.replace(" ", "_") + ".pdf");
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888; // Используем software-конфигурацию

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setMutableRequired(true);
            });
        } else {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            Bitmap bitmap = BitmapFactory.decodeFileDescriptor(
                    pfd.getFileDescriptor(),
                    null,
                    options
            );
            pfd.close();
            return bitmap;
        }
    }

    // Вспомогательный метод для добавления текста в PDF
    private void addPdfText(Canvas canvas, Paint paint, int x, int y, String label, String value) {
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(label, x, y, paint);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(value, x + 120, y, paint);
    }

    // Метод для сохранения PDF в папку Документы
    private void savePdfToDocuments(PdfDocument document, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Для Android 10+ (API 29+)
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);

            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);

            if (uri != null) {
                try (OutputStream stream = ((android.content.ContentResolver) resolver).openOutputStream(uri)) {
                    if (stream != null) {
                        document.writeTo(stream);
                        Toast.makeText(this, "PDF saved to Documents folder", Toast.LENGTH_LONG).show();
                    }
                } catch (IOException e) {
                    Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            // Для старых версий Android
            File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (!docsDir.exists()) {
                docsDir.mkdirs();
            }

            File file = new File(docsDir, fileName);
            try {
                document.writeTo(new FileOutputStream(file));
                Toast.makeText(this, "PDF saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        document.close();
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