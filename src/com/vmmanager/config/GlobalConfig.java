package com.vmmanager.config;

public class GlobalConfig {

    // Proxmox
    public String proxmoxHost;
    public String proxmoxUser;
    public String proxmoxPassword;
    public String proxmoxRealm = "pam";

    // Ansible
    public String ansibleUser;
    public String ansibleKey;

    // App
    public boolean useRealApi;
}
