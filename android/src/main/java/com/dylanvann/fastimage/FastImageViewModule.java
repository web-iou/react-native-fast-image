package com.dylanvann.fastimage;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.views.imagehelper.ImageSource;

import java.io.File;
import java.util.concurrent.CountDownLatch;

class FastImageViewModule extends ReactContextBaseJavaModule {

    private static final String REACT_CLASS = "FastImageView";

    FastImageViewModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @ReactMethod
    public void preload(final ReadableArray sources) {
        final Activity activity = getCurrentActivity();
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < sources.size(); i++) {
                    final ReadableMap source = sources.getMap(i);
                    final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);

                    Glide
                            .with(activity.getApplicationContext())
                            // This will make this work for remote and local images. e.g.
                            //    - file:///
                            //    - content://
                            //    - res:/
                            //    - android.resource://
                            //    - data:image/png;base64
                            .load(
                                    imageSource.isBase64Resource() ? imageSource.getSource() :
                                    imageSource.isResource() ? imageSource.getUri() : imageSource.getGlideUrl()
                            )
                            .apply(FastImageViewConverter.getOptions(activity, imageSource, source))
                            .preload();
                }
            }
        });
    }
    @ReactMethod
    public void getSize(
            final ReadableArray sources,
            final Promise promise
    ) {

        final Activity activity = getCurrentActivity();

        if (activity == null) {
            promise.reject("NO_ACTIVITY", "Activity is null");
            return;
        }

        WritableArray resultArray = Arguments.createArray();

        CountDownLatch latch = new CountDownLatch(sources.size());

        for (int i = 0; i < sources.size(); i++) {

            final int index = i;

            ReadableMap source = sources.getMap(i);

            final FastImageSource imageSource =
                    FastImageViewConverter.getImageSource(activity, source);

            Object model =
                    imageSource.isBase64Resource()
                            ? imageSource.getSource()
                            : imageSource.isResource()
                            ? imageSource.getUri()
                            : imageSource.getGlideUrl();

            Glide.with(activity.getApplicationContext())
                    .asFile()
                    .load(model)
                    .into(new CustomTarget<File>() {

                        @Override
                        public void onResourceReady(
                                @NonNull File resource,
                                @Nullable Transition<? super File> transition
                        ) {

                            BitmapFactory.Options options =
                                    new BitmapFactory.Options();

                            options.inJustDecodeBounds = true;

                            BitmapFactory.decodeFile(
                                    resource.getAbsolutePath(),
                                    options
                            );

                            WritableMap item = Arguments.createMap();

                            item.putInt("index", index);
                            item.putInt("width", options.outWidth);
                            item.putInt("height", options.outHeight);

                            synchronized (resultArray) {
                                resultArray.pushMap(item);
                            }

                            latch.countDown();
                        }

                        @Override
                        public void onLoadCleared(
                                @Nullable Drawable placeholder
                        ) {
                        }

                        @Override
                        public void onLoadFailed(
                                @Nullable Drawable errorDrawable
                        ) {

                            WritableMap item = Arguments.createMap();

                            item.putInt("index", index);
                            item.putInt("width", 0);
                            item.putInt("height", 0);

                            synchronized (resultArray) {
                                resultArray.pushMap(item);
                            }

                            latch.countDown();
                        }
                    });
        }

        new Thread(() -> {
            try {
                latch.await();
                promise.resolve(resultArray);
            } catch (InterruptedException e) {
                promise.reject("INTERRUPTED", e);
            }
        }).start();
    }
    @ReactMethod
    public void clearMemoryCache(final Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(null);
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Glide.get(activity.getApplicationContext()).clearMemory();
                promise.resolve(null);
            }
        });
    }

    @ReactMethod
    public void clearDiskCache(Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(null);
            return;
        }

        Glide.get(activity.getApplicationContext()).clearDiskCache();
        promise.resolve(null);
    }
}
