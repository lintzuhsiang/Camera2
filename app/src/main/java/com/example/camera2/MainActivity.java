package com.example.camera2;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;

import com.vuzix.hud.actionmenu.ActionMenuActivity;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

//import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;


import android.hardware.camera2.*;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;


/**
 * An activity that listens for audio and then uses a TensorFlow model to detect particular classes,
 * by default a small set of action words.
 */
public class MainActivity extends ActionMenuActivity {
//        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    // Constants that control the behavior of the recognition code and model
    // settings. See the audio recognition tutorial for a detailed explanation of
    // all these, but you should customize them to match your training settings if
    // you are running your own model.
    private static final int SAMPLE_RATE = 48000;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);

    private static final String LOG_TAG = "Thesis";
    private static final String TAG = "Thesis";


    // UI elements.
    private static final int REQUEST_RECORD_AUDIO = 13;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_AUDIO_PERMISSION = 300;
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    // Working variables.
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;
    boolean shouldContinue = true;
    boolean shouldContinueRecognition = true;
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault());
    private Thread recordingThread;
    private Thread recognitionThread;
    private List<String> labels = new ArrayList<String>();
    private List<String> displayedLabels = new ArrayList<>();
    private LinearLayout bottomSheetLayout;


    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
   ;
    //camera
    private String cameraId;
    //  private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
//    @Override
//    public void onImageAvailable(ImageReader imageReader) {
//      backgroundHandler.post(new ImageSaver(imageReader.acquireLatestImage()))
//    }
//  };
    private Size imageDimension;
    private Bitmap storedBitmap;
    private ImageReader imageReader;
    private String fileName = null;
    private File file;
    private TextureView mTextureView;
    private MediaRecorder recorder;
    private AudioRecord record;
    private Surface recorderSurface;
    private CameraCaptureSession recordCaptureSession;
    protected CameraCaptureSession previewCaptureSession;
    private CameraCaptureSession mCaptureSession;
    protected CameraDevice cameraDevice;
    protected CaptureRequest.Builder captureRequestBuilder;

    private Button photo_btn;
    private Button video_btn;
    private Button sensor_btn;
    private ImageView redDot;
    float maxZoom;
    int minWidth;
    int minHeight;
    int difWidth;
    int difHeight;
