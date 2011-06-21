/*
 * Copyright (c) 2011 by Double Rebel
 * http://www.doublerebel.com
 *
 *
 * Based on Titanium Barcode Module 0.2
 * Copyright (c) 2011 by M-Way Solutions GmbH
 * http://www.mwaysolutions.com
 *
 * Based on Zxing pre-3.6 (SVN Trunk Rev 1770)
 * Copyright (C) 2010 ZXing authors
 * http://code.google.com/p/zxing/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.doublerebel.barcode;

import java.io.IOException;
import java.util.Vector;
import java.util.regex.Pattern;

import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;

import com.doublerebel.barcode.camera.CameraManager;
import com.doublerebel.barcode.constants.BarcodeColor;
import com.doublerebel.barcode.constants.BarcodeString;
import com.doublerebel.barcode.constants.Id;
import com.doublerebel.barcode.views.CaptureView;
import com.doublerebel.barcode.zxing.BeepManager;
import com.doublerebel.barcode.zxing.CaptureActivityHandler;
import com.doublerebel.barcode.zxing.Intents;
import com.doublerebel.barcode.zxing.ViewfinderView;
import com.doublerebel.barcode.zxing.result.ResultHandler;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;

import android.view.KeyEvent;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;

/**
 * The barcode reader activity itself. This is loosely based on the
 * CameraPreview example included in the Android SDK.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author sven@roothausen.de (Sven Pfleiderer)
 * @author charles@doublerebel.com (Charles Phillips)
 */

