// Copyright 2024 The Lynx Authors. All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package com.lynx.explorer.service.image.background;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.request.SuccessResult;
import com.lynx.tasm.behavior.ui.background.BackgroundLayerDrawable;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.launch;

/**
 * Coil 라이브러리를 사용하여 배경 이미지를 로드하는 BackgroundLayerDrawable 구현체
 */
public class CoilBackgroundLayerDrawable extends BackgroundLayerDrawable {
    private final Context mContext;
    private final String mUrl;
    private Drawable mImageDrawable;
    private boolean mIsLoading = false;
    private final CoroutineScope mCoroutineScope = new CoroutineScope(Dispatchers.getMain());
    private ImageLoader mImageLoader;

    public CoilBackgroundLayerDrawable(@NonNull Context context, @NonNull String url) {
        super();
        mContext = context;
        mUrl = url;
        loadImage();
    }

    private void initImageLoader() {
        if (mImageLoader == null) {
            mImageLoader = Coil.imageLoader(mContext);
        }
    }

    private void loadImage() {
        if (mIsLoading || mImageDrawable != null) {
            return;
        }

        mIsLoading = true;
        initImageLoader();

        ImageRequest request = new ImageRequest.Builder(mContext)
                .data(mUrl)
                .target(drawable -> {
                    mImageDrawable = drawable;
                    mIsLoading = false;
                    invalidateSelf();
                })
                .build();

        mCoroutineScope.launch(Dispatchers.Main) {
            try {
                mImageLoader.enqueue(request);
            } catch (Exception e) {
                mIsLoading = false;
            }
        };
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mImageDrawable != null) {
            mImageDrawable.setBounds(getBounds());
            mImageDrawable.draw(canvas);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return mImageDrawable != null ? mImageDrawable.getIntrinsicWidth() : -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return mImageDrawable != null ? mImageDrawable.getIntrinsicHeight() : -1;
    }

    @Override
    public void setAlpha(int alpha) {
        if (mImageDrawable != null) {
            mImageDrawable.setAlpha(alpha);
        }
    }

    @Override
    public int getOpacity() {
        return mImageDrawable != null ? mImageDrawable.getOpacity() : super.getOpacity();
    }

    @Override
    public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
        if (mImageDrawable != null) {
            mImageDrawable.setColorFilter(colorFilter);
        }
    }
}