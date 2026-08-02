package com.mira.ui.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

/**
 * MIRA UI Helper
 * 
 * مساعد الواجهات وإدارة لوحة المفاتيح والتنبيهات الموحدة بمنظومة MIRA
 */
public class UIHelper {

    // ===================== 🔔 Toast & Alert Management =====================

    /**
     * إظهار Toast بطلب نص مباشر
     */
    public static void ToastMessage(Context cont, String msg) {
        if (cont != null && msg != null) {
            Toast.makeText(cont, msg, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * إظهار Toast باستخدام معرف مورد النص (Resource ID)
     */
    public static void ToastMessage(Context cont, int msgResId) {
        if (cont != null) {
            Toast.makeText(cont, msgResId, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * إظهار Toast مخصص بتحديد مدة الظهور
     */
    public static void ToastMessage(Context cont, String msg, int duration) {
        if (cont != null && msg != null) {
            Toast.makeText(cont, msg, duration).show();
        }
    }

    /**
     * عرض نافذة تنبيه (Alert Dialog) عبر معرفات الموارد
     */
    public static void alert(Activity act, int titleInt, int messageInt, int iconInt) {
        if (act == null || act.isFinishing()) return;
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(act);
            builder.setTitle(titleInt);
            builder.setMessage(messageInt);
            if (iconInt != 0) builder.setIcon(iconInt);
            builder.setNegativeButton("إغلاق", (dialog, which) -> dialog.dismiss());
            builder.create().show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * عرض نافذة تنبيه (Alert Dialog) بنص مباشر
     */
    public static void alert(Activity act, int titleInt, String message, int iconInt) {
        if (act == null || act.isFinishing()) return;
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(act);
            builder.setTitle(titleInt);
            builder.setMessage(message != null ? message : "");
            if (iconInt != 0) builder.setIcon(iconInt);
            builder.setNegativeButton("إغلاق", (dialog, which) -> dialog.dismiss());
            builder.create().show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===================== ⌨️ Keyboard Management =====================

    /**
     * إخفاء لوحة المفاتيح من النشاط الحالي
     */
    public static void hideKeyboard(Activity activity) {
        if (activity == null) return;
        
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    /**
     * إخفاء لوحة المفاتيح عبر عنصر View محدد
     */
    public static void hideKeyboard(View view) {
        if (view == null) return;
        
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * إخفاء لوحة المفاتيح تلقائياً عند الضغط خارج حقول النص
     * تستخدم في setupUI() داخل الـ Fragments والـ Activities
     */
    public static void setupTouchToDismissKeyboard(Activity activity, View rootView) {
        if (activity == null || rootView == null) return;

        if (!(rootView instanceof EditText)) {
            rootView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    hideKeyboard(activity);
                    v.clearFocus();
                }
                return false;
            });
        }

        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setupTouchToDismissKeyboard(activity, child);
            }
        }
    }

    /**
     * إخفاء لوحة المفاتيح مع سحب التركيز من العنصر المباشر
     */
    public static void hideKeyboardAndClearFocus(Activity activity) {
        if (activity == null) return;
        
        View currentFocus = activity.getCurrentFocus();
        if (currentFocus != null) {
            currentFocus.clearFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
    }

    /**
     * إظهار لوحة المفاتيح وتوجيه التركيز إلى حقل نص محدد
     */
    public static void showKeyboard(Activity activity, EditText editText) {
        if (activity == null || editText == null) return;
        
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * التحقق مما إذا كانت لوحة المفاتيح الشاشة ظاهرة
     */
    public static boolean isKeyboardVisible(Activity activity) {
        if (activity == null) return false;
        
        View rootView = activity.getWindow().getDecorView().findViewById(android.R.id.content);
        if (rootView == null) return false;
        
        Rect rect = new Rect();
        rootView.getWindowVisibleDisplayFrame(rect);
        int screenHeight = rootView.getHeight();
        int keypadHeight = screenHeight - rect.bottom;
        
        return keypadHeight > screenHeight * 0.15;
    }
}
