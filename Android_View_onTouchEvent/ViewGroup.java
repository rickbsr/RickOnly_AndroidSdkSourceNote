package android.view;

@UiThread
public abstract class ViewGroup extends View implements ViewParent, ViewManager {

	// ... Line: 7888

    /**
     * Return true if the pressed state should be delayed for children or descendants of this
     * ViewGroup. Generally, this should be done for containers that can scroll, such as a List.
     * This prevents the pressed state from appearing when the user is actually trying to scroll
     * the content.
     *
     * The default implementation returns true for compatibility reasons. Subclasses that do
     * not scroll should generally override this method and return false.
     */
    public boolean shouldDelayChildPressedState() {
        return true; // ViewGroup 默認為滑動空間，因此，在自定義 View 時，若不需要該延遲，則需覆寫此方法。
    }
}

