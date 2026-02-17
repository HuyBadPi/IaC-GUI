package com.vmmanager.models;

import com.vmmanager.models.enums.OSType;

public class ProxmoxVM extends VM {
    private int vmid;
    private String node;
    private String storage;
    private String template;
    private String networkBridge;
    private String sshKey;
    
    public ProxmoxVM() {
        super();
        this.node = "pve1";
        this.networkBridge = "vmbr0";
    }
    
    @Override
    public boolean validate() {
        if (name == null || name.trim().isEmpty()) return false;
        if (cpuCores < 1 || cpuCores > 32) return false;
        if (memoryGB < 1 || memoryGB > 128) return false;
        if (diskGB < 10 || diskGB > 2000) return false;
        return true;
    }
    
    @Override
    public String generateTerraformConfig() {
        StringBuilder config = new StringBuilder();
        
        config.append(String.format("""
            # Terraform configuration for %s
            resource "proxmox_vm_qemu" "%s" {
                name        = "%s"
                vmid        = %s
                target_node = "%s"
                
                cores   = %d
                sockets = 1
                memory  = %d
                
                os_type = "cloud-init"
                clone   = "%s"
                
                disk {
                    storage = "%s"
                    type    = "scsi"
                    size    = "%dG"
                }
                
                network {
                    model  = "virtio"
                    bridge = "%s"
                }
            }
            """,
            name,
            name.toLowerCase().replace(" ", "_"),
            name,
            id,
            node,
            cpuCores,
            memoryGB * 1024,
            template != null ? template : "local:vztmpl/ubuntu-22.04-standard",
            storage != null ? storage : "local-lvm",
            diskGB,
            networkBridge
        ));
        
        return config.toString();
    }
    
    // Getters and Setters
    public int getVmid() { 
        return id != null ? Integer.parseInt(id) : 0; 
    }
    public void setVmid(int vmid) { 
        this.id = String.valueOf(vmid);
        this.vmid = vmid;
    }
    
    public String getNode() { return node; }
    public void setNode(String node) { this.node = node; }
    
    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }
    
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    
    public String getNetworkBridge() { return networkBridge; }
    public void setNetworkBridge(String networkBridge) { this.networkBridge = networkBridge; }
    
    public String getSshKey() { return sshKey; }
    public void setSshKey(String sshKey) { this.sshKey = sshKey; }
}