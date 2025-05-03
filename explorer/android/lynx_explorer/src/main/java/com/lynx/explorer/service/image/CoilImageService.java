// Copyright 2024 The Lynx Authors. All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package com.lynx.explorer.service.image;

import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import coil.Coil;
import coil.ImageLoader;
import coil.decode.GifDecoder;
import coil.decode.ImageDecoderDecoder;
import coil.decode.SvgDecoder;
import coil.request.ImageRequest;
import coil.request.SuccessResult;
import coil.size.Size;
import coil.transform.Transformation;
import com.lynx.explorer.service.image.background.CoilBackgroundLayerDrawable;
import com.lynx.react.bridge.ReadableMap;
import com.lynx.tasm.LynxEnv;
import com.lynx.tasm.LynxSubErrorCode;
import com.lynx.tasm.behavior.Behavior;
import com.lynx.tasm.behavior.LynxContext;
import com.lynx.tasm.behavior.shadow.ShadowNode;
import com.lynx.tasm.behavior.ui.LynxFlattenUI;
import com.lynx.tasm.behavior.ui.LynxUI;
import com.lynx.tasm.behavior.ui.background.BackgroundLayerDrawable;
import com.lynx.tasm.behavior.ui.image.BackgroundImageDrawable;
import com.lynx.tasm.behavior.ui.image.FlattenUIImage;
import com.lynx.tasm.behavior.ui.image.InlineImageShadowNode;
import com.lynx.tasm.behavior.ui.image.UIImage;
import com.lynx.tasm.image.AutoSizeImage;
import com.lynx.tasm.image.model.AnimationListener;
import com.lynx.tasm.image.model.ImageInfo;
import com.lynx.tasm.image.model.ImageLoadListener;
import com.lynx.tasm.image.model.ImageRequestInfo;
import com.lynx.tasm.service.ILynxImageService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.launch;

@Keep
public class CoilImageService implements ILynxImageService {
    private static final String TAG = "CoilImageService";
    
    private volatile static CoilImageService sInstance = null;
    private final Map<String, Drawable> mDrawableCache = new ConcurrentHashMap<>();
    private final CoroutineScope mCoroutineScope = new CoroutineScope(Dispatchers.getMain());
    private ImageLoader mImageLoader;

    private CoilImageService() {
        super();
        List<Behavior> behaviorList = new ArrayList<>();
        behaviorList.add(new Behavior("image", true, true) {
            @Override
            public LynxUI createUI(LynxContext context) {
                return new UIImage(context);
            }

            @Override
            public LynxFlattenUI createFlattenUI(final LynxContext context) {
                return new FlattenUIImage(context);
            }
            
            @Override
            public ShadowNode createShadowNode() {
                return new AutoSizeImage();
            }
        });
        behaviorList.add(new Behavior("inline-image", false, true) {
            @Override
            public ShadowNode createShadowNode() {
                return new InlineImageShadowNode();
            }
        });
        LynxEnv.inst().addBehaviors(behaviorList);
    }

    public static CoilImageService getInstance() {
        if (sInstance == null) {
            synchronized (CoilImageService.class) {
                if (sInstance == null) {
                    sInstance = new CoilImageService();
                }
            }
        }
        return sInstance;
    }

    private void initImageLoader(Context context) {
        if (mImageLoader == null) {
            mImageLoader = new ImageLoader.Builder(context)
                    .components(builder -> {
                        builder.add(new GifDecoder());
                        builder.add(new SvgDecoder());
                        builder.add(new ImageDecoderDecoder());
                    })
                    .build();
        }
    }

