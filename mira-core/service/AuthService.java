package com.mira.core;

import android.os.Handler;
import android.os.Looper;

public class AuthService {

    public interface AuthCallback {
        void onSuccess(String username, String role, String token);
        void onError(String errorMessage);
    }

    // دالة التحقق من الحساب (محاكاة للربط المباشر مع سيرفر Odoo / REST API)
    public static void authenticateUser(String username, String password, AuthCallback callback) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            
            if (username.equalsIgnoreCase("admin") && password.equals("admin123")) {
                callback.onSuccess("المدير العام", "ADMIN", "JWT_TOKEN_ADMIN_MIRA_2026");
            } else if (username.equalsIgnoreCase("mira") || username.equalsIgnoreCase("user")) {
                callback.onSuccess("مسؤول المخزن", "INVENTORY", "JWT_TOKEN_INV_MIRA_2026");
            } else {
                callback.onError("اسم المستخدم أو كلمة المرور غير صحيحة");
            }
            
        }, 800); // تأخير زمني لمحاكاة استجابة الشبكة
    }
}
