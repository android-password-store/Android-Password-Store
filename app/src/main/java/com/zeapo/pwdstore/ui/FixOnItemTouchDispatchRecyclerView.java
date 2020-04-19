/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zeapo.pwdstore.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.zhanghai.android.fastscroll.FastScroller;
import me.zhanghai.android.fastscroll.PopupTextProvider;
import me.zhanghai.android.fastscroll.Predicate;
import me.zhanghai.android.fastscroll.ViewHelperProvider;

public class FixOnItemTouchDispatchRecyclerView extends RecyclerView implements ViewHelperProvider {

    @NonNull
    private final ViewHelper mViewHelper = new ViewHelper(this);

    @Nullable
    private OnItemTouchListener mPhantomOnItemTouchListener = null;
    private OnItemTouchListener mInterceptingOnItemTouchListener = null;

    public FixOnItemTouchDispatchRecyclerView(@NonNull Context context) {
        super(context);
    }

    public FixOnItemTouchDispatchRecyclerView(@NonNull Context context,
                                              @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FixOnItemTouchDispatchRecyclerView(@NonNull Context context,
                                              @Nullable AttributeSet attrs,
                                              @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @NonNull
    @Override
    public FastScroller.ViewHelper getViewHelper() {
        return mViewHelper;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        mInterceptingOnItemTouchListener = null;
        if (findInterceptingOnItemTouchListener(e)) {
            cancelScroll();
            return true;
        }
        return super.onInterceptTouchEvent(e);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (dispatchOnItemTouchListeners(e)) {
            cancelScroll();
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (mPhantomOnItemTouchListener != null) {
            mPhantomOnItemTouchListener.onRequestDisallowInterceptTouchEvent(disallowIntercept);
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    private void cancelScroll() {
        MotionEvent syntheticCancel = MotionEvent.obtain(
                0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        super.onInterceptTouchEvent(syntheticCancel);
        syntheticCancel.recycle();
    }

    private boolean dispatchOnItemTouchListeners(@NonNull MotionEvent e) {
        if (mInterceptingOnItemTouchListener == null) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                return false;
            }
            return findInterceptingOnItemTouchListener(e);
        } else {
            mInterceptingOnItemTouchListener.onTouchEvent(this, e);
            final int action = e.getAction();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                mInterceptingOnItemTouchListener = null;
            }
            return true;
        }
    }

    private boolean findInterceptingOnItemTouchListener(@NonNull MotionEvent e) {
        int action = e.getAction();
        if (mPhantomOnItemTouchListener != null
                && mPhantomOnItemTouchListener.onInterceptTouchEvent(this, e)
                && action != MotionEvent.ACTION_CANCEL) {
            mInterceptingOnItemTouchListener = mPhantomOnItemTouchListener;
            return true;
        }
        return false;
    }

    class RecyclerViewHelper implements FastScroller.ViewHelper {

        @NonNull
        private final RecyclerView mView;

        @NonNull
        private final Rect mTempRect = new Rect();

        public RecyclerViewHelper(@NonNull RecyclerView view) {
            mView = view;
        }

        @Override
        public void addOnPreDrawListener(@NonNull Runnable onPreDraw) {
            mView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
                    onPreDraw.run();
                }
            });
        }

        @Override
        public void addOnScrollChangedListener(@NonNull Runnable onScrollChanged) {
            mView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    onScrollChanged.run();
                }
            });
        }

        @Override
        public void addOnTouchEventListener(@NonNull Predicate<MotionEvent> onTouchEvent) {
            mView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView,
                                                     @NonNull MotionEvent event) {
                    return onTouchEvent.test(event);
                }
                @Override
                public void onTouchEvent(@NonNull RecyclerView recyclerView,
                                         @NonNull MotionEvent event) {
                    onTouchEvent.test(event);
                }
            });
        }

        @Override
        public int getScrollRange() {
            int itemCount = getItemCount();
            if (itemCount == 0) {
                return 0;
            }
            int itemHeight = getItemHeight();
            if (itemHeight == 0) {
                return 0;
            }
            return mView.getPaddingTop() + itemCount * itemHeight + mView.getPaddingBottom();
        }

        @Override
        public int getScrollOffset() {
            int firstItemPosition = getFirstItemPosition();
            if (firstItemPosition == RecyclerView.NO_POSITION) {
                return 0;
            }
            int itemHeight = getItemHeight();
            int firstItemTop = getFirstItemOffset();
            return mView.getPaddingTop() + firstItemPosition * itemHeight - firstItemTop;
        }

        @Override
        public void scrollTo(int offset) {
            // Stop any scroll in progress for RecyclerView.
            mView.stopScroll();
            offset -= mView.getPaddingTop();
            int itemHeight = getItemHeight();
            // firstItemPosition should be non-negative even if paddingTop is greater than item height.
            int firstItemPosition = Math.max(0, offset / itemHeight);
            int firstItemTop = firstItemPosition * itemHeight - offset;
            scrollToPositionWithOffset(firstItemPosition, firstItemTop);
        }

        @Nullable
        @Override
        public String getPopupText() {
            RecyclerView.Adapter<?> adapter = mView.getAdapter();
            if (!(adapter instanceof PopupTextProvider)) {
                return null;
            }
            PopupTextProvider popupTextProvider = (PopupTextProvider) adapter;
            int position = getFirstItemAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return null;
            }
            return popupTextProvider.getPopupText(position);
        }

        private int getItemCount() {
            LinearLayoutManager linearLayoutManager = getVerticalLinearLayoutManager();
            if (linearLayoutManager == null) {
                return 0;
            }
            int itemCount = linearLayoutManager.getItemCount();
            if (itemCount == 0) {
                return 0;
            }
            if (linearLayoutManager instanceof GridLayoutManager) {
                GridLayoutManager gridLayoutManager = (GridLayoutManager) linearLayoutManager;
                itemCount = (itemCount - 1) / gridLayoutManager.getSpanCount() + 1;
            }
            return itemCount;
        }

        private int getItemHeight() {
            if (mView.getChildCount() == 0) {
                return 0;
            }
            View itemView = mView.getChildAt(0);
            mView.getDecoratedBoundsWithMargins(itemView, mTempRect);
            return mTempRect.height();
        }

        private int getFirstItemPosition() {
            int position = getFirstItemAdapterPosition();
            LinearLayoutManager linearLayoutManager = getVerticalLinearLayoutManager();
            if (linearLayoutManager == null) {
                return RecyclerView.NO_POSITION;
            }
            if (linearLayoutManager instanceof GridLayoutManager) {
                GridLayoutManager gridLayoutManager = (GridLayoutManager) linearLayoutManager;
                position /= gridLayoutManager.getSpanCount();
            }
            return position;
        }

        private int getFirstItemAdapterPosition() {
            if (mView.getChildCount() == 0) {
                return RecyclerView.NO_POSITION;
            }
            View itemView = mView.getChildAt(0);
            LinearLayoutManager linearLayoutManager = getVerticalLinearLayoutManager();
            if (linearLayoutManager == null) {
                return RecyclerView.NO_POSITION;
            }
            return linearLayoutManager.getPosition(itemView);
        }

        private int getFirstItemOffset() {
            if (mView.getChildCount() == 0) {
                return RecyclerView.NO_POSITION;
            }
            View itemView = mView.getChildAt(0);
            mView.getDecoratedBoundsWithMargins(itemView, mTempRect);
            return mTempRect.top;
        }

        private void scrollToPositionWithOffset(int position, int offset) {
            LinearLayoutManager linearLayoutManager = getVerticalLinearLayoutManager();
            if (linearLayoutManager == null) {
                return;
            }
            if (linearLayoutManager instanceof GridLayoutManager) {
                GridLayoutManager gridLayoutManager = (GridLayoutManager) linearLayoutManager;
                position *= gridLayoutManager.getSpanCount();
            }
            // LinearLayoutManager actually takes offset from paddingTop instead of top of RecyclerView.
            offset -= mView.getPaddingTop();
            linearLayoutManager.scrollToPositionWithOffset(position, offset);
        }

        @Nullable
        private LinearLayoutManager getVerticalLinearLayoutManager() {
            RecyclerView.LayoutManager layoutManager = mView.getLayoutManager();
            if (!(layoutManager instanceof LinearLayoutManager)) {
                return null;
            }
            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
            if (linearLayoutManager.getOrientation() != RecyclerView.VERTICAL) {
                return null;
            }
            return linearLayoutManager;
        }
    }

    private class ViewHelper extends RecyclerViewHelper {

        ViewHelper(@NonNull RecyclerView view) {
            super(view);
        }

        @Override
        public void addOnTouchEventListener(@NonNull Predicate<MotionEvent> onTouchEvent) {
            mPhantomOnItemTouchListener = new RecyclerView.SimpleOnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView,
                                                     @NonNull MotionEvent event) {
                    return onTouchEvent.test(event);
                }

                @Override
                public void onTouchEvent(@NonNull RecyclerView recyclerView,
                                         @NonNull MotionEvent event) {
                    onTouchEvent.test(event);
                }
            };
        }
    }
}