    @Override
    public void fetchImage(@NonNull ImageRequestInfo imageRequestInfo,
            @NonNull ImageLoadListener loadListener, @Nullable AnimationListener animationListener,
            @NonNull Context context) {
        
        initImageLoader(context);
        
        String url = imageRequestInfo.getUrl();
        
        // 이미 캐시된 이미지가 있는지 확인
        if (mDrawableCache.containsKey(url)) {
            Drawable cachedDrawable = mDrawableCache.get(url);
            if (cachedDrawable != null) {
                boolean isAnim = cachedDrawable instanceof Animatable;
                if (isAnim && animationListener != null) {
                    setupAnimationListener((Animatable) cachedDrawable, animationListener);
                }
                
                // 애니메이션 자동 재생 설정
                if (isAnim && imageRequestInfo.isAutoPlay()) {
                    ((Animatable) cachedDrawable).start();
                }
                
                loadListener.onSuccess(
                        cachedDrawable, 
                        imageRequestInfo, 
                        new ImageInfo(
                                cachedDrawable.getIntrinsicWidth(), 
                                cachedDrawable.getIntrinsicHeight(), 
                                isAnim));
                return;
            }
        }
        
        // Coil 이미지 요청 생성
        ImageRequest.Builder requestBuilder = new ImageRequest.Builder(context)
                .data(url)
                .listener(new coil.request.Listener() {
                    @Override
                    public void onStart(@NonNull ImageRequest request) {
                        // 이미지 로딩 시작 시 처리할 내용이 있다면 여기에 구현
                    }

                    @Override
                    public void onCancel(@NonNull ImageRequest request) {
                        // 이미지 로딩 취소 시 처리할 내용이 있다면 여기에 구현
                    }

                    @Override
                    public void onError(@NonNull ImageRequest request, @NonNull Throwable throwable) {
                        loadListener.onFailure(LynxSubErrorCode.E_RESOURCE_IMAGE_PIC_SOURCE, throwable);
                    }

                    @Override
                    public void onSuccess(@NonNull ImageRequest request, @NonNull coil.request.SuccessResult result) {
                        // 이미지 로딩 성공 시 처리할 내용이 있다면 여기에 구현
                    }
                });
        
        // 리사이즈 옵션 설정
        if (imageRequestInfo.isEnableDownSampling() && !imageRequestInfo.isEnableResourceHint()) {
            requestBuilder.size(new Size(imageRequestInfo.getResizeWidth(), imageRequestInfo.getResizeHeight()));
        }
        
        // 이미지 요청 실행
        mCoroutineScope.launch(Dispatchers.Main) {
            try {
                coil.request.ImageRequest request = requestBuilder.build();
                Object result = mImageLoader.execute(request);
                
                if (result instanceof SuccessResult) {
                    Drawable drawable = ((SuccessResult) result).getDrawable();
                    boolean isAnim = drawable instanceof Animatable;
                    
                    // 캐시에 저장
                    mDrawableCache.put(url, drawable);
                    
                    // 애니메이션 리스너 설정
                    if (isAnim && animationListener != null) {
                        setupAnimationListener((Animatable) drawable, animationListener);
                    }
                    
                    // 애니메이션 자동 재생 설정
                    if (isAnim && imageRequestInfo.isAutoPlay()) {
                        ((Animatable) drawable).start();
                    }
                    
                    loadListener.onSuccess(
                            drawable, 
                            imageRequestInfo, 
                            new ImageInfo(
                                    drawable.getIntrinsicWidth(), 
                                    drawable.getIntrinsicHeight(), 
                                    isAnim));
                } else {
                    loadListener.onFailure(LynxSubErrorCode.E_RESOURCE_IMAGE_PIC_SOURCE, 
                            new Exception("Failed to load image"));
                }
            } catch (Exception e) {
                loadListener.onFailure(LynxSubErrorCode.E_RESOURCE_IMAGE_PIC_SOURCE, e);
            }
        };
    }
    
    private void setupAnimationListener(Animatable animatable, AnimationListener listener) {
        // Coil의 Animatable은 애니메이션 리스너를 직접 지원하지 않으므로
        // 여기서는 간단한 구현만 제공합니다.
        // 실제 구현에서는 더 복잡한 로직이 필요할 수 있습니다.
        listener.onAnimationStart(animatable);
    }

    @Override
    public boolean startAnimation(@NonNull Drawable animatable) {
        if (animatable instanceof Animatable) {
            ((Animatable) animatable).stop();
            ((Animatable) animatable).start();
            return true;
        }
        return false;
    }

    @Override
    public boolean resumeAnimation(@NonNull Drawable animatable) {
        if (animatable instanceof Animatable) {
            ((Animatable) animatable).start();
            return true;
        }
        return false;
    }

    @Override
    public boolean pauseAnimation(@NonNull Drawable animatable) {
        if (animatable instanceof Animatable) {
            ((Animatable) animatable).stop();
            return true;
        }
        return false;
    }

    @Override
    public boolean stopAnimation(@NonNull Drawable animatable) {
        if (animatable instanceof Animatable) {
            ((Animatable) animatable).stop();
            return true;
        }
        return false;
    }

    @Override
    public void prefetchImage(
            @NonNull String uri, @Nullable Object callerContext, @Nullable ReadableMap params) {
        Context context = LynxEnv.inst().getContext();
        if (context == null) return;
        
        initImageLoader(context);
        
        // Coil 이미지 프리페치
        ImageRequest request = new ImageRequest.Builder(context)
                .data(uri)
                .build();
                
        mCoroutineScope.launch(Dispatchers.IO) {
            mImageLoader.enqueue(request);
        };
    }

    @Override
    public void releaseImage(@NonNull ImageRequestInfo imageRequestInfo) {
        if (imageRequestInfo != null) {
            mDrawableCache.remove(imageRequestInfo.getUrl());
        }
    }

    @Override
    public void releaseAnimDrawable(@NonNull Drawable drawable) {
        // Coil은 Drawable 리소스를 자동으로 관리하므로 별도의 처리가 필요 없습니다.
    }

    @Override
    public @Nullable BackgroundLayerDrawable createBackgroundImageDrawable(
            @NonNull Context context, @NonNull String url) {
        return new CoilBackgroundLayerDrawable(context, url);
    }

    @Deprecated
    @Override
    public void setCustomImageDecoder(@NonNull Object builder) {
        // 사용되지 않는 메서드
    }

    @Deprecated
    @Override
    public @Nullable Object getImageSRPostProcessor() {
        return null;
    }

    @Deprecated
    @Override
    public void setImageSRSize(@NonNull Object request, @NonNull View view) {
        // 사용되지 않는 메서드
    }

    @Deprecated
    @Override
    public void setImageCacheChoice(@NonNull String cacheChoice, @NonNull Object builder) {
        // 사용되지 않는 메서드
    }

    @Deprecated
    @Override
    public void setImagePlaceHolderHash(@NonNull Object hierarchy, @NonNull Object request,
            @NonNull Object scaleType, @NonNull String hash, @Nullable String metaData, int width,
            int height, int radius, int iterations, boolean isPreView) {
        // 사용되지 않는 메서드
    }
}