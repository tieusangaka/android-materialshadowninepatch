/*
 *    Copyright (C) 2015 Haruki Hasegawa
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.h6ah4i.android.materialshadowninepatch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

public class MaterialShadowContainerView extends FrameLayout {
    private static final String TAG = "ShadowContainerView";

    private static final float SPOT_SHADOW_X_TRANSLATION_AMOUNT_COEFFICIENT = 0.0002f;
    private static final float SPOT_SHADOW_Y_TRANSLATION_AMOUNT_COEFFICIENT = 0.002f;

    private float mDisplayDensity;
    private float mInvDisplayDensity;
    private int mLightPositionX;
    private int mLightPositionY;
    private int mSpotShadowTranslationX;
    private int mSpotShadowTranslationY;
    private float mShadowTranslationZ = 0;
    private float mShadowElevation = 0;

    private boolean mForceUseCompatShadow = false;

    private int[] mSpotShadowResourcesIdList;
    private int[] mAmbientShadowResourcesIdList;

    private int mMaxSpotShadowLevel;
    private int mMaxAmbientShadowLevel;

    private int mCurrentSpotShadowDrawable1ResId;
    private NinePatchDrawable mCurrentSpotShadowDrawable1;
    private int mCurrentSpotShadowDrawable2ResId;
    private NinePatchDrawable mCurrentSpotShadowDrawable2;
    private int mCurrentAmbientShadowDrawable1ResId;
    private NinePatchDrawable mCurrentAmbientShadowDrawable1;
    private int mCurrentAmbientShadowDrawable2ResId;
    private NinePatchDrawable mCurrentAmbientShadowDrawable2;

    private Rect mTempRect = new Rect();
    private int[] mTmpLocations = new int[2];

    public MaterialShadowContainerView(Context context) {
        this(context, null, 0);
    }

    public MaterialShadowContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaterialShadowContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MaterialShadowContainerView, 0, 0);
        final float shadowTranslationZ = ta.getDimension(R.styleable.MaterialShadowContainerView_shadowTranslationZ, mShadowTranslationZ);
        final float shadowElevation = ta.getDimension(R.styleable.MaterialShadowContainerView_shadowElevation, mShadowElevation);
        final int spotShadowLevelListResId = ta.getResourceId(R.styleable.MaterialShadowContainerView_spotShadowDrawablesList, R.array.ms9_spot_shadow_drawables);
        final int ambientShadowLevelListResId = ta.getResourceId(R.styleable.MaterialShadowContainerView_ambientShadowDrawablesList, R.array.ms9_ambient_shadow_drawables);
        final boolean forceUseCompatShadow = ta.getBoolean(R.styleable.MaterialShadowContainerView_forceUseCompatShadow, mForceUseCompatShadow);
        ta.recycle();

        mSpotShadowResourcesIdList = getResourceIdArray(getResources(), spotShadowLevelListResId);
        mAmbientShadowResourcesIdList = getResourceIdArray(getResources(), ambientShadowLevelListResId);

        mMaxSpotShadowLevel = getMaxShadowLevel(mSpotShadowResourcesIdList);
        mMaxAmbientShadowLevel = getMaxShadowLevel(mAmbientShadowResourcesIdList);

        mDisplayDensity = getResources().getDisplayMetrics().density;
        mInvDisplayDensity = 1.0f / mDisplayDensity;
        mShadowTranslationZ = shadowTranslationZ;
        mShadowElevation = shadowElevation;
        mForceUseCompatShadow = forceUseCompatShadow;

        updateShadowLevel(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if ((getChildCount() > 0) && (getChildAt(0).getVisibility() == View.VISIBLE)) {
            if (mCurrentAmbientShadowDrawable1 != null) {
                mCurrentAmbientShadowDrawable1.draw(canvas);
            }
            if (mCurrentAmbientShadowDrawable2 != null) {
                mCurrentAmbientShadowDrawable2.draw(canvas);
            }

            if (mCurrentSpotShadowDrawable1 != null || mCurrentSpotShadowDrawable2 != null) {
                final int savedCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);

                canvas.translate(mSpotShadowTranslationX, mSpotShadowTranslationY);

                if (mCurrentSpotShadowDrawable1 != null) {
                    mCurrentSpotShadowDrawable1.draw(canvas);
                }

                if (mCurrentSpotShadowDrawable2 != null) {
                    mCurrentSpotShadowDrawable2.draw(canvas);
                }

                canvas.restoreToCount(savedCount);
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        updateShadowDrawableBounds();
        updateSpotShadowPosition();
    }

    public void setShadowTranslationZ(float translationZ) {
        if (mShadowTranslationZ == translationZ) {
            return;
        }

        mShadowTranslationZ = translationZ;

        updateShadowLevel(false);
    }

    public float getShadowTranslationZ() {
        return mShadowTranslationZ;
    }

    public void setShadowElevation(float elevation) {
        if (mShadowElevation == elevation) {
            return;
        }

        mShadowElevation = elevation;

        updateShadowLevel(false);
    }

    public float getShadowElevation() {
        return mShadowElevation;
    }

    public void setForceUseCompatShadow(boolean forceUseCompatShadow) {
        if (mForceUseCompatShadow == forceUseCompatShadow) {
            return;
        }

        final boolean prevUseCompatShadow = useCompatShadow();

        mForceUseCompatShadow = forceUseCompatShadow;

        final boolean curUseCompatShadow = useCompatShadow();

        if (prevUseCompatShadow != curUseCompatShadow) {
            // disable native shadow
            if (curUseCompatShadow && supportsNativeShadow()) {
                updateShadowLevelNative(0.0f, 0.0f, true);
            }

            // apply
            updateShadowLevel(true);
        }
    }

    public boolean useCompatShadow() {
        if (!supportsNativeShadow()) {
            return true;
        } else {
            return mForceUseCompatShadow;
        }
    }

    public static boolean supportsNativeShadow() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
    }

    private static int getMaxShadowLevel(int[] shadowDrawableResIds) {
        return (shadowDrawableResIds != null) ? Math.max(0, shadowDrawableResIds.length - 1) : 0;
    }

    private NinePatchDrawable getNinePatchDrawableFromResource(int resId) {
        final Drawable drawable = (resId != 0) ? getResources().getDrawable(resId) : null;

        if (drawable instanceof NinePatchDrawable) {
            return (NinePatchDrawable) drawable;
        } else {
            return null;
        }
    }

    private void updateShadowLevelCompat(float translationZ, float elevation, boolean force) {
        final float floatLevel = Math.max((translationZ + elevation) * mInvDisplayDensity, 0.0f);
        final int intLevel = (int) floatLevel;
        final int spotLevel1 = Math.min(intLevel, mMaxSpotShadowLevel);
        final int spotLevel2 = Math.min(intLevel + 1, mMaxSpotShadowLevel);
        final int ambientLevel1 = Math.min(intLevel, mMaxAmbientShadowLevel);
        final int ambientLevel2 = Math.min(intLevel + 1, mMaxAmbientShadowLevel);

        // update drawable
        final int spotShadow1ResId = (mSpotShadowResourcesIdList != null) ? mSpotShadowResourcesIdList[spotLevel1] : 0;
        final int spotShadow2ResId = (mSpotShadowResourcesIdList != null) ? mSpotShadowResourcesIdList[spotLevel2] : 0;
        final int ambientShadow1ResId = (mAmbientShadowResourcesIdList != null) ? mAmbientShadowResourcesIdList[ambientLevel1] : 0;
        final int ambientShadow2ResId = (mAmbientShadowResourcesIdList != null) ? mAmbientShadowResourcesIdList[ambientLevel2] : 0;

        if (force ||
                spotShadow1ResId != mCurrentSpotShadowDrawable1ResId ||
                spotShadow2ResId != mCurrentSpotShadowDrawable2ResId ||
                ambientShadow1ResId != mCurrentAmbientShadowDrawable1ResId ||
                ambientShadow2ResId != mCurrentAmbientShadowDrawable2ResId) {

            if (spotShadow1ResId != mCurrentSpotShadowDrawable1ResId) {
                mCurrentSpotShadowDrawable1 = getNinePatchDrawableFromResource(spotShadow1ResId);
                mCurrentSpotShadowDrawable1ResId = spotShadow1ResId;
            }

            if (spotShadow2ResId != mCurrentSpotShadowDrawable2ResId) {
                mCurrentSpotShadowDrawable2 = (spotShadow2ResId == spotShadow1ResId) ? null : getNinePatchDrawableFromResource(spotShadow2ResId);
                mCurrentSpotShadowDrawable2ResId = (spotShadow2ResId == spotShadow1ResId) ? 0 : spotShadow2ResId;
            }

            if (ambientShadow1ResId != mCurrentAmbientShadowDrawable1ResId) {
                mCurrentAmbientShadowDrawable1 = getNinePatchDrawableFromResource(ambientShadow1ResId);
                mCurrentAmbientShadowDrawable1ResId = ambientShadow1ResId;
            }

            if (ambientShadow2ResId != mCurrentAmbientShadowDrawable2ResId) {
                mCurrentAmbientShadowDrawable2 = (ambientShadow2ResId == ambientShadow1ResId) ? null : getNinePatchDrawableFromResource(ambientShadow2ResId);
                mCurrentAmbientShadowDrawable2ResId = (ambientShadow2ResId == ambientShadow1ResId) ? 0 : ambientShadow2ResId;
            }
            updateShadowDrawableBounds();
            updateSpotShadowPosition();

            updateWillNotDraw();
        }

        // update alpha
        final int alpha1 = 255 - Math.min(Math.max((int) ((floatLevel - intLevel) * 255 + 0.5f), 0), 255);
        final int alpha2 = 255 - alpha1;

        if (mCurrentSpotShadowDrawable1 != null) {
            if (mCurrentSpotShadowDrawable2 != null) {
                mCurrentSpotShadowDrawable1.setAlpha(alpha1);
            } else {
                mCurrentSpotShadowDrawable1.setAlpha(255);
            }
        }

        if (mCurrentSpotShadowDrawable2 != null) {
            mCurrentSpotShadowDrawable2.setAlpha(alpha2);
        }

        if (mCurrentAmbientShadowDrawable1 != null) {
            if (mCurrentAmbientShadowDrawable2 != null) {
                mCurrentAmbientShadowDrawable1.setAlpha(alpha1);
            } else {
                mCurrentAmbientShadowDrawable1.setAlpha(255);
            }
        }

        if (mCurrentAmbientShadowDrawable2 != null) {
            mCurrentAmbientShadowDrawable2.setAlpha(alpha2);
        }

        // invalidate
        if (!willNotDraw()) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private void updateShadowLevel(boolean force) {
        if (useCompatShadow()) {
            updateShadowLevelCompat(mShadowTranslationZ, mShadowElevation, force);
        } else {
            updateShadowLevelNative(mShadowTranslationZ, mShadowElevation, force);
        }
    }

    private void updateShadowLevelNative(float translationZ, float elevation, boolean force) {
        if (force) {
            mCurrentSpotShadowDrawable1 = null;
            mCurrentSpotShadowDrawable1ResId = 0;
            mCurrentSpotShadowDrawable2 = null;
            mCurrentSpotShadowDrawable2ResId = 0;
            mCurrentAmbientShadowDrawable1 = null;
            mCurrentAmbientShadowDrawable1ResId = 0;
            mCurrentAmbientShadowDrawable2 = null;
            mCurrentAmbientShadowDrawable2ResId = 0;
            updateWillNotDraw();
        }

        final View childView = (getChildCount() > 0) ? getChildAt(0) : null;

        if (childView != null) {
            ViewCompat.setTranslationZ(childView, translationZ);
            ViewCompat.setElevation(childView, elevation);
        }
    }

    private boolean updateWillNotDraw() {
        boolean willNotDraw =
                mCurrentSpotShadowDrawable1 == null &&
                        mCurrentSpotShadowDrawable2 == null &&
                        mCurrentAmbientShadowDrawable1 == null &&
                        mCurrentAmbientShadowDrawable2 == null &&
                        getBackground() == null &&
                        getForeground() == null;
        setWillNotDraw(willNotDraw);

        return willNotDraw;
    }

    private void updateShadowDrawableBounds() {
        if (getChildCount() <= 0) {
            return;
        }

        final View childView = getChildAt(0);

        final int childLeft = childView.getLeft();
        final int childTop = childView.getTop();
        final int childRight = childView.getRight();
        final int childBottom = childView.getBottom();

        updateNinePatchBounds(mCurrentSpotShadowDrawable1, childLeft, childTop, childRight, childBottom);
        if (mCurrentAmbientShadowDrawable1 != mCurrentSpotShadowDrawable2) {
            updateNinePatchBounds(mCurrentSpotShadowDrawable2, childLeft, childTop, childRight, childBottom);
        }

        updateNinePatchBounds(mCurrentAmbientShadowDrawable1, childLeft, childTop, childRight, childBottom);
        if (mCurrentAmbientShadowDrawable1 != mCurrentAmbientShadowDrawable2) {
            updateNinePatchBounds(mCurrentAmbientShadowDrawable2, childLeft, childTop, childRight, childBottom);
        }
    }

    private void updateNinePatchBounds(NinePatchDrawable ninePatch, int childLeft, int childTop, int childRight, int childBottom) {
        if (ninePatch == null) {
            return;
        }

        final Rect t = mTempRect;
        ninePatch.getPadding(t);
        ninePatch.setBounds(
                childLeft - t.left, childTop - t.top,
                childRight + t.right, childBottom + t.bottom);
    }

    private void updateSpotShadowPosition() {
        if (getChildCount() < 1) {
            return;
        }

        final View childView = getChildAt(0);

        childView.getWindowVisibleDisplayFrame(mTempRect);

        mLightPositionX = mTempRect.width() / 2;
        mLightPositionY = 0;

        childView.getLocationInWindow(mTmpLocations);

        final float zPosition = (mShadowTranslationZ + mShadowElevation);
        final float tx = ViewCompat.getTranslationX(childView);
        final float ty = ViewCompat.getTranslationY(childView);

        final int childWidth = childView.getWidth();
        final int childHeight = childView.getHeight();

        final int childCenterPosX = mTmpLocations[0] + (childWidth / 2);
        final int childCenterPosY = mTmpLocations[1] + (childHeight / 2);

        final float positionRelatedTranslationX = (float) Math.sqrt((childCenterPosX - mLightPositionX) * mInvDisplayDensity * SPOT_SHADOW_X_TRANSLATION_AMOUNT_COEFFICIENT) * zPosition;
        final float positionRelatedTranslationY =  (float) Math.sqrt((childCenterPosY - mLightPositionY) * mInvDisplayDensity * SPOT_SHADOW_Y_TRANSLATION_AMOUNT_COEFFICIENT) * zPosition;

        mSpotShadowTranslationX = (int) (positionRelatedTranslationX + tx + 0.5f);
        mSpotShadowTranslationY = (int) (positionRelatedTranslationY + ty + 0.5f);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        updateShadowDrawableBounds();
        updateSpotShadowPosition();

        if (requiresChildViewLayoutFix()) {
            fixChildViewGravity();
        }

        if (!useCompatShadow()) {
            updateShadowLevelNative(mShadowTranslationZ, mShadowElevation, true);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (requiresChildViewLayoutFix()) {
            onMeasureCompat(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private boolean requiresChildViewLayoutFix() {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) && (!isInEditMode());
    }

    @SuppressLint("RtlHardcoded")
    private void fixChildViewGravity() {
        for (int i = 0; i < getChildCount(); i++) {
            View childView = getChildAt(i);
            LayoutParams params = (LayoutParams) childView.getLayoutParams();

            if (params.gravity == -1) {
                params.gravity = Gravity.TOP | Gravity.LEFT;
            }

            childView.setLayoutParams(params);
        }
    }

    private void onMeasureCompat(int widthMeasureSpec, int heightMeasureSpec) {
        int count = Math.min(1, getChildCount());

        final boolean measureMatchParentChildren =
                MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY ||
                        MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY;

        View matchParentChildren = null;

        int maxHeight = 0;
        int maxWidth = 0;
        int childState = 0;

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);

                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                maxWidth = Math.max(maxWidth,
                        child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
                maxHeight = Math.max(maxHeight,
                        child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);

                childState |= ViewCompat.getMeasuredState(child);

                if (measureMatchParentChildren) {
                    if (lp.width == LayoutParams.MATCH_PARENT ||
                            lp.height == LayoutParams.MATCH_PARENT) {
                        matchParentChildren = child;
                    }
                }
            }
        }

        final int paddingH = getPaddingLeft() + getPaddingRight();
        final int paddingV = getPaddingTop() + getPaddingBottom();

        maxWidth += paddingH;
        maxHeight += paddingV;

        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        final Drawable drawable = getForeground();
        if (drawable != null) {
            maxHeight = Math.max(maxHeight, drawable.getMinimumHeight());
            maxWidth = Math.max(maxWidth, drawable.getMinimumWidth());
        }

        setMeasuredDimension(
                ViewCompat.resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                ViewCompat.resolveSizeAndState(maxHeight, heightMeasureSpec,
                        childState << ViewCompat.MEASURED_HEIGHT_STATE_SHIFT));

        if (matchParentChildren != null) {
            final View child = matchParentChildren;

            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int childWidthMeasureSpec;
            int childHeightMeasureSpec;

            if (lp.width == LayoutParams.MATCH_PARENT) {
                childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                        getMeasuredWidth() - paddingH - lp.leftMargin - lp.rightMargin,
                        MeasureSpec.EXACTLY);
            } else {
                childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                        paddingH + lp.leftMargin + lp.rightMargin,
                        lp.width);
            }

            if (lp.height == LayoutParams.MATCH_PARENT) {
                childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                        getMeasuredHeight() - paddingV - lp.topMargin - lp.bottomMargin,
                        MeasureSpec.EXACTLY);
            } else {
                childHeightMeasureSpec = getChildMeasureSpec(
                        heightMeasureSpec,
                        paddingV + lp.topMargin + lp.bottomMargin,
                        lp.height);
            }

            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }

    private int[] getResourceIdArray(Resources resources, int id) {
        if (isInEditMode()) {
            return null;
        }

        TypedArray ta = resources.obtainTypedArray(id);
        int[] array = new int[ta.length()];

        for (int i = 0; i < array.length; i++) {
            array[i] = ta.getResourceId(i, 0);
        }

        ta.recycle();

        return array;
    }
}
