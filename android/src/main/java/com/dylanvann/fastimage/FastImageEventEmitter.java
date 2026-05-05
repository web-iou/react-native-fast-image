package com.dylanvann.fastimage;

import android.view.View;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.UIManagerHelper;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.facebook.react.uimanager.events.RCTEventEmitter;

final class FastImageEventEmitter {
    private FastImageEventEmitter() {}

    static void dispatch(View view, String eventName, @Nullable WritableMap params) {
        if (view == null) {
            return;
        }
        ReactContext reactContext = UIManagerHelper.getReactContext(view);
        int viewId = view.getId();
        EventDispatcher eventDispatcher =
                UIManagerHelper.getEventDispatcherForReactTag(reactContext, viewId);
        if (eventDispatcher == null) {
            return;
        }
        final WritableMap payload = params != null ? params : new WritableNativeMap();
        eventDispatcher.dispatchEvent(
                new Event(viewId) {
                    @Override
                    public String getEventName() {
                        return eventName;
                    }

                    @Override
                    public boolean canCoalesce() {
                        return false;
                    }

                    @Override
                    public void dispatch(RCTEventEmitter rctEventEmitter) {
                        rctEventEmitter.receiveEvent(viewId, eventName, payload);
                    }
                });
    }
}
