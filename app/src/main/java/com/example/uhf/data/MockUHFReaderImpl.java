package com.example.uhf.data;

public class MockUHFReaderImpl implements UHFReaderRepository {

    private boolean isConnected = false;
    private boolean isScanning = false;
    private TagCallback tagCallback;

    @Override
    public boolean connect() {
        this.isConnected = true;
        return true;
    }

    @Override
    public boolean disconnect() {
        this.isConnected = false;
        this.isScanning = false;
        return true;
    }

    @Override
    public boolean isConnected() {
        return this.isConnected;
    }

    @Override
    public boolean startInventory() {
        if (!isConnected) return false;
        this.isScanning = true;
        return true;
    }

    @Override
    public boolean stopInventory() {
        this.isScanning = false;
        return true;
    }

    @Override
    public void setTagCallback(TagCallback callback) {
        this.tagCallback = callback;
    }

    @Override
    public boolean injectManualTag(String epc, String tid) {
        if (epc == null || epc.trim().isEmpty()) {
            return false;
        }

        String cleanEpc = epc.trim().toUpperCase();
        String cleanTid = (tid != null) ? tid.trim().toUpperCase() : "";
        String mockRssi = "-59 dBm";

        if (tagCallback != null) {
            tagCallback.onTagRead(cleanEpc, cleanTid, mockRssi);
        }
        
        return true;
    }
}
