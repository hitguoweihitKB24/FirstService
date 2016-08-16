package org.crazyit.service;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import java.io.IOException;

public class MainActivity extends Activity
{
	Button start, stop;
	private static final String TAG = "MainActivity";
	public static int mScreenDensity;
	public static int mScreenWidth;
	public static int mScreenHeight;
	private static final String MIME_TYPE = "video/avc";

	private int framerate = 15;
	public static boolean isRun = false;
	public static MediaCodec.BufferInfo info;
	public static MediaFormat format;
	public static MediaCodec mediaCodec;
	public static Surface mSurface;
	public static MediaProjection mMediaProjection;
	private MediaProjectionManager mMediaProjectionManager;
	MediaFormat newFormat2;
	private static final int CAPTURE_CODE = 115;
	 private Intent intent=null;
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		super.onCreate(savedInstanceState);
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		mScreenDensity = metrics.densityDpi;
		mScreenWidth = metrics.widthPixels;
		mScreenHeight = metrics.heightPixels;
		mMediaProjectionManager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);

		// 获取程序界面中的start、stop两个按钮
		start = (Button) findViewById(R.id.start);
		stop = (Button) findViewById(R.id.stop);
		// 创建启动Service的Intent
		 intent = new Intent(this , FirstService.class);
		start.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View arg0)
			{
				// 启动指定Service
				StartScreenCapture();

			}
		});
		stop.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View arg0)
			{
				// 停止指定Service
				StopScreenCapture();
				stopService(intent);
			}
		});
	}
	private void StartScreenCapture(){
		prepareEncoder();
		Intent intentA = mMediaProjectionManager.createScreenCaptureIntent();
		startActivityForResult(intentA, CAPTURE_CODE);
		Log.v(TAG, "StartScreenCapture()");
		return;
	}
	private void StopScreenCapture(){
		isRun = false;
		Log.v(TAG,"StopScreenCapture()");
	}
	private void prepareEncoder(){
		info = new MediaCodec.BufferInfo();
		format = MediaFormat.createVideoFormat(MIME_TYPE,mScreenWidth,mScreenHeight);
		format.setInteger(MediaFormat.KEY_BIT_RATE, 1250000);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
				MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
		try {
			mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
		} catch (IOException e) {
			e.printStackTrace();
		}
		mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		newFormat2 = mediaCodec.getOutputFormat();
		mSurface = mediaCodec.createInputSurface();//mSurface必须用OpenGL绘制， lockCanvas会出错
		mediaCodec.start();
		Log.d(TAG, "encoder output format not changed: " + format);
		//没有设置format的SPS和PPS，所以format不能用
		//后面设置了
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == CAPTURE_CODE){
			if (resultCode != RESULT_OK)
			{
				Log.i("onActivityResult","用户取消了屏幕捕捉");
				return;
			}
		}
		mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode,data);
		Log.v("onActivityResult", "createVirtualDisplay start");
		Log.v(TAG,"onActivityResult");
		startService(intent);
	}
}


