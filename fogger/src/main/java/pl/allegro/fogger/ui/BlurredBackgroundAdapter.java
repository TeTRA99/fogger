/*
 * Copyright 2014 the original author or authors.
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

package pl.allegro.fogger.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import pl.allegro.fogger.FoggerConfig;
import pl.allegro.fogger.utils.ScreenShooter;
import pl.allegro.fogger.utils.TaskRunnerImpl;
import pl.allegro.fogger.blur.BlurringImageTask;
import pl.allegro.fogger.blur.BlurringImageTaskFactory;
import pl.allegro.fogger.utils.ImageUtils;
import pl.allegro.fogger.utils.TaskRunner;

public class BlurredBackgroundAdapter implements BlurringImageTask.BlurringImageListener {

    private static final String TAG = BlurredBackgroundAdapter.class.getName();

    protected static enum BlurredBackgroundAdapterState {
        WORKING,
        RESETTED,
        IDLE,
        READY
    }

    private static BlurredBackgroundAdapter instance;

    protected static String internalFilesDirPath;
    protected ImageUtils imageUtils;
    protected BlurringImageTaskFactory blurringImageTaskFactory;
    protected ScreenShooter screenShooter;

    protected BlurredBackgroundAdapterState state = BlurredBackgroundAdapterState.IDLE;
    private BlurringImageTask.BlurringImageListener blurringImageListener;
    private Bitmap blurredImage;

    public static synchronized BlurredBackgroundAdapter getInstance(Application application) {
        if (instance == null) {
            instance = new BlurredBackgroundAdapter();
        }
        instance.internalFilesDirPath = application.getFilesDir().getPath();
        return instance;
    }

    private BlurredBackgroundAdapter() {
        imageUtils = new ImageUtils();
        blurringImageTaskFactory = new BlurringImageTaskFactory();
        screenShooter = new ScreenShooter();
    }

    public synchronized void prepareBlurredBackgroundForActivity(Activity activity) {
        prepareAdapterToStartBlurringBackgroundProcess();
        Bitmap appScreenshot = screenShooter.createScreenShot(activity);
        runBlurringTask(activity, appScreenshot);
    }

    public synchronized void prepareBlurredBackgroundForView(Context context, View viewToBlur) {
        prepareAdapterToStartBlurringBackgroundProcess();
        Bitmap appScreenshot = screenShooter.createScreenShot(viewToBlur);
        runBlurringTask(context, appScreenshot);
    }

    @Override
    public synchronized void onBlurringFinish(Bitmap blurredImage) {
        if (state == BlurredBackgroundAdapterState.RESETTED) {
            Log.i(TAG, "BlurringAdapter was reseted, so I recycle created bitmap and reset BlurringAdapterState.");
            blurredImage.recycle();
            blurringImageListener = null;
        }
        state = BlurredBackgroundAdapterState.READY;
        this.blurredImage = blurredImage;
        if(blurringImageListener != null) {
            blurringImageListener.onBlurringFinish(blurredImage);
        }
    }

    public synchronized void reset() {
        leaveCurrentBlurredImage();
        blurringImageListener = null;
        state = BlurredBackgroundAdapterState.RESETTED;
    }

    public synchronized void resetBlurringListener(BlurringImageTask.BlurringImageListener listener) {
        if (state == BlurredBackgroundAdapterState.WORKING) {
            blurringImageListener = listener;
        } else if (state == BlurredBackgroundAdapterState.READY && listener != null && blurredImage != null) {
            listener.onBlurringFinish(blurredImage);
        } else if (state != BlurredBackgroundAdapterState.RESETTED && listener != null) {
            Log.w(TAG, "Something was wrong. There isn't any ready blurred background"
                    + " Thus I try to restore some from internal storage.");
            leaveCurrentBlurredImage();
            blurredImage = imageUtils.createBitmapFromFile(internalFilesDirPath
                                                            + FoggerConfig.BLURRED_SCREENSHOT_FILE_NAME);
            listener.onBlurringFinish(blurredImage);
        }
    }

    private synchronized void prepareAdapterToStartBlurringBackgroundProcess() {
        if (state == BlurredBackgroundAdapterState.WORKING) {
            throw new IllegalStateException("BlurredBackgroundAdapter already working, "
                    + "it can not handle more then one pl.allegro.fogger.pl.blur request.");
        }
        state = BlurredBackgroundAdapterState.WORKING;
        leaveCurrentBlurredImage();
    }

    private void runBlurringTask(Context activity, Bitmap appScreenshot) {
        BlurringImageTask blurringImageTask = blurringImageTaskFactory.create(activity, this, appScreenshot);
        TaskRunner taskRunner = new TaskRunnerImpl();
        taskRunner.execute(blurringImageTask);
    }

    private void leaveCurrentBlurredImage() {
        if (blurredImage != null) {
            blurredImage.recycle();
            blurredImage = null;
        }
    }
}