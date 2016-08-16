package org.crazyit.service;

import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;

public class FirstService extends Service
{
	private static final String TAG = "MainActivity";
	private int mScreenDensity=MainActivity.mScreenDensity;
	private int mScreenWidth=MainActivity.mScreenWidth;
	private int mScreenHeight=MainActivity.mScreenHeight;
	private long totalSize = 0;
	private int mVideoTrackIndex;
	private boolean isRun = MainActivity.isRun;
	MediaCodec.BufferInfo info=MainActivity.info;
	MediaCodec mediaCodec=MainActivity.mediaCodec;
	private Surface mSurface=MainActivity.mSurface;
	private MediaProjection mMediaProjection;
	private VirtualDisplay mVirtualDisplay;
	private MyThread myThread;
	private MediaMuxer mMediaMuxer;
	private String FileName;
	private static final String SDCARD_PATH  = Environment.getExternalStorageDirectory().getPath();
	// 必须实现的方法
	@Override
	public IBinder onBind(Intent arg0)
	{
		return null;
	}
	// Service被创建时回调该方法
	@Override
	public void onCreate()
	{
		super.onCreate();
		System.out.println("Service is Started");
		System.out.println("mMedia="+MainActivity.mMediaProjection);
		showimage();
		myThread = new MyThread();
		myThread.start();
	}
	// Service被启动时回调该方法
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		return START_STICKY;
	}
	// Service被关闭之前回调
    public void showimage()
	{
	  MainActivity.mMediaProjection.createVirtualDisplay("屏幕捕捉", mScreenWidth, mScreenHeight,
			  mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
			  mSurface, null, null);
	}
	class MyThread extends Thread{
		public void run(){
			try {
				Log.v("doNext","process()");
				process();
				//Thread.sleep(100);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	protected boolean process() throws IOException {
		Log.v("process()","process() start");
		isRun = true;
		SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyyMMddhhmmss");
		FileName = sDateFormat.format(new java.util.Date());
		mMediaMuxer = new MediaMuxer(SDCARD_PATH+"/"+FileName+".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
		try{

			while(isRun){

				drainEncoder(false);
				//Thread.sleep(200);
			}
			drainEncoder(true);
		}
		finally {
			releaseEncoder();
		}
		return true;
	}
	private void releaseEncoder() {
		Log.d(TAG, "releasing encoder objects");
		if (mediaCodec != null) {
			mediaCodec.stop();
			mediaCodec.release();
			mediaCodec = null;
		}
		if (mSurface != null) {
			mSurface.release();
			mSurface = null;
		}
		if (mMediaMuxer != null) {
			mMediaMuxer.stop();
			mMediaMuxer.release();
			mMediaMuxer = null;
		}
		if(mVirtualDisplay!=null){
			mVirtualDisplay.release();
			mVirtualDisplay = null;
		}
		if(mMediaProjection!=null){
			mMediaProjection.stop();
			mMediaProjection = null;
		}
	}
	private void drainEncoder(boolean endOfStream) {
		final int TIMEOUT_USEC = 10000;
		Log.d(TAG, "drainEncoder(" + endOfStream + ")");

		if (endOfStream) {
			Log.d(TAG, "sending EOS to encoder");
			mediaCodec.signalEndOfInputStream();
		}
        /*encoderOutputBuffers是一个ByteBuffer的数组
        * ByteBuffer本身是一个数组
        * 所以encoderOutputBuffers是一个二维数组
        * 类似乒乓缓存一样，mediaCodec向这组数组中轮流写数据
        * */
		ByteBuffer[] encoderOutputBuffers = mediaCodec.getOutputBuffers();
		while (true) {

			int encoderStatus = mediaCodec.dequeueOutputBuffer(info, TIMEOUT_USEC);//dequeue出队，encoderStatus返回是写入多缓存的哪个缓存
			if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
				//-1
				// no output available yet
				if (!endOfStream) {
					break;      // out of while
				} else {
					Log.d(TAG, "no output available, spinning to await EOS");
				}
			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				// not expected for an encoder
				//-3
				encoderOutputBuffers = mediaCodec.getOutputBuffers();
			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                /*
                * MediaCodec在一开始调用dequeueOutputBuffer()时会返回一次INFO_OUTPUT_FORMAT_CHANGED消息。
                * 我们只需在这里获取该MediaCodec的format，并注册到MediaMuxer里。
                * 这是一个技巧，大家都这么用
                * */
				MediaFormat newFormat = mediaCodec.getOutputFormat();
				Log.d(TAG, "encoder output format changed: " + newFormat);

                /*
                //从newFormat中提取SPS和PPS，设置进format
                //这样就可以用之前的format，format中包含码率、关键帧等信息
                //newFormat不包含
                ByteBuffer csd0 = newFormat.getByteBuffer("csd-0");
                ByteBuffer csd1 = newFormat.getByteBuffer("csd-1");
                format.setByteBuffer("csd-0",csd0);
                format.setByteBuffer("csd-1",csd1);
                mVideoTrackIndex = mMediaMuxer.addTrack(format);
                */
				mVideoTrackIndex = mMediaMuxer.addTrack(newFormat);//可以直接传入newFormat
				mMediaMuxer.start();
				// mMuxerStarted = true;
			} else if (encoderStatus < 0) {
				Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
						encoderStatus);
				// let's ignore it
			} else {
                /*encodedData是二维数组encoderOutputBuffers中的第encoderStatus行
                *
                * */
				ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
				if (encodedData == null) {
					throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
							" was null");
				}

				if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					// The codec config data was pulled out and fed to the muxer when we got
					// the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
					Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
					info.size = 0;
				}

				if (info.size != 0) {
					// adjust the ByteBuffer values to match BufferInfo (not needed?)
//                    encodedData.position(info.offset);
//                    encodedData.limit(info.offset + info.size);
					mMediaMuxer.writeSampleData(mVideoTrackIndex, encodedData, info);
					//从encodedData中可直接获取数据,而且是经过H.264压缩后的数据
					//Log.d(TAG, "sent " + info.size + " bytes to muxer");
				}
				totalSize+=info.size;
				mediaCodec.releaseOutputBuffer(encoderStatus, false);

				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					//如果
					if (!endOfStream) {
						Log.w(TAG, "reached end of stream unexpectedly");
					} else {
						Log.d(TAG, "end of stream reached");
					}
					break;      // out of while
				}
			}
		}
	}
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		isRun=false;
		System.out.println("Service is Destroyed");
	}
}
