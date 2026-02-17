package com.vmmanager.models;

import com.vmmanager.models.enums.VMStatus;
import com.vmmanager.models.enums.OSType;
import java.time.LocalDateTime;

public abstract class VM {
    protected String id;
    protected String name;
    protected int cpuCores;
    protected int memoryGB;
    protected int diskGB;
    protected OSType osType;
    protected VMStatus status;
    protected LocalDateTime createdDate;
    protected String ipAddress;
    
    public VM() {
        this.createdDate = LocalDateTime.now();
        this.status = VMStatus.PENDING;
    }
    
    public abstract boolean validate();
    public abstract String generateTerraformConfig();
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public int getCpuCores() { return cpuCores; }
    public void setCpuCores(int cpuCores) { this.cpuCores = cpuCores; }
    
    public int getMemoryGB() { return memoryGB; }
    public void setMemoryGB(int memoryGB) { this.memoryGB = memoryGB; }
    
    public int getDiskGB() { return diskGB; }
    public void setDiskGB(int diskGB) { this.diskGB = diskGB; }
    
    public OSType getOsType() { return osType; }
    public void setOsType(OSType osType) { this.osType = osType; }
    
    public VMStatus getStatus() { return status; }
    public void setStatus(VMStatus status) { this.status = status; }
    
    public LocalDateTime getCreatedDate() { return createdDate; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}