//    private RecordWaveTask recordTask = null;
    private boolean mIsRecording = false;
    private boolean mIsSensoring = false;
    //sensor
    private SensorManager sensorManager;
    private SensorEventListener sensorEventListener = new SensorEventListener() {

        private float minGyro = -5;
        private float maxGyro = 5;
        private float newminGyro = 0;
        private float newmaxGyro = 5;
        private float ratio = (newmaxGyro - newminGyro) / (maxGyro - minGyro);

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values != null) {
                switch (event.sensor.getStringType()) {
                    case Sensor.STRING_TYPE_GYROSCOPE:
//                        Log.d(TAG, "gyroscope x "+ String.valueOf(100*event.values[0]));
//                        Log.d(TAG, "gyroscope y "+ String.valueOf(100*event.values[1]));
//
//                        Log.d(TAG, "gyroscope z "+ String.valueOf(100*event.values[2]));

                        //                        mOnHeadListener.onChanged(event.values[2]);
                        float zoomNorm = newminGyro + ratio * (event.values[0] - minGyro);  //range (0,5)
                        float zoomLevel = zoomNorm * (mTextureView.getHeight() / maxZoom / 5);  //range (0,120)
//                        int cropWidth = difWidth /((int)(50 * zoomLevel)+1);
//                        int cropHeight = difHeight /((int)(50 * zoomLevel)+1);
//                        int cropWidth = (int) (2*zoomLevel);
//                        int cropHeight = (int) (2*zoomLevel);

//                        Log.d(TAG, "TextureView width "+String.valueOf(mTextureView.getWidth()));
//                        Log.d(TAG, "TextureView height "+String.valueOf(mTextureView.getHeight()));
//                        Log.d(TAG, "maxzoom "+String.valueOf(maxZoom));
//                        Log.d(TAG, "zoomNorm "+String.valueOf(zoomNorm));
//
//                        Log.d(TAG, "zoomLevel "+String.valueOf(zoomLevel));
//                        Log.d(TAG, "minWidth "+String.valueOf(minWidth));
//                        Log.d(TAG, "minHeight "+String.valueOf(minHeight));
//                        Log.d(TAG, "difWidth "+String.valueOf(difWidth));
//                        Log.d(TAG, "difHeight "+String.valueOf(difHeight));
//
                        int cropHeight = 0;
                        int cropWidth = 0;

//                        Log.d(TAG, "cropWidth "+String.valueOf(cropWidth));
//                        Log.d(TAG, "cropHeight "+String.valueOf(cropHeight));
                        Log.d(TAG,"image dimension: "+imageDimension.getWidth()+"   "+imageDimension.getHeight());

//                        Rect zoom = new Rect(cropWidth, cropHeight, mTextureView.getWidth() - cropWidth, mTextureView.getHeight() - cropHeight);
                        Rect zoom = new Rect(0, 0, 480 + 0, 480 + 0);

//                        Rect zoom = new Rect(cropWidth, cropHeight, mTextureView.getWidth() - cropWidth, mTextureView.getHeight() - cropHeight);
                        try {
                            updatePreview(zoom);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }

                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

//    private static AccelerometerListener listener;

    /**
     * Memory-map the model file in Assets.
     */


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up the UI.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.textureView);
        video_btn = findViewById(R.id.video_button);
        photo_btn = findViewById(R.id.camera_button);
        sensor_btn = findViewById(R.id.sensor_button);
        redDot = findViewById(R.id.red_dot);
        int imageResource = getResources().getIdentifier("@drawable/reddot", "drawable", getPackageName());
        redDot.setImageResource(imageResource);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        assert photo_btn != null;
        assert video_btn != null;

        photo_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startStillCapture();
            }

        });
        video_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsRecording) {
                    Log.d(TAG, "stop record");
                    mIsRecording = false;
                    redDot.setVisibility(View.INVISIBLE);
                    stopRecord();
                } else {
                    Log.d(TAG, "start record");
                    redDot.setVisibility(View.VISIBLE);
                    mIsRecording = true;
                    startRecord();
                    recorder.start();
                }
            }
        });
        sensor_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsSensoring) {
                    mIsSensoring = false;
                    stopPreview();
                    stopSensor();
                } else {
                    mIsSensoring = true;
                    startPreview();
                    startSensor();
                }
            }
        });
//

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        System.out.println("width-display :"  + dm.widthPixels);
        System.out.println("heigth-display :"  + dm.heightPixels);
        System.out.println("xdpi :"  + dm.xdpi);
        System.out.println("ydpi :"  + dm.ydpi);

        System.out.println("density :"  + dm.density);
        System.out.println("densityDpi :"  + dm.densityDpi);
        System.out.println("heightPixels :"  + dm.heightPixels);
        System.out.println("widthPixels :"  + dm.widthPixels);




// 通過Resources獲取
        DisplayMetrics dm2 = getResources().getDisplayMetrics();
        System.out.println("width-display :" +   dm2.widthPixels);
        System.out.println("heigth-display :" +  dm2.heightPixels);
        System.out.println("xdpi :"  + dm2.xdpi);
        System.out.println("ydpi :"  + dm2.ydpi);
// 獲取螢幕的預設解析度
        Display display = getWindowManager().getDefaultDisplay();
        System.out.println("width-display :"  + display.getWidth());
        System.out.println("heigth-display :"  + display.getHeight());
    }


    // Set up an object to smooth recognition results to increase accuracy.

    private void requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

