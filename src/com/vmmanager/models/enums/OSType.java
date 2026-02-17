package com.vmmanager.models.enums;

public enum OSType {
    UBUNTU_22_04("Ubuntu 22.04 LTS"),
    UBUNTU_20_04("Ubuntu 20.04 LTS"),
    CENTOS_9("CentOS 9"),
    DEBIAN_12("Debian 12"),
    WINDOWS_2022("Windows Server 2022");

    private final String displayName;

    OSType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static OSType fromString(String text) {
        for (OSType os : values()) {
            if (os.displayName.equals(text)) {
                return os;
            }
        }
        return UBUNTU_22_04;
    }
}
