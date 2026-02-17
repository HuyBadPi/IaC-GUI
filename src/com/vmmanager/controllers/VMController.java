package com.vmmanager.controllers;

import java.util.ArrayList;
import java.io.File;
import java.util.List;

import javax.swing.JOptionPane;

import com.vmmanager.config.GlobalConfig;
import com.vmmanager.models.ProxmoxVM;
import com.vmmanager.models.enums.VMStatus;
import com.vmmanager.models.enums.OSType;
import com.vmmanager.services.proxmox.ProxmoxVMService;
import com.vmmanager.services.ansible.AnsibleService;
import com.vmmanager.utils.LoggerUtil;

public class VMController {

    private final List<ProxmoxVM> vmList = new ArrayList<>();

    private ProxmoxVMService proxmoxService;
    private AnsibleService ansibleService;

    private boolean useRealAPI = false;
    private String currentNode = "mock";

    private GlobalConfig config;

    // ================= CONSTRUCTOR =================
    public VMController() {
        loadSampleData();
    }

    // ================= GLOBAL CONFIG =================
    public void setGlobalConfig(GlobalConfig cfg) {

        try {
            if (cfg == null) {
                LoggerUtil.error("GlobalConfig null");
                return;
            }

            this.config = cfg;
            this.useRealAPI = cfg.useRealApi;

            LoggerUtil.info("🔄 Applying GlobalConfig (real=" + useRealAPI + ")");

            if (!useRealAPI) {
                proxmoxService = null;
                ansibleService = null;
                currentNode = "mock";
                loadSampleData();
                return;
            }

            // ==== VALIDATE PROXMOX ====
            if (isBlank(cfg.proxmoxHost) ||
                isBlank(cfg.proxmoxUser) ||
                isBlank(cfg.proxmoxPassword)) {

                LoggerUtil.error("Missing Proxmox config");
                loadSampleData();
                return;
            }

            // ==== INIT PROXMOX ====
            proxmoxService = new ProxmoxVMService(
                    cfg.proxmoxHost,
                    cfg.proxmoxUser,
                    cfg.proxmoxPassword,
                    "pam",
                    null
            );

            currentNode = proxmoxService.getCurrentNode();

            // ==== INIT ANSIBLE ====
            if (!isBlank(cfg.ansibleUser) && !isBlank(cfg.ansibleKey)) {
                ansibleService = new AnsibleService(
                        cfg.ansibleUser,
                        cfg.ansibleKey
                );
            }

            // ==== TEST CONNECTION ====
            if (!proxmoxService.testConnection()) {
                LoggerUtil.error("❌ Proxmox connection failed");
                loadSampleData();
                return;
            }

            // ==== LOAD REAL DATA ====
            loadVMsFromProxmox();
            LoggerUtil.info("✅ Connected Proxmox node=" + currentNode);

        } catch (Exception e) {
            LoggerUtil.error("GlobalConfig apply error", e);
            loadSampleData();
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ================= LOAD =================
    private void loadVMsFromProxmox() {
        try {
            vmList.clear();
            vmList.addAll(proxmoxService.listVMs());
        } catch (Exception e) {
            LoggerUtil.error("Load VM error", e);
            loadSampleData();
        }
    }

    private void loadSampleData() {
        vmList.clear();

        ProxmoxVM vm = new ProxmoxVM();
        vm.setVmid(100);
        vm.setName("mock-vm");
        vm.setCpuCores(2);
        vm.setMemoryGB(4);
        vm.setDiskGB(32);
        vm.setStatus(VMStatus.RUNNING);
        vm.setNode("mock");
        vm.setIpAddress("192.168.1.10");

        vmList.add(vm);
    }

    // ================= CREATE =================
    public boolean createVM(String name, int vmid, int cpu, int ram, int disk,
                            String osType, String template, String storage,
                            String bridge, String node) {

        if (!validateVMInput(name, cpu, ram, disk)) return false;
        if (isVMIDExists(vmid)) return error("VM ID exists: " + vmid);

        ProxmoxVM vm = new ProxmoxVM();
        vm.setVmid(vmid);
        vm.setName(name);
        vm.setCpuCores(cpu);
        vm.setMemoryGB(ram);
        vm.setDiskGB(disk);
        vm.setStatus(VMStatus.PENDING);
        vm.setOsType(OSType.fromString(osType));
        vm.setTemplate(template);
        vm.setStorage(storage);
        vm.setNetworkBridge(bridge);
        vm.setNode(node != null ? node : currentNode);

        if (!useRealAPI || proxmoxService == null) {
            vmList.add(vm);
            return info("MOCK create: " + name);
        }

        try {
            boolean ok = proxmoxService.createVM(vm);
            if (!ok) return error("Create VM failed");

            Thread.sleep(2000);
            loadVMsFromProxmox();
            return info("Created VM: " + name);

        } catch (Exception e) {
            LoggerUtil.error("Create VM error", e);
            return error(e.getMessage());
        }
    }

    // ================= START =================
    public boolean startVM(String vmId) {
        ProxmoxVM vm = findVM(vmId);
        if (vm == null) return error("VM not found");

        if (!useRealAPI || proxmoxService == null) {
            vm.setStatus(VMStatus.RUNNING);
            return info("MOCK start " + vmId);
        }

        try {
            boolean ok = proxmoxService.startVM(vm.getVmid());
            if (ok) vm.setStatus(VMStatus.RUNNING);
            return ok;
        } catch (Exception e) {
            LoggerUtil.error("Start VM", e);
            return error(e.getMessage());
        }
    }

    // ================= STOP =================
    public boolean stopVM(String vmId) {
        ProxmoxVM vm = findVM(vmId);
        if (vm == null) return error("VM not found");

        if (!useRealAPI || proxmoxService == null) {
            vm.setStatus(VMStatus.STOPPED);
            return info("MOCK stop " + vmId);
        }

        try {
            boolean ok = proxmoxService.stopVM(vm.getVmid());
            if (ok) vm.setStatus(VMStatus.STOPPED);
            return ok;
        } catch (Exception e) {
            LoggerUtil.error("Stop VM", e);
            return error(e.getMessage());
        }
    }

    // ================= DELETE =================
    public boolean deleteVM(String vmId) {
        ProxmoxVM vm = findVM(vmId);
        if (vm == null) return error("VM not found");

        if (!useRealAPI || proxmoxService == null) {
            vmList.remove(vm);
            return info("MOCK delete " + vmId);
        }

        try {
            boolean ok = proxmoxService.deleteVM(vm.getVmid());
            if (ok) vmList.remove(vm);
            return ok;
        } catch (Exception e) {
            LoggerUtil.error("Delete VM", e);
            return error(e.getMessage());
        }
    }

    // ================= FIND =================
    private ProxmoxVM findVM(String vmId) {
        try {
            int id = Integer.parseInt(vmId);
            return vmList.stream()
                    .filter(v -> v.getVmid() == id)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isVMIDExists(int id) {
        return vmList.stream().anyMatch(v -> v.getVmid() == id);
    }

    // ================= TABLE =================
    public Object[][] getVMTableData() {
        Object[][] data = new Object[vmList.size()][8];
        for (int i = 0; i < vmList.size(); i++) {
            ProxmoxVM vm = vmList.get(i);
            data[i][0] = vm.getVmid();
            data[i][1] = vm.getName();
            data[i][2] = vm.getCpuCores();
            data[i][3] = vm.getMemoryGB();
            data[i][4] = vm.getDiskGB();
            data[i][5] = vm.getIpAddress();
            data[i][6] = vm.getStatus().name();
            data[i][7] = vm.getNode();
        }
        return data;
    }

    // ================= REAL OPTIONS =================
    public String[] getAllNodes() {
        if (useRealAPI && proxmoxService != null)
            return proxmoxService.getAllNodes().toArray(new String[0]);
        return new String[]{"mock"};
    }

    public String[] getAllTemplates() {
        if (useRealAPI && proxmoxService != null)
            return proxmoxService.listTemplates()
                    .stream()
                    .map(t -> String.valueOf(t.getVmid()))
                    .toArray(String[]::new);
        return new String[]{"9000"};
    }

    public String[] getAllStorages() {
        if (useRealAPI && proxmoxService != null)
            return proxmoxService.listStorages().toArray(new String[0]);
        return new String[]{"local-lvm"};
    }

    public String[] getAllNetworks() {
        if (useRealAPI && proxmoxService != null)
            return proxmoxService.listBridges().toArray(new String[0]);
        return new String[]{"vmbr0"};
    }

    // ================= ANSIBLE =================
    
    public boolean addCustomPlaybook(File sourceFile) {

        try {

            File destDir = new File("ansible/custom");
            if (!destDir.exists())
                destDir.mkdirs();

            File destFile = new File(destDir, sourceFile.getName());

            java.nio.file.Files.copy(
                    sourceFile.toPath(),
                    destFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            LoggerUtil.info("Added custom playbook: " + destFile);

            return true;

        } catch (Exception e) {
            LoggerUtil.error("Add custom playbook", e);
            return false;
        }
    }

    
    public String[] getCustomPlaybooks() {

        java.io.File dir = new java.io.File("ansible/custom");

        if (!dir.exists()) dir.mkdirs();

        String[] files = dir.list((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));

        return files != null ? files : new String[0];
    }
    
    public String[] getPlaybooks() {

        List<String> list = new ArrayList<>();

        // ===== BUILT-IN =====
        if (ansibleService != null) {
            list.addAll(ansibleService.listPlaybooks());
        }

        // ===== CUSTOM =====
        for (String c : getCustomPlaybooks()) {
            list.add("[Custom] " + c);
        }

        if (list.isEmpty()) {
            list.add("none");
        }

        return list.toArray(new String[0]);
    }


    public String[] getRunningVMsForAnsible() {
        return vmList.stream()
                .filter(v -> v.getStatus() == VMStatus.RUNNING)
                .filter(v -> v.getIpAddress() != null)
                .map(v -> v.getVmid() + " (" + v.getIpAddress() + ")")
                .toArray(String[]::new);
    }

    public boolean runPlaybookOnVM(String vmId, String playbook, String extraVars) {

		ProxmoxVM vm = findVM(vmId);
		if (vm == null) return error("VM not found");
		
		if (vm.getIpAddress() == null || vm.getIpAddress().isBlank())
			return error("VM chưa có IP");
		
		try {
			// ===== resolve playbook path =====
			String playbookPath;
			
			if (playbook.startsWith("[Custom] ")) {
				String name = playbook.replace("[Custom] ", "");
				playbookPath = "ansible/custom/" + name;
			} else {
				playbookPath = "ansible/playbooks/" + playbook;
			}
			
			LoggerUtil.info("▶ Run Ansible playbook: " + playbookPath + " on " + vm.getIpAddress());
			
			return ansibleService.runPlaybook(
				vm.getIpAddress(),
				playbookPath,
				extraVars
			);
		
		} catch (Exception e) {
			LoggerUtil.error("Run ansible", e);
			return false;
		}
	}

    public String getCurrentNode() {
        return currentNode;
    }

    // ================= UI =================
    private boolean error(String m) {
        JOptionPane.showMessageDialog(null, m, "Error", JOptionPane.ERROR_MESSAGE);
        return false;
    }

    private boolean info(String m) {
        JOptionPane.showMessageDialog(null, m, "Info", JOptionPane.INFORMATION_MESSAGE);
        return true;
    }

    private boolean validateVMInput(String name, int cpu, int ram, int disk) {
        if (name == null || name.isBlank()) return error("Name empty");
        if (cpu <= 0 || ram <= 0 || disk <= 0) return error("Invalid resources");
        return true;
    }

    // ================= SHUTDOWN =================
    public void cleanup() {
        if (proxmoxService != null) proxmoxService.close();
    }
    
    public void shutdown() {

        if (ansibleService != null)
            ansibleService.shutdown();

        if (proxmoxService != null)
            proxmoxService.close();
    }

}