//        @Override
//        public void onRequestPermissionsResult (
//        int requestCode, String[] permissions,int[] grantResults){
//            if (requestCode == REQUEST_RECORD_AUDIO
//                    && grantResults.length > 0
//                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                startRecording();
//                startRecognition();
//            }
//        }
//
//    public synchronized void startRecording() {
//        if (recordingThread != null) {
//            return;
//        }
//        shouldContinue = true;
//        recordingThread =
//                new Thread(
//                        new Runnable() {
//                            @Override
//                            public void run() {
//                                record();
//                            }
//                        });
//        recordingThread.start();
//    }
//
//    public synchronized void stopRecording() {
//        if (recordingThread == null) {
//            return;
//        }
//        shouldContinue = false;
//        recordingThread = null;
//    }

//    private void record() {
//        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
//
//        // Estimate the buffer size we'll need for this device.
//        int bufferSize =
//                AudioRecord.getMinBufferSize(
//                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
//        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
//            bufferSize = SAMPLE_RATE * 2;
//        }
//        short[] audioBuffer = new short[bufferSize / 2];
//
//        AudioRecord record =
//                new AudioRecord(
//                        MediaRecorder.AudioSource.DEFAULT,
//                        SAMPLE_RATE,
//                        AudioFormat.CHANNEL_IN_MONO,
//                        AudioFormat.ENCODING_PCM_16BIT,
//                        bufferSize);
//
//        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
//            Log.e(LOG_TAG, "Audio Record can't initialize!");
//            return;
//        }
//
//        record.startRecording();
//
//
//        Log.v(LOG_TAG, "Start recording");
//
//        // Loop, gathering audio data and copying it to a round-robin buffer.
//        while (shouldContinue) {
//            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
//            int maxLength = recordingBuffer.length;
//            int newRecordingOffset = recordingOffset + numberRead;
//            int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
//            int firstCopyLength = numberRead - secondCopyLength;
//            // We store off all the data for the recognition thread to access. The ML
//            // thread will copy out of this buffer into its own, while holding the
//            // lock, so this should be thread safe.
//            recordingBufferLock.lock();
//            try {
//                System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
//                System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
//                recordingOffset = newRecordingOffset % maxLength;
//            } finally {
//                recordingBufferLock.unlock();
//            }
//        }
//
//        record.stop();
//        record.release();
//    }


    private static final String HANDLE_THREAD_NAME = "CameraBackground";

    private void startBackgroundThread() {
        backgroundHandlerThread = new HandlerThread(HANDLE_THREAD_NAME);
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundHandlerThread != null) {
            backgroundHandlerThread.quitSafely();
        }
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted when stopping background thread", e);
        }
    }

    //camera
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.e(LOG_TAG, "onOpened");
            cameraDevice = camera;
            if (mIsRecording) {
                startRecord();
                recorder.start();
            } else {
//                startPreview();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            camera.close();
            cameraDevice = null;

        }
    };
    protected void startPreview() {
        mTextureView.setAlpha((float) 1.0);

        if (!mIsRecording) {
            try {
                SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();

                assert surfaceTexture != null;
                surfaceTexture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());

                Surface previewSurface = new Surface(surfaceTexture);
//                    Surface recorderSurface = recorder.getSurface();

                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuilder.addTarget(previewSurface);

//

                cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession Session) {
                        if (null == cameraDevice) {
                            return;
                        }
//                        previewCaptureSession = Session;
                        mCaptureSession = Session;

//                        try {
////                            previewCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
//                            mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
//                        } catch (CameraAccessException e) {
//                            e.printStackTrace();
//                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.d(TAG, "unable to turn on preview");
                    }
                }, null);
