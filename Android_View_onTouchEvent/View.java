package android.view;

@UiThread
public class View implements Drawable.Callback, KeyEvent.Callback,
        AccessibilityEventSource {

    // ... Line: 15639

    /**
     * Implement this method to handle touch screen motion events.
     * <p>
     * If this method is used to detect click actions, it is recommended that
     * the actions be performed by implementing and calling
     * {@link #performClick()}. This will ensure consistent system behavior,
     * including:
     * <ul>
     * <li>obeying click sound preferences
     * <li>dispatching OnClickListener calls
     * <li>handling {@link AccessibilityNodeInfo#ACTION_CLICK ACTION_CLICK} when
     * accessibility features are enabled
     * </ul>
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise.
     */
    public boolean onTouchEvent(MotionEvent event) {
        // 獲取基本訊息，如座標、事件的屬性…等。
        final float x = event.getX();
        final float y = event.getY();
        final int viewFlags = mViewFlags;
        final int action = event.getAction(); // 使用 getAction() 代表不支援多點觸控；但建議以 getActionMasked() 取代。

        /* 
         * 判斷是否為可以點擊的 View。
         * 
         * - CLICKABLE：點擊
         * - LONG_CLICKABLE：長按
         * - CONTEXT_CLICKABLE：長按選單
         */
        final boolean clickable = ((viewFlags & CLICKABLE) == CLICKABLE
                || (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE)
                || (viewFlags & CONTEXT_CLICKABLE) == CONTEXT_CLICKABLE;

        /*
         * 判斷是否為禁用（使之失效），而非透明化。。
         */
        if ((viewFlags & ENABLED_MASK) == DISABLED) {
            /* 
             * 預防機制，若原本為已經按下的情況，也就是不會觸發 ACTION_DOWN 的情況下，將設置為 false。
             */
            if (action == MotionEvent.ACTION_UP && (mPrivateFlags & PFLAG_PRESSED) != 0) {
                setPressed(false);
            }
            mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
            // A disabled view that is clickable still consumes the touch
            // events, it just doesn't respond to them.
            return clickable; // 如果該 View 為可點擊，要避免其事件向下傳遞，因此不能直接 return false。
        }

        /*
         * TouchDelegate 是點擊代理，例如點擊的區域過小，如文字方塊、按鈕…等，導致不利於點擊操作，
         * 我們可以藉由此增加其區域，但仍是建議以設計的方式解決；目前來說，不常被使用。
         */
        if (mTouchDelegate != null) {
            if (mTouchDelegate.onTouchEvent(event)) {
                return true;
            }
        }

        /*
         * TOOLTIP：
         * 
         * - 在 API 26 以後所加入的工具，長按後會顯示該元件的說明，因此，即使是非點擊元件，也要將之判斷。
         */
        if (clickable || (viewFlags & TOOLTIP) == TOOLTIP) {
            switch (action) {
                case MotionEvent.ACTION_UP:
                    mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
                    if ((viewFlags & TOOLTIP) == TOOLTIP) {
                        handleTooltipUp(); // 關閉 TOOLTIP
                    }

                    /*
                     * 如果不支持點擊，代表僅支持 TOOLTIP，因此，直接取消所有監聽。
                     */
                    if (!clickable) {
                        removeTapCallback();
                        removeLongPressCallback();
                        mInContextButtonPress = false;
                        mHasPerformedLongPress = false;
                        mIgnoreNextUpEvent = false;
                        break;
                    }

                    // 判斷是否為按下或預按下狀態。
                    boolean prepressed = (mPrivateFlags & PFLAG_PREPRESSED) != 0;
                    if ((mPrivateFlags & PFLAG_PRESSED) != 0 || prepressed) {

                        /* 
                         * 設置焦點：設置焦點是指將焦點設置該 View 上，但尚未點擊。
                         * 
                         * 備註：設置焦點的功能在遙控器上很重要，但對於觸控而言，大多時候用處不大。
                         */
                        // take focus if we don't have it already and we should in
                        // touch mode.
                        boolean focusTaken = false;
                        if (isFocusable() && isFocusableInTouchMode() && !isFocused()) {
                            focusTaken = requestFocus(); // 設置焦點
                        }

                        /* 
                         * 預按下狀態時觸發 ACTION_UP，即代表完成一次點擊，故設置為按下。
                         */ 
                        if (prepressed) { 
                            // The button is being released before we actually
                            // showed it as pressed.  Make it show the pressed
                            // state now (before scheduling the click) to ensure
                            // the user sees it.
                            setPressed(true, x, y);
                        }

                        if (!mHasPerformedLongPress && !mIgnoreNextUpEvent) {
                            // This is a tap, so remove the longpress check
                            removeLongPressCallback();

                            // Only perform take click actions if we were in the pressed state
                            if (!focusTaken) {
                                // Use a Runnable and post this rather than calling
                                // performClick directly. This lets other visual state
                                // of the view update before click actions start.
                                if (mPerformClick == null) {
                                    mPerformClick = new PerformClick();
                                }
                                if (!post(mPerformClick)) {
                                    performClickInternal(); // 觸發點擊監聽
                                }
                            }
                        }

                        if (mUnsetPressedState == null) {
                            mUnsetPressedState = new UnsetPressedState();
                        }

                        /* 
                         * 由於預按下是沒有效果的，且會等待 100 ms，而當抬起觸發時，其才被設置為按下；也就是說，
                         * 按下與抬起將會發生在同一時間，也就是同一段程式碼中，因此該點擊事件就會沒有效果，因此，
                         * 我們必須延遲其效果。
                         */
                        if (prepressed) {
                            // 延遲按下狀態的置空
                            postDelayed(mUnsetPressedState,
                                    ViewConfiguration.getPressedStateDuration());
                        } else if (!post(mUnsetPressedState)) {
                            // If the post failed, unpress right now
                            mUnsetPressedState.run();
                        }

                        removeTapCallback();
                    }
                    mIgnoreNextUpEvent = false;
                    break;

                case MotionEvent.ACTION_DOWN: // DOWN：按鍵序列的領頭
                    /* 
                     * 判斷 Input 是否為觸摸，而非實體按鍵，遙控器也屬於實體按鍵。
                     *
                     * 作用：若為手指觸摸，則顯示說明文字時的距離比較遠，如 TOOLTIP，作用為避免文字被手指遮蔽。
                     */
                    if (event.getSource() == InputDevice.SOURCE_TOUCHSCREEN) {
                        mPrivateFlags3 |= PFLAG3_FINGER_DOWN;
                    }
                    mHasPerformedLongPress = false;

                    /* 
                     * 若為不可點擊，檢查是否為 TOOLTIP。
                     */
                    if (!clickable) {
                        checkForLongClick( // TOOLTIP 為長按觸發，故放置長按監聽器。
                                ViewConfiguration.getLongPressTimeout(),
                                x,
                                y,
                                TOUCH_GESTURE_CLASSIFIED__CLASSIFICATION__LONG_PRESS);
                        break; // 若不可以點擊，直接跳出。
                    }

                    /* 
                     * 判斷若為鼠標右鍵點擊，顯示 CONTEXT_CLICKABLE，並跳出。
                     */
                    if (performButtonActionOnTouchDown(event)) {
                        break;
                    }

                    // Walk up the hierarchy to determine if we're inside a scrolling container.
                    boolean isInScrollingContainer = isInScrollingContainer(); // 取得是否為滑動空間內。

                    // For views inside a scrolling container, delay the pressed feedback for
                    // a short period in case this is a scroll.
                    if (isInScrollingContainer) {
                        /* 
                         * 如果 View 在滑動空間中，則可能有下列兩情況：
                         * 
                         * - 操作子 View（選取）。
                         * - 操作父 View（滑動）。
                         * 
                         * 因此，使用預按下的機制處理。
                         */
                        mPrivateFlags |= PFLAG_PREPRESSED; // 狀態設置為預按下，而非按下；非立即反應，延遲處理。
                        if (mPendingCheckForTap == null) {
                            mPendingCheckForTap = new CheckForTap(); // 預按下的等待期。
                        }
                        mPendingCheckForTap.x = event.getX();
                        mPendingCheckForTap.y = event.getY();
                        postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                    } else {
                        /* 
                         * 如果該 View 不位於滑動空間中，觸摸就是按壓。
                         * 
                         * - setPressed(true, ...)，設置為按下。
                         * - 確認長按的狀態。
                         */
                        // Not inside a scrolling container, so show the feedback right away
                        setPressed(true, x, y);
                        checkForLongClick(
                                ViewConfiguration.getLongPressTimeout(), // 若超過預設的 Timeout 時間，則觸發長按，而非點擊。
                                x,
                                y,
                                TOUCH_GESTURE_CLASSIFIED__CLASSIFICATION__LONG_PRESS);
                    }
                    break;

                // 基本上就是置空的代碼
                case MotionEvent.ACTION_CANCEL:
                    if (clickable) {
                        setPressed(false);
                    }
                    removeTapCallback();
                    removeLongPressCallback();
                    mInContextButtonPress = false;
                    mHasPerformedLongPress = false;
                    mIgnoreNextUpEvent = false;
                    mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
                    break;

                /*
                 * ACTION_MOVE，偵測移動，對於手指操作的情況，該信息基本上會持續的傳入。
                 */
                case MotionEvent.ACTION_MOVE:
                    if (clickable) {
                        drawableHotspotChanged(x, y); // 移動時水波紋的效果
                    }

                    /* 
                     * 猜測可能跟全局手勢相關
                     */
                    final int motionClassification = event.getClassification(); // 取得操作類別
                    final boolean ambiguousGesture = // 判斷操作類別是否為 CLASSIFICATION_AMBIGUOUS_GESTURE
                            motionClassification == MotionEvent.CLASSIFICATION_AMBIGUOUS_GESTURE; 
                    int touchSlop = mTouchSlop;
                    if (ambiguousGesture && hasPendingLongPressCallback()) {
                        if (!pointInView(x, y, touchSlop)) {
                            // The default action here is to cancel long press. But instead, we
                            // just extend the timeout here, in case the classification
                            // stays ambiguous.
                            removeLongPressCallback(); // 移除預設長按監聽

                            // 新增長按監聽，並修正時間。
                            long delay = (long) (ViewConfiguration.getLongPressTimeout()
                                    * mAmbiguousGestureMultiplier);
                            // Subtract the time already spent
                            delay -= event.getEventTime() - event.getDownTime();
                            checkForLongClick(
                                    delay,
                                    x,
                                    y,
                                    TOUCH_GESTURE_CLASSIFIED__CLASSIFICATION__LONG_PRESS);
                        }
                        touchSlop *= mAmbiguousGestureMultiplier;
                    }

                    // Be lenient about moving outside of buttons
                    if (!pointInView(x, y, touchSlop)) {
                        /*
                         * 若移動出界，則取消所有動作。
                         * 
                         * 備註：為避免手指誤觸螢幕邊界而導致出界判斷，因此設置 touchSlop：觸摸邊界。
                         */
                        // Outside button
                        // Remove any future long press/tap checks
                        removeTapCallback();
                        removeLongPressCallback();
                        if ((mPrivateFlags & PFLAG_PRESSED) != 0) {
                            setPressed(false);
                        }
                        mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
                    }

                    /*
                     * 大力點擊事件
                     *
                     * - 需求 Android 10 之後。
                     * - 直接觸發長按。
                     */
                    final boolean deepPress =
                            motionClassification == MotionEvent.CLASSIFICATION_DEEP_PRESS;
                    if (deepPress && hasPendingLongPressCallback()) {
                        // process the long click action immediately
                        removeLongPressCallback();
                        checkForLongClick( // 立刻觸發長按效果
                                0 /* send immediately */,
                                x,
                                y,
                                TOUCH_GESTURE_CLASSIFIED__CLASSIFICATION__DEEP_PRESS);
                    }

                    break;
            }

            return true;
        }

        return false;
    }

    // ... Line: 15864

    /**
     * @hide
     * 
     * 判斷是否位於滑動空間中。
     */
    @UnsupportedAppUsage
    public boolean isInScrollingContainer() {
        ViewParent p = getParent();
        while (p != null && p instanceof ViewGroup) {
            if (((ViewGroup) p).shouldDelayChildPressedState()) { // 默認值為 true
                return true;
            }
            p = p.getParent(); // 需要遞迴父類。
        }
        return false;
    }

    // ... Line: 17978

    /**
     * Utility method to determine whether the given point, in local coordinates,
     * is inside the view, where the area of the view is expanded by the slop factor.
     * This method is called while processing touch-move events to determine if the event
     * is still within the view.
     *
     * @hide
     * 
     * 判斷是否出界
     */
    @UnsupportedAppUsage
    public boolean pointInView(float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < ((mRight - mLeft) + slop) &&
                localY < ((mBottom - mTop) + slop);
    }

    // ... Line: 28287

    private final class CheckForTap implements Runnable { // 是 Runnable 物件
        public float x;
        public float y;

        @Override
        public void run() {
            mPrivateFlags &= ~PFLAG_PREPRESSED; // 清除預按下
            setPressed(true, x, y); // 改為按下
            // 預按下後的長按等待時間 = 長按等待時間 - 預按下等待時間，避免使用者對於長按等待不一致。
            final long delay =
                    ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout();
            checkForLongClick(delay, x, y, TOUCH_GESTURE_CLASSIFIED__CLASSIFICATION__LONG_PRESS); // 設置長按監聽器
        }
    }

    // ... Line:30282

    private void handleTooltipUp() {
        if (mTooltipInfo == null || mTooltipInfo.mTooltipPopup == null) {
            return;
        }
        removeCallbacks(mTooltipInfo.mHideTooltipRunnable);
        postDelayed(mTooltipInfo.mHideTooltipRunnable, // 默認延遲 1.5 秒
                ViewConfiguration.getLongPressTooltipHideTimeout());
    }
}