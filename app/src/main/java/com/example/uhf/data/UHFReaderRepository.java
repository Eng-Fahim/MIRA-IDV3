package com.example.uhf.data;

public interface UHFReaderRepository {
    
    interface TagCallback {
        void onTagRead(String epc, String tid, String rssi);
    }

    boolean connect();
    boolean disconnect();
    boolean isConnected();

    boolean startInventory();
    boolean stopInventory();

    // إرسال البيانات يدويًا عبر MIRA Bridge
    boolean injectManualTag(String epc, String tid);
    
    // تسجيل المستمع لاستقبال البيانات
    void setTagCallback(TagCallback callback);
}