//                    }

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void updatePreview(Rect zoom) throws CameraAccessException {
//        captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
//        previewCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
    }

    private void stopPreview() {
        mTextureView.setAlpha((float) 0);

        if (!mIsRecording) {
            try {
//            previewCaptureSession.stopRepeating();
                mCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void startStillCapture() {
        if (null == cameraDevice) {
            openCamera();
            Log.e(LOG_TAG, "cameraDevice is null");
        }
        try {
            if (mIsSensoring) {
                stopSensor();
                stopPreview();
                mIsSensoring = false;
                mTextureView.setAlpha((float) 0);
            }
//        float maxzoom = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)) * 10;
//        Size[] jpegSizes = null;
//        if (characteristics != null) {
//          jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
//        }
//        if (jpegSizes != null && 0 < jpegSizes.length) {
//          width = jpegSizes[0].getWidth();
//          height = jpegSizes[0].getHeight();
//        }
            if (mIsRecording) {
                //legacy camera don't support video snapshot
//                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);

            } else {
                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            }
//            if(mIsSensoring){
//                mCaptureSession.stopRepeating();
//            }
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);

            outputSurfaces.add(imageReader.getSurface());
            //preview surface
            outputSurfaces.add(new Surface(mTextureView.getSurfaceTexture()));


            captureRequestBuilder.addTarget(imageReader.getSurface());
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));


            String filename = getFilePath();
            file = new File(filename + ".jpg");


            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();

                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);

                        if (isExternalStorageWritable()) {
                            save(bytes);
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                //save image to device
                private void save(byte[] bytes) throws IOException {
                    FileOutputStream output = null;
                    storedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                    Matrix mat = new Matrix();
                    mat.postRotate(270);
                    storedBitmap = Bitmap.createBitmap(storedBitmap, 0, 0, storedBitmap.getWidth(), storedBitmap.getHeight(), mat, true);
                    try {
                        output = new FileOutputStream(file);
                        storedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
                    } finally {
                        if (null != output) {
                            Log.d(LOG_TAG, "image path: " + file.getAbsolutePath());
                            output.flush();
                            output.close();
                        }
                    }
                }
            };

            imageReader.setOnImageAvailableListener(readerListener, backgroundHandler);


            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {

                    super.onCaptureCompleted(session, request, result);
//                    if(mIsSensoring){
//                        Log.d(TAG,"in stillcapture capture callback listener");
//                        updatePreview();
//                    }
//                    Log.d(TAG,"isSensoring ==false and  stillcapture capture callback listener");

                    //create file name
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession s, CaptureRequest r, CaptureFailure t) {
                    super.onCaptureFailed(s, r, t);

                    Log.d(TAG, "failed in capture callback");

                }
            };

            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession Session) {
                    if (null == cameraDevice) {
                        return;
                    }
                    mCaptureSession = Session;
                    try {
                        mCaptureSession.capture(captureRequestBuilder.build(), captureListener, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.d(TAG, "unable to turn on preview");
                }
            }, null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void setUpRecord() {
        if (recorder == null) {
            recorder = new MediaRecorder();
        }


//        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
//
        CamcorderProfile profile = CamcorderProfile.get(Integer.parseInt(cameraId), CamcorderProfile.QUALITY_HIGH);
        recorder.setOutputFormat(profile.fileFormat);
        recorder.setVideoEncoder(profile.videoCodec);
        recorder.setVideoEncodingBitRate(profile.videoBitRate);
        recorder.setVideoFrameRate(profile.videoFrameRate);
        recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);

//        CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
//        recorder.setProfile(cpHigh);
        String fileName = getFilePath();
        recorder.setOutputFile(fileName + ".3gp");


        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }

       record = new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);



        try {
            recorder.prepare();
            Log.d(TAG, "recorder prepared");
        } catch (IOException e) {
        }
    }

    private void startRecord() {
        try {
            setUpRecord();
            record.startRecording();
            int bufferSize =
                    AudioRecord.getMinBufferSize(
                            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                bufferSize = SAMPLE_RATE * 2;
            }
            short[] audioBuffer = new short[bufferSize / 2];
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
            Log.d("Audio", String.valueOf(numberRead));
//            mTextureView.setAlpha((float) 0);
            if (mIsSensoring) {
                stopSensor();
                stopPreview();
                mIsSensoring = false;
            }
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface previewSurface = new Surface(texture);
            Surface recorderSurface = recorder.getSurface();

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.addTarget(recorderSurface);


            //surfaces can be replaced as recorderSurface if don't want preview
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, recorderSurface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                //            cameraDevice.createCaptureSession(Arrays.asList(recorderSurface,imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
//                        recordCaptureSession = cameraCaptureSession;
                    mCaptureSession = cameraCaptureSession;
                    try {
//                            recordCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                        mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                }
            }, null);//cameraHandler
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void stopRecord() {
        if (mIsSensoring) {
            mIsSensoring = false;
            stopSensor();
        }
        mTextureView.setAlpha((float) 0);   //setAlpha 0 equals to invisible
//            if (cameraDevice != null && recordCaptureSession != null) {
        if (cameraDevice != null && mCaptureSession != null) {
            try {
//                  recordCaptureSession.stopRepeating();
                mCaptureSession.stopRepeating();
                record.stop();
                record.release();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                if (null != recorder) {
                    recorder.stop();
                    recorder.reset();    // set state to idle
//                        recorder.release();
                    recorder = null;
                }
            }
        };
        timer.schedule(timerTask, 100);

    }


    private void updatePreview() {
        try {
            mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //setup camera
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Log.d("Camera", String.valueOf(characteristics.getAvailableCaptureRequestKeys()));

            //set for preview use
            maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            minWidth = (int) (mTextureView.getWidth() / maxZoom);
            minHeight = (int) (mTextureView.getHeight() / maxZoom);
            difWidth = mTextureView.getWidth() - minWidth;
            difHeight = mTextureView.getHeight() - minHeight;
            Log.d(TAG, "TextureView width " + String.valueOf(mTextureView.getWidth()));
            Log.d(TAG, "TextureView height " + String.valueOf(mTextureView.getHeight()));
            Log.d(TAG, "maxzoom " + String.valueOf(maxZoom));
            Log.d(TAG, "minWidth " + String.valueOf(minWidth));
            Log.d(TAG, "minHeight " + String.valueOf(minHeight));
            Log.d(TAG, "difWidth " + String.valueOf(difWidth));
            Log.d(TAG, "difHeight " + String.valueOf(difHeight));
            Log.d(TAG, "Open Camera complete");

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;

            imageDimension = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), mTextureView.getWidth(), mTextureView.getHeight());
            imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(), ImageFormat.JPEG, 1);
            Log.d(TAG,"image dimension: "+imageDimension.getWidth()+"   "+imageDimension.getHeight());
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            }
            manager.openCamera(cameraId, stateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //connect to camera
    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
                } else {
                    if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                        Toast.makeText(this, "Video app required access to camera", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[]{android.Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
                    }, REQUEST_CAMERA_PERMISSION_RESULT);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != recorder) {
            recorder.release();
            recorder = null;
        }
    }

    private void startSensor() {
        sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopSensor() {
        sensorManager.unregisterListener(sensorEventListener);

    }


    private String getFilePath() {
        fileName = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/" + formatter.format(new Date());
        Log.i(TAG, "path " + fileName);
        return fileName;
    }

    private boolean isExternalStorageWritable() {

        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());

    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) (lhs.getWidth() * lhs.getHeight()) - (long) (rhs.getWidth() * rhs.getHeight()));
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not run without camera services", Toast.LENGTH_SHORT).show();
            }
            if (grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not have audio on record", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mIsRecording) {
                    mIsRecording = true;
//                    mRecordImageButton.setImageResource(R.mipmap.btn_video_busy);
                }
                Toast.makeText(this,
                        "Permission successfully granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "App needs to save video to run", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            Log.d(TAG, "onResuem textureView is Available");
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(textureListener);
        }
    }


    @Override
    protected void onPause() {
        closeCamera();
        stopSensor();
        stopBackgroundThread();
        super.onPause();
    }
}