public final class TitaniumBarcodeActivity extends TiBaseActivity implements
		SurfaceHolder.Callback {

	public static final String EXTRA_RESULT = "scanResult";

	private static final String TAG = "CaptureActivity";
    private static final boolean DBG = TiConfig.LOGD;
	private static final Pattern COMMA_PATTERN = Pattern.compile(",");

	static final Vector<BarcodeFormat> PRODUCT_FORMATS;
	static final Vector<BarcodeFormat> ONE_D_FORMATS;
	static final Vector<BarcodeFormat> QR_CODE_FORMATS;
	public static final Vector<BarcodeFormat> ALL_FORMATS;

	static {
		PRODUCT_FORMATS = new Vector<BarcodeFormat>(5);
		PRODUCT_FORMATS.add(BarcodeFormat.UPC_A);
		PRODUCT_FORMATS.add(BarcodeFormat.UPC_E);
		PRODUCT_FORMATS.add(BarcodeFormat.EAN_13);
		PRODUCT_FORMATS.add(BarcodeFormat.EAN_8);
		// PRODUCT_FORMATS.add(BarcodeFormat.RSS14);
		
		ONE_D_FORMATS = new Vector<BarcodeFormat>(PRODUCT_FORMATS.size() + 3);
		ONE_D_FORMATS.addAll(PRODUCT_FORMATS);
		ONE_D_FORMATS.add(BarcodeFormat.CODE_39);
		ONE_D_FORMATS.add(BarcodeFormat.CODE_128);
		ONE_D_FORMATS.add(BarcodeFormat.ITF);
		
		QR_CODE_FORMATS = new Vector<BarcodeFormat>(2);
		QR_CODE_FORMATS.add(BarcodeFormat.QR_CODE);
		QR_CODE_FORMATS.add(BarcodeFormat.DATA_MATRIX);
		
		ALL_FORMATS = new Vector<BarcodeFormat>(ONE_D_FORMATS.size()
				+ QR_CODE_FORMATS.size());
		ALL_FORMATS.addAll(ONE_D_FORMATS);
		ALL_FORMATS.addAll(QR_CODE_FORMATS);
	}

	private enum Source {
		NATIVE_APP_INTENT, NONE
	}

	private CaptureActivityHandler mHandler;

	private ViewfinderView viewfinderView;
	private Result lastResult;
	private boolean hasSurface;
	private Source source;
	private Vector<BarcodeFormat> decodeFormats;
	private String characterSet;
	private CaptureView captureView;
    private BeepManager beepManager;
    
    private TiViewProxy localOverlayProxy = null;
    
    public static TiViewProxy overlayProxy = null;
    public static TitaniumBarcodeActivity tiBarcodeActivity = null;

	/**
	 * @return current viewfinderView
	 */

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	/**
	 * @return current handler
	 */
	public Handler getHandler() {
		return mHandler;
	}
    
    /* prevent menu from crashing module */
    @Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		return false;
	}

	/**
	 * Lifecycle method which is called of the android runtime
	 */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		captureView = new CaptureView(this);
		captureView.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));
		
        // set preview overlay
		localOverlayProxy = overlayProxy;
		overlayProxy = null; // clear the static object once we have a local reference
        
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(captureView);

		CameraManager.init(getApplication());
		viewfinderView = captureView.getViewfinderView();

		lastResult = null;
		hasSurface = false;
        
        beepManager = new BeepManager(this);
	}

	/**
	 * Lifecycle method which is called of the android runtime
	 */

	@Override
	protected void onResume() {
		super.onResume();

		tiBarcodeActivity = this;
        //captureView.addView(viewfinderView);
        captureView.addView(localOverlayProxy.getView(this).getNativeView(), new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        
        SurfaceView surfaceView = captureView.getPreviewView();
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder);
		} else {
			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		Intent intent = getIntent();

        source = Source.NATIVE_APP_INTENT;
        decodeFormats = parseDecodeFormats(intent);
        beepManager.updatePrefs();
        resetStatusView();

	}

	private static Vector<BarcodeFormat> parseDecodeFormats(Intent intent) {
		String scanFormats = intent.getStringExtra(Intents.Scan.SCAN_FORMATS);
		if (scanFormats != null) {
			Vector<BarcodeFormat> formats = new Vector<BarcodeFormat>();
			try {
				for (String format : COMMA_PATTERN.split(scanFormats)) {
					formats.add(BarcodeFormat.valueOf(format));
				}
			} catch (IllegalArgumentException iae) {
				// ignore it then
                Log.w(TAG, iae.toString());
			}
		}
		String decodeMode = intent.getStringExtra(Intents.Scan.MODE);
		if (decodeMode != null) {
			if (Intents.Scan.PRODUCT_MODE.equals(decodeMode)) {
				return PRODUCT_FORMATS;
			}
			if (Intents.Scan.QR_CODE_MODE.equals(decodeMode)) {
				return QR_CODE_FORMATS;
			}
			if (Intents.Scan.ONE_D_MODE.equals(decodeMode)) {
				return ONE_D_FORMATS;
			}
		}
		return null;
	}

	@Override
	protected void onPause() {
		super.onPause();
        //captureView.removeView(viewfinderView);
		captureView.removeView(localOverlayProxy.getView(this).getNativeView());

		if (mHandler != null) {
			mHandler.quitSynchronously();
			mHandler = null;
		}
		CameraManager.get().closeDriver();
        tiBarcodeActivity = null;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (source == Source.NATIVE_APP_INTENT) {
				tiBarcodeActivity.setResult(RESULT_CANCELED);
				tiBarcodeActivity.finish();
				return true;
			} else if ((source == Source.NONE) && lastResult != null) {
				resetStatusView();
				if (mHandler != null) {
					mHandler.sendEmptyMessage(Id.RESTART_PREVIEW);
				}
				return true;
			}
		} else if (keyCode == KeyEvent.KEYCODE_FOCUS
				|| keyCode == KeyEvent.KEYCODE_CAMERA) {
			// Handle these events so they don't launch the Camera app
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	/**
	 * A valid barcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult
	 *            The contents of the barcode.
	 * @param barcode
	 *            A greyscale bitmap of the camera data which was decoded.
	 */

	public void handleDecode(Result rawResult, Bitmap barcode) {
		lastResult = rawResult;
		if (barcode == null) {
			// This is from history -- no saved barcode
			handleDecodeInternally(rawResult);
		} else {
            beepManager.playBeepSoundAndVibrate();
			drawResultPoints(barcode, rawResult);
			switch (source) {
			case NATIVE_APP_INTENT:
			case NONE:
				handleDecodeInternally(rawResult);
				break;
			}
		}
	}

	/**
	 * Superimpose a line for 1D or dots for 2D to highlight the key features of
	 * the barcode.
	 * 
	 * @param barcode
	 *            A bitmap of the captured image.
	 * @param rawResult
	 *            The decoded results which contains the points to draw.
	 */

	private void drawResultPoints(Bitmap barcode, Result rawResult) {
		ResultPoint[] points = rawResult.getResultPoints();
		if (points != null && points.length > 0) {
			Canvas canvas = new Canvas(barcode);
			Paint paint = new Paint();
			paint.setColor(BarcodeColor.RESULT_IMAGE_BORDER);
			paint.setStrokeWidth(3.0f);
			paint.setStyle(Paint.Style.STROKE);
			Rect border = new Rect(2, 2, barcode.getWidth() - 2,
					barcode.getHeight() - 2);
			canvas.drawRect(border, paint);

			paint.setColor(BarcodeColor.RESULT_POINTS);
			if (points.length == 2) {
				paint.setStrokeWidth(4.0f);
				canvas.drawLine(points[0].getX(), points[0].getY(),
						points[1].getX(), points[1].getY(), paint);
			} else {
				paint.setStrokeWidth(10.0f);
				for (ResultPoint point : points) {
					canvas.drawPoint(point.getX(), point.getY(), paint);
				}
			}

		}
	}

	private void handleDecodeInternally(final Result rawResult) {
		ResultHandler resultHandler = new ResultHandler(rawResult);
		CharSequence displayContents = resultHandler.getDisplayContents();
		Log.i(TAG, "Got return value: " + displayContents.toString());
		fireSuccessCallback(displayContents.toString());
	}

	private void fireSuccessCallback(String scanResult) {
		Intent intent = new Intent();
		intent.putExtra(EXTRA_RESULT, scanResult);
		tiBarcodeActivity.setResult(Activity.RESULT_OK, intent);
		if (DBG) {
            Log.d(TAG, "Set result, finish()");
        }
		tiBarcodeActivity.finish();
	}

	private void initCamera(final SurfaceHolder surfaceHolder) {
		try {
			CameraManager.get().openDriver(surfaceHolder);
		} catch (IOException ioe) {
			//Log.w(TAG, ioe); //Won't compile with this line
			displayFrameworkBugMessageAndExit();
			return;
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.e(TAG, e.toString());
			displayFrameworkBugMessageAndExit();
			return;
		}
		if (mHandler == null) {
			boolean beginScanning = lastResult == null;
			mHandler = new CaptureActivityHandler(this, decodeFormats,
					characterSet, beginScanning);
		}
	}

	private void displayFrameworkBugMessageAndExit() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(BarcodeString.APP_NAME);
		builder.setMessage(BarcodeString.MSG_CAMERA_FRAMEWORK_BUG);
		builder.setPositiveButton(BarcodeString.BUTTON_OK,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialogInterface, int i) {
						tiBarcodeActivity.finish();
					}
				});
		builder.show();
	}

	private void resetStatusView() {
		lastResult = null;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}

}
