package com.example.skpark.lifeteller;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.SensorEventListener;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.widget.Toast.makeText;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class AndroidSampleActivity extends AppCompatActivity implements SensorEventListener {

    private Button startBtn;
    //private TextView latitudeTxt;
    //private TextView longitudeTxt;

    private TextView accXTxt;
    private TextView accYTxt;
    private TextView accZTxt;
    private TextView speedTxt;

    private FrameLayout previewFrame;

    private CameraSurfaceView cameraView;

    private boolean isOnPause = false;
    private static final int CAPTURE_INTERVAL = 5000;

    public final static String FOLDER_PATH = "LifeTeller";
    public final static String THUMBNAIL_FOLDER_PATH = "LifeTeller" + File.separator + "thumbnail";

    private int testCounter = 0;

    private int IMAGE_SIZE_X = 1200;
    private int IMAGE_SIZE_Y = 1600;
    private int THUMBNAIL_IMAGE_SIZE_X = 300;
    private int THUMBNAIL_IMAGE_SIZE_Y = 400;

    private double latitude = 0.0;
    private double longitude = 0.0;

    private long lastTime;
    private float speed;
    private float lastX;
    private float lastY;
    private float lastZ;
    private float x = 0.0f;
    private float y = 0.0f;
    private float z = 0.0f;

    private static final int SHAKE_THRESHOLD = 500;
    private static final int DATA_X = SensorManager.DATA_X;
    private static final int DATA_Y = SensorManager.DATA_Y;
    private static final int DATA_Z = SensorManager.DATA_Z;
    private SensorManager sensorManager;
    private Sensor accelerometerSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_android_sample);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //latitudeTxt = (TextView) findViewById(R.id.latitudeTxt);
        //longitudeTxt = (TextView) findViewById(R.id.longitudeTxt);

        accXTxt = (TextView) findViewById(R.id.accXTxt);
        accYTxt = (TextView) findViewById(R.id.accYTxt);
        accZTxt = (TextView) findViewById(R.id.accZTxt);
        speedTxt = (TextView) findViewById(R.id.speedTxt);
        startBtn = (Button) findViewById(R.id.startBtn);
        previewFrame = (FrameLayout)findViewById(R.id.previewFrame);

        cameraView = new CameraSurfaceView(getApplicationContext());
        previewFrame.addView(cameraView);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        startBtn.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                //TODO: 쓰레드 정상적으로 STOP/START 전환 할 수 있도록 구현 필요
                if (startBtn.getText().equals("동작중...")) {
                    finish();
                    System.exit(0);
                }
                else {
                    //startBtn.setVisibility(View.INVISIBLE);
                    captureThread.start();
                    startBtn.setText("동작중...");
                }
            }
        });

        checkDangerousPermissions();
        startLocationService();
    }



    private Thread captureThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while (!isOnPause) {
                try {
                    Thread.sleep(CAPTURE_INTERVAL);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
                try {

                    cameraView.capture(new Camera.PictureCallback() {
                        public void onPictureTaken(byte[] data, Camera camera) {
                            try {
                                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

                                Matrix m = new Matrix();
                                m.postRotate(90); //카메라가 반시계 방향으로 90도 돌아가서 찍길래 이 코드로 정상화시킴
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

                                //오리지널 파일 저장용 디렉토리 확인 및 파일 저장
                                if (new File(Environment.getExternalStorageDirectory(), FOLDER_PATH).mkdirs()) {
                                    Log.d("CAMERA", "FOLDER_PATH Created");
                                }
                                String savePicName = Environment.getExternalStorageDirectory().getPath() + File.separator + FOLDER_PATH + File.separator + timeStamp + ".jpg";
                                FileOutputStream originalFile = new FileOutputStream(savePicName);
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, originalFile);
                                originalFile.close();

                                //오리지널 파일의 EXIF 정보 삽입 (Degree포맷으로 저장됨)
                                ExifInterface exif = new ExifInterface(Environment.getExternalStorageDirectory().getPath() + File.separator + FOLDER_PATH + File.separator + timeStamp + ".jpg");
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, String.valueOf(latitude));
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, String.valueOf(longitude));
                                exif.setAttribute(ExifInterface.TAG_DATETIME, new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").format(new Date()));
                                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, String.valueOf(x)+";"+String.valueOf(y)+";"+String.valueOf(z)+";"+String.valueOf(speed));
                                exif.saveAttributes();

                                //썸네일 파일 저장용 디렉토리 확인 및 파일 저장
                                bitmap = Bitmap.createScaledBitmap(bitmap, THUMBNAIL_IMAGE_SIZE_X, THUMBNAIL_IMAGE_SIZE_Y, false);
                                if (new File(Environment.getExternalStorageDirectory(), THUMBNAIL_FOLDER_PATH).mkdirs()) {
                                    Log.d("CAMERA", "FOLDER_PATH Created");
                                }
                                String saveThumbnailName = Environment.getExternalStorageDirectory().getPath() + File.separator + THUMBNAIL_FOLDER_PATH + File.separator + timeStamp + "_thumb.jpg";
                                FileOutputStream thumbnailFile = new FileOutputStream(saveThumbnailName);
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, thumbnailFile);
                                thumbnailFile.close();

                                Date dt = new Date();
                                System.out.println(dt.toString());
                                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a");
                                //Toast.makeText(getApplicationContext(), testCounter + "번째 찰칵!", Toast.LENGTH_SHORT).show();
                                Toast.makeText(getApplicationContext(), sdf.format(dt).toString() + " 찰칵!\nLat: " + String.valueOf(latitude) + "\nLong: " + String.valueOf(longitude) +  "\nSpeed: " + speed, Toast.LENGTH_SHORT).show();
                                //latitudeTxt.setText(String.valueOf(testCounter));
                                //testCounter += 1;

                                // restart
                                camera.startPreview();
                            } catch (Exception e) {
                                Log.e("Life Teller", "Failed to insert image.", e);
                            }
                        }
                    });
                }
                catch (RuntimeException e) {
                    Log.e("Life Teller", "takePicture failed");
                }
            }
        }
    });

    @Override
    public void onStart() {
        super.onStart();
        if (accelerometerSensor != null)
            sensorManager.registerListener(this, accelerometerSensor,
                    SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (sensorManager != null)
            sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Nothing
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //http://pulsebeat.tistory.com/44 참고
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long currentTime = System.currentTimeMillis();
            long gabOfTime = (currentTime - lastTime);
            if (gabOfTime > 50) {
                lastTime = currentTime;
                x = event.values[SensorManager.DATA_X];
                y = event.values[SensorManager.DATA_Y];
                z = event.values[SensorManager.DATA_Z];

                speed = Math.abs(x + y + z - lastX - lastY - lastZ) / gabOfTime * 10000;

                if (speed > SHAKE_THRESHOLD) {
                    //Toast.makeText(getApplicationContext(), speed + " 만큼 흔들림", Toast.LENGTH_SHORT).show();
                }

                lastX = event.values[DATA_X];
                lastY = event.values[DATA_Y];
                lastZ = event.values[DATA_Z];
            }

            accXTxt.setText(String.valueOf(x));
            accYTxt.setText(String.valueOf(y));
            accZTxt.setText(String.valueOf(z));
            speedTxt.setText(String.valueOf(speed));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //isOnPause = false;
    }

    @Override
    protected void onPause() {
        // 홈버튼 또는 뒤로버튼 클릭시 호출
        // 전원버튼 눌러서 화면 껏다켜도 호출되나?
        super.onPause();
        //isOnPause = true;
    }

    private void checkDangerousPermissions() {
        String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
        };

        int permissionCheck = PackageManager.PERMISSION_GRANTED;

        for (int i = 0; i < permissions.length; i++) {
            permissionCheck = ContextCompat.checkSelfPermission(this, permissions[i]);
            if (permissionCheck == PackageManager.PERMISSION_DENIED) {
                break;
            }
        }

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            //Toast.makeText(this, "권한 있음", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "권한 없음", Toast.LENGTH_LONG).show();

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                Toast.makeText(this, "권한 설명 필요함.", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this, permissions, 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, permissions[i] + " 권한이 승인됨.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, permissions[i] + " 권한이 승인되지 않음.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     * 위치 정보 확인을 위해 정의한 메소드
     */
    private void startLocationService() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Toast.makeText(getApplicationContext(), "위치 권한 문제 발생", Toast.LENGTH_LONG).show();
            return;
        }

        // 위치 관리자 객체 참조
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // 위치 정보를 받을 리스너 생성
        GPSListener gpsListener = new GPSListener();
        long minTime = 10000;
        float minDistance = 0;

        try {
            // GPS를 이용한 위치 요청
            manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTime,
                    minDistance,
                    gpsListener);

            // 네트워크를 이용한 위치 요청
            manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTime,
                    minDistance,
                    gpsListener);

            // 위치 확인이 안되는 경우에도 최근에 확인된 위치 정보 먼저 확인
            Location lastLocation = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation != null) {
                latitude = lastLocation.getLatitude();
                longitude = lastLocation.getLongitude();

                //latitudeTxt.setText(String.valueOf(latitude));
                //longitudeTxt.setText(String.valueOf(longitude));
                //Toast.makeText(getApplicationContext(), "Last Known Location : " + "Latitude : " + latitude + "\nLongitude:" + longitude, Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException ex) {
            ex.printStackTrace();
        }

        //Toast.makeText(getApplicationContext(), "위치 확인이 시작되었습니다.", Toast.LENGTH_SHORT).show();
    }

    /**
     * GPS리스너 클래스 정의
     */
    private class GPSListener implements LocationListener {
        /**
         * 위치 정보가 확인될 때 자동 호출되는 메소드
         */
        public void onLocationChanged(Location location) {
            latitude = location.getLatitude();
            longitude = location.getLongitude();

            //latitudeTxt.setText(String.valueOf(latitude));
            //longitudeTxt.setText(String.valueOf(longitude));

            //String msg = "Latitude : "+ latitude + "\nLongitude:"+ longitude;
            //주소 팝업!
            //Toast.makeText(getApplicationContext(), getAddress(getApplicationContext(), latitude, longitude), Toast.LENGTH_SHORT).show();
        }

        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_android_sample, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * 카메라 미리보기를 위한 서피스뷰 정의
     */
    private class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera camera = null;

        public CameraSurfaceView(Context context) {
            super(context);

            mHolder = getHolder();
            mHolder.addCallback(this);

        }

        public void surfaceCreated(SurfaceHolder holder) {
            camera = Camera.open();

            try {
                // 카메라가 반시계 방향으로 90도 돌아간 상태로 찍기 때문에,
                // 해당 코드로 surfaceView에 재대로 나오도록 해결
                // 카메라 모델마다 다름.
                camera.setDisplayOrientation(90);
                camera.setPreviewDisplay(mHolder);
            } catch (Exception e) {
                Log.e("CameraSurfaceView", "Failed to set camera preview.", e);
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            try {
                camera.startPreview();
            }
            catch (Exception e) {
                Log.e("Camera", "Failed to load the camera.");
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }

        public boolean capture(Camera.PictureCallback handler) {
            if (camera != null) {
                Camera.Parameters params = camera.getParameters();
                if (params.getSupportedFocusModes().contains(
                        Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                }
                camera.setParameters(params);
                camera.takePicture(null, null, handler);
                return true;
            } else {
                return false;
            }
        }

    }
}
