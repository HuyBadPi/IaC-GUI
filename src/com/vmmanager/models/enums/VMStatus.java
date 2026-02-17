package com.vmmanager.models.enums;

public enum VMStatus {
    PENDING("⏳ Đang tạo"),
    RUNNING("🟢 Đang chạy"),
    STOPPED("🔴 Đã dừng"),
    ERROR("❌ Lỗi"),
    SUSPENDED("🟡 Tạm dừng");
    
    private final String displayName;
    
    VMStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}