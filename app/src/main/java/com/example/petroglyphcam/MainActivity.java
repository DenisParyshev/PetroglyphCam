package com.example.petroglyphcam;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.FileDescriptor;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private static final int REQUEST_CODE_APP_SETTINGS = 101;
    private static final int REQUEST_CODE_PREVIEW = 102;
    private static final int REQUEST_CODE_GALLERY = 103;

    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private PreviewView previewView;
    private ImageButton captureButton;
    private Button galleryButton;
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private float[] lastAccelerometer = new float[3];
    private float[] lastMagnetometer = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] orientation = new float[3];
    private float currentDirection = 0f;
    private boolean lastAccelerometerSet = false;
    private boolean lastMagnetometerSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initLocationClient();
        initSensors();
        initCameraExecutor();
        checkPermissions();
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.captureButton);
        galleryButton = findViewById(R.id.galleryButton);

        captureButton.setOnClickListener(v -> takePhoto());
        galleryButton.setOnClickListener(v -> openGallery());
    }

    private void initLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    private void initCameraExecutor() {
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void openGallery() {
        if (checkStoragePermission()) {
            // Заменяем вызов системной галереи на нашу GalleryActivity
            Intent intent = new Intent(this, GalleryActivity.class);
            startActivity(intent);
        } else {
            requestStoragePermission();
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                    REQUEST_CODE_PERMISSIONS);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_PERMISSIONS);
        }
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        REQUEST_CODE_PERMISSIONS);
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_CODE_PERMISSIONS);
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerSensors();
    }

    private void registerSensors() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.length);
            lastAccelerometerSet = true;
        } else if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.length);
            lastMagnetometerSet = true;
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            updateDirection();
        }
    }

    private void updateDirection() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)) {
            SensorManager.getOrientation(rotationMatrix, orientation);
            currentDirection = (float) Math.toDegrees(orientation[0]);
            currentDirection = (currentDirection + 360) % 360;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        return permissions.toArray(new String[0]);
    }

    private void checkPermissions() {
        String[] requiredPermissions = getRequiredPermissions();
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            initCamera();
            requestLocation();
        } else {
            requestPermissions(permissionsToRequest);
        }
    }

    private void requestPermissions(List<String> permissions) {
        boolean shouldShowRationale = false;
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                shouldShowRationale = true;
                break;
            }
        }

        if (shouldShowRationale) {
            showPermissionRationaleDialog(permissions);
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    permissions.toArray(new String[0]),
                    REQUEST_CODE_PERMISSIONS);
        }
    }

    private void showPermissionRationaleDialog(List<String> permissions) {
        new AlertDialog.Builder(this)
                .setTitle("Необходимы разрешения")
                .setMessage(getPermissionExplanation(permissions))
                .setPositiveButton("OK", (dialog, which) ->
                        ActivityCompat.requestPermissions(
                                MainActivity.this,
                                permissions.toArray(new String[0]),
                                REQUEST_CODE_PERMISSIONS))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private String getPermissionExplanation(List<String> permissions) {
        StringBuilder explanation = new StringBuilder();
        explanation.append("Для работы приложения требуются следующие разрешения:\n");

        for (String permission : permissions) {
            switch (permission) {
                case Manifest.permission.CAMERA:
                    explanation.append("\n• Камера - для съемки фотографий");
                    break;
                case Manifest.permission.READ_MEDIA_IMAGES:
                case Manifest.permission.READ_EXTERNAL_STORAGE:
                    explanation.append("\n• Чтение хранилища - для работы с галереей");
                    break;
                case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                    explanation.append("\n• Запись в хранилище - для сохранения фото");
                    break;
                case Manifest.permission.ACCESS_FINE_LOCATION:
                case Manifest.permission.ACCESS_COARSE_LOCATION:
                    explanation.append("\n• Геолокация - для добавления координат к фото");
                    break;
            }
        }
        return explanation.toString();
    }

    private void initCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Ошибка инициализации камеры", e);
                Toast.makeText(this, "Ошибка инициализации камеры", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
    }

    private void requestLocation() {
        if (hasLocationPermission()) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            lastKnownLocation = location;
                            Log.d(TAG, "Получены координаты: " +
                                    location.getLatitude() + ", " + location.getLongitude());
                        }
                    });
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        ContentValues contentValues = createContentValues();
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
        ).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        handleImageSaveSuccess(outputFileResults.getSavedUri(), contentValues);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        handleImageSaveError(exception);
                    }
                });
    }

    private ContentValues createContentValues() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME,
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/PetroglyphCam");
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        return contentValues;
    }

    private void handleImageSaveSuccess(Uri savedUri, ContentValues contentValues) {
        if (savedUri != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear();
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(savedUri, contentValues, null, null);
            }

            Intent intent = new Intent(MainActivity.this, PreviewActivity.class);
            intent.setData(savedUri);
            if (lastKnownLocation != null) {
                intent.putExtra("location", lastKnownLocation);
            }
            intent.putExtra("direction", currentDirection);
            intent.putExtra("should_delete_on_cancel", true);
            startActivityForResult(intent, REQUEST_CODE_PREVIEW);
        } else {
            Toast.makeText(this, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleImageSaveError(ImageCaptureException exception) {
        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
        runOnUiThread(() ->
                Toast.makeText(MainActivity.this, "Ошибка при сохранении фото", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_APP_SETTINGS) {
            checkPermissions();
        } else if (requestCode == REQUEST_CODE_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                Intent intent = new Intent(this, PreviewActivity.class);
                intent.setData(selectedImageUri);
                intent.putExtra("should_delete_on_cancel", false);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            handlePermissionResults(grantResults);
        }
    }

    private void handlePermissionResults(int[] grantResults) {
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            initCamera();
            requestLocation();
        } else {
            handlePermissionDenial();
        }
    }

    private void handlePermissionDenial() {
        List<String> deniedPermissions = getDeniedPermissions();
        if (shouldShowPermissionRationale(deniedPermissions)) {
            showPermissionRationaleDialog(deniedPermissions);
        } else {
            showSettingsRedirectDialog();
        }
    }

    private List<String> getDeniedPermissions() {
        List<String> denied = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                denied.add(permission);
            }
        }
        return denied;
    }

    private boolean shouldShowPermissionRationale(List<String> permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    private void showSettingsRedirectDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Требуются разрешения")
                .setMessage("Вы запретили запрос разрешений. Пожалуйста, предоставьте разрешения в настройках приложения.")
                .setPositiveButton("Настройки", (dialog, which) -> openAppSettings())
                .setNegativeButton("Выйти", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivityForResult(intent, REQUEST_CODE_APP_SETTINGS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}


