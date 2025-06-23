package com.example.petroglyphcam;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Locale;

public class PreviewActivity extends AppCompatActivity {
    private static final String TAG = "PreviewActivity";

    private Uri imageUri;
    private boolean shouldDeleteOnCancel;
    private Location photoLocation;
    private float photoDirection;
    private EditText descriptionEditText;
    private RadioGroup preservationRadioGroup;
    private EditText rockParametersEditText;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        dbHelper = new DatabaseHelper(this);
        initViews();
        setupRadioGroup();
        processIntentData();
    }

    private void initViews() {
        ImageView imageView = findViewById(R.id.preview_image);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        Button closeButton = findViewById(R.id.close_button);
        Button saveButton = findViewById(R.id.save_button);
        TextView coordinatesText = findViewById(R.id.coordinates_text);
        TextView directionText = findViewById(R.id.direction_text);
        descriptionEditText = findViewById(R.id.description_edit_text);
        preservationRadioGroup = findViewById(R.id.preservation_radio_group);
        rockParametersEditText = findViewById(R.id.rock_parameters_edit_text);

        closeButton.setOnClickListener(v -> handleClose());
        saveButton.setOnClickListener(v -> saveImageWithMetadata());
    }

    private void setupRadioGroup() {
        preservationRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < group.getChildCount(); i++) {
                RadioButton button = (RadioButton) group.getChildAt(i);
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.radio_button_unselected));
                button.setTextColor(ContextCompat.getColor(this, R.color.radio_button_text_unselected));
            }

            RadioButton selectedButton = findViewById(checkedId);
            if (selectedButton != null) {
                selectedButton.setBackgroundColor(ContextCompat.getColor(this, R.color.radio_button_selected));
                selectedButton.setTextColor(ContextCompat.getColor(this, R.color.radio_button_text_selected));
            }
        });
    }

    private void processIntentData() {
        imageUri = getIntent().getData();
        shouldDeleteOnCancel = getIntent().getBooleanExtra("should_delete_on_cancel", true);
        photoLocation = getIntent().getParcelableExtra("location");
        photoDirection = getIntent().getFloatExtra("direction", 0f);

        if (imageUri == null) {
            Toast.makeText(this, "Ошибка: изображение не найдено", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        displayLocationInfo();
        loadImage(imageUri, (ImageView) findViewById(R.id.preview_image),
                (ProgressBar) findViewById(R.id.progressBar));
    }

    private void displayLocationInfo() {
        TextView coordinatesText = findViewById(R.id.coordinates_text);
        TextView directionText = findViewById(R.id.direction_text);

        if (photoLocation != null) {
            DecimalFormat df = new DecimalFormat("#.######");
            String coords = String.format(Locale.getDefault(),
                    "Широта: %s\nДолгота: %s\nВысота: %s м",
                    df.format(photoLocation.getLatitude()),
                    df.format(photoLocation.getLongitude()),
                    df.format(photoLocation.getAltitude()));

            coordinatesText.setText(coords);
            coordinatesText.setVisibility(View.VISIBLE);

            String directionStr = getDirectionString(photoDirection);
            directionText.setText(String.format("Направление: %s", directionStr));
            directionText.setVisibility(View.VISIBLE);
        }
    }

    private String getDirectionString(float degrees) {
        if (degrees < 0) return "Неизвестно";
        String[] directions = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
        int index = (int) ((degrees + 22.5) / 45) % 8;
        return directions[index] + " (" + (int)degrees + "°)";
    }

    private void loadImage(Uri imageUri, ImageView imageView, ProgressBar progressBar) {
        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
                if (inputStream == null) {
                    showError("Ошибка загрузки изображения");
                    return;
                }

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);

                runOnUiThread(() -> {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        progressBar.setVisibility(View.GONE);
                    } else {
                        showError("Ошибка декодирования изображения");
                    }
                });
            } catch (IOException e) {
                showError("Ошибка ввода-вывода");
            }
        }).start();
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void saveImageWithMetadata() {
        String description = descriptionEditText.getText().toString().trim();
        String preservation = getSelectedPreservation();
        String rockParameters = rockParametersEditText.getText().toString().trim();

        if (preservation.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, выберите сохранность", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            long id = dbHelper.addPetroglyph(
                    imageUri.toString(),
                    description,
                    preservation,
                    rockParameters,
                    photoLocation,
                    photoDirection
            );

            if (id != -1) {
                if (photoLocation != null) {
                    updateExifData(imageUri, photoLocation.getLatitude(), photoLocation.getLongitude());
                }
                Toast.makeText(this, "Данные сохранены", Toast.LENGTH_SHORT).show();
                shouldDeleteOnCancel = false;
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Ошибка сохранения", e);
        }
    }

    private String getSelectedPreservation() {
        int selectedId = preservationRadioGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.preservation_bad) return "Плохая";
        if (selectedId == R.id.preservation_medium) return "Средняя";
        if (selectedId == R.id.preservation_good) return "Хорошая";
        if (selectedId == R.id.preservation_excellent) return "Отличная";
        return "";
    }

    private void updateExifData(Uri imageUri, double latitude, double longitude) {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(imageUri, "rw")) {
            if (pfd == null) return;

            ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDegreeMinuteSeconds(latitude));
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latitude >= 0 ? "N" : "S");
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDegreeMinuteSeconds(longitude));
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, longitude >= 0 ? "E" : "W");
            exif.saveAttributes();
        } catch (IOException e) {
            Log.e(TAG, "Ошибка записи EXIF", e);
        }
    }

    private String convertToDegreeMinuteSeconds(double coordinate) {
        coordinate = Math.abs(coordinate);
        int degrees = (int) coordinate;
        double remaining = coordinate - degrees;
        int minutes = (int) (remaining * 60);
        double seconds = (remaining * 60 - minutes) * 60;
        return degrees + "/1," + minutes + "/1," + (int) (seconds * 1000) + "/1000";
    }

    private void handleClose() {
        if (shouldDeleteOnCancel) {
            showDeleteConfirmationDialog();
        } else {
            finish();
        }
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Удаление фото")
                .setMessage("Вы уверены, что хотите удалить это фото и данные?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    deleteImageAndData();
                    finish();
                })
                .setNegativeButton("Отмена", (dialog, which) -> finish())
                .show();
    }

    private void deleteImageAndData() {
        try {
            dbHelper.deletePetroglyph(imageUri.toString());
            getContentResolver().delete(imageUri, null, null);
            Toast.makeText(this, "Данные удалены", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка удаления", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Ошибка удаления", e);
        }
    }

    private String getRealPathFromUri(Uri contentUri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        try (Cursor cursor = getContentResolver().query(contentUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка получения пути", e);
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        dbHelper.close();
        super.onDestroy();
    }
}