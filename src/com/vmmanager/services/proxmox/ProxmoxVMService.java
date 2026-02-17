package com.vmmanager.services.proxmox;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vmmanager.models.ProxmoxVM;
import com.vmmanager.models.enums.VMStatus;
import com.vmmanager.utils.LoggerUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProxmoxVMService {

    private final ProxmoxAPIClient apiClient;
    private String node;

    public ProxmoxVMService(String host, String user, String password, String realm, String configNode) {
        this.apiClient = new ProxmoxAPIClient(host, user, password, realm);

        String autoNode = detectNode();
        this.node = (autoNode != null) ? autoNode : configNode;

        LoggerUtil.info("✅ Using Proxmox node: " + node);
    }

    // =========================================================
    // NODE
    // =========================================================
    private String detectNode() {
        try {
            String res = apiClient.getRequest("/nodes");
            JsonArray data = JsonParser.parseString(res)
                    .getAsJsonObject()
                    .getAsJsonArray("data");

            if (data.size() > 0) {
                return data.get(0).getAsJsonObject()
                        .get("node").getAsString();
            }
        } catch (Exception e) {
            LoggerUtil.error("Detect node error", e);
        }
        return null;
    }

    public String getCurrentNode() {
        return node;
    }

    public List<String> getAllNodes() {
        List<String> nodes = new ArrayList<>();
        try {
            String res = apiClient.getRequest("/nodes");
            JsonArray data = JsonParser.parseString(res)
                    .getAsJsonObject()
                    .getAsJsonArray("data");

            for (int i = 0; i < data.size(); i++) {
                nodes.add(data.get(i)
                        .getAsJsonObject()
                        .get("node")
                        .getAsString());
            }
        } catch (Exception e) {
            LoggerUtil.error("Get nodes error", e);
        }
        return nodes;
    }

    public boolean testConnection() {
        return apiClient.testConnection();
    }

    // =========================================================
    // 🔥 GET VM IP FROM QEMU AGENT
    // =========================================================
    private String getVMIPAddress(int vmid) {
        try {
            String res = apiClient.getRequest(
                    "/nodes/" + node + "/qemu/" + vmid + "/agent/network-get-interfaces"
            );

            JsonObject root = JsonParser.parseString(res).getAsJsonObject();
            JsonArray ifaces = root
                    .getAsJsonObject("data")
                    .getAsJsonArray("result");

            for (int i = 0; i < ifaces.size(); i++) {
                JsonObject iface = ifaces.get(i).getAsJsonObject();

                if (!iface.has("ip-addresses")) continue;

                JsonArray ips = iface.getAsJsonArray("ip-addresses");

                for (int j = 0; j < ips.size(); j++) {
                    JsonObject ipObj = ips.get(j).getAsJsonObject();

                    String ip = ipObj.get("ip-address").getAsString();
                    String type = ipObj.get("ip-address-type").getAsString();

                    if ("ipv4".equals(type) && !ip.startsWith("127.")) {
                        return ip;
                    }
                }
            }

        } catch (Exception e) {
            // VM chưa có agent hoặc chưa có IP → ignore
        }

        return null;
    }

    // =========================================================
    // CREATE VM (CLONE FROM TEMPLATE)
    // =========================================================
    public boolean createVM(ProxmoxVM vm) {
        try {
            if (vm.getTemplate() == null || vm.getTemplate().isBlank()) {
                LoggerUtil.error("Template VMID missing");
                return false;
            }

            int templateId = Integer.parseInt(vm.getTemplate());

            JsonObject data = new JsonObject();
            data.addProperty("newid", vm.getVmid());
            data.addProperty("name", vm.getName());
            data.addProperty("full", 1);

            if (vm.getStorage() != null)
                data.addProperty("storage", vm.getStorage());

            if (vm.getNode() != null)
                data.addProperty("target", vm.getNode());

            apiClient.postRequest(
                    "/nodes/" + node + "/qemu/" + templateId + "/clone",
                    data
            );

            LoggerUtil.info("✅ Clone VM success: " + vm.getName());
            return true;

        } catch (Exception e) {
            LoggerUtil.error("Clone VM error", e);
            return false;
        }
    }

    // =========================================================
    // START / STOP / DELETE
    // =========================================================
    public boolean startVM(int vmid) {
        try {
            apiClient.postRequest(
                    "/nodes/" + node + "/qemu/" + vmid + "/status/start",
                    new JsonObject()
            );
            return true;
        } catch (IOException e) {
            LoggerUtil.error("Start VM error", e);
            return false;
        }
    }

    public boolean stopVM(int vmid) {
        try {
            apiClient.postRequest(
                    "/nodes/" + node + "/qemu/" + vmid + "/status/shutdown",
                    new JsonObject()
            );
            return true;
        } catch (IOException e) {
            LoggerUtil.error("Stop VM error", e);
            return false;
        }
    }

    public boolean deleteVM(int vmid) {
        try {
            apiClient.deleteRequest("/nodes/" + node + "/qemu/" + vmid);
            return true;
        } catch (IOException e) {
            LoggerUtil.error("Delete VM error", e);
            return false;
        }
    }

    // =========================================================
    // 🔥 LIST VMs (WITH IP)
    // =========================================================
    public List<ProxmoxVM> listVMs() {
        List<ProxmoxVM> list = new ArrayList<>();

        try {
            String res = apiClient.getRequest("/nodes/" + node + "/qemu");
            JsonArray data = JsonParser.parseString(res)
                    .getAsJsonObject()
                    .getAsJsonArray("data");

            for (int i = 0; i < data.size(); i++) {
                JsonObject vmJson = data.get(i).getAsJsonObject();

                ProxmoxVM vm = new ProxmoxVM();
                vm.setVmid(vmJson.get("vmid").getAsInt());
                vm.setName(vmJson.has("name") ? vmJson.get("name").getAsString() : "unknown");
                vm.setNode(node);

                if (vmJson.has("cores"))
                    vm.setCpuCores(vmJson.get("cores").getAsInt());

                if (vmJson.has("maxmem")) {
                    long mem = vmJson.get("maxmem").getAsLong();
                    vm.setMemoryGB((int) (mem / 1024 / 1024));
                }

                if (vmJson.has("maxdisk")) {
                    long disk = vmJson.get("maxdisk").getAsLong();
                    vm.setDiskGB((int) (disk / 1024 / 1024 / 1024));
                }

                String status = vmJson.has("status")
                        ? vmJson.get("status").getAsString()
                        : "stopped";

                vm.setStatus(parseStatus(status));

                // 🔥 LẤY IP NẾU VM RUNNING
                if ("running".equalsIgnoreCase(status)) {
                    String ip = getVMIPAddress(vm.getVmid());
                    vm.setIpAddress(ip);
                }

                list.add(vm);
            }

        } catch (Exception e) {
            LoggerUtil.error("List VMs error", e);
        }

        return list;
    }

    // =========================================================
    // LIST TEMPLATES
    // =========================================================
    public List<ProxmoxVM> listTemplates() {
        List<ProxmoxVM> list = new ArrayList<>();

        try {
            String res = apiClient.getRequest("/nodes/" + node + "/qemu");
            JsonArray data = JsonParser.parseString(res)
                    .getAsJsonObject()
                    .getAsJsonArray("data");

            for (int i = 0; i < data.size(); i++) {
                JsonObject vmJson = data.get(i).getAsJsonObject();

                if (vmJson.has("template") &&
                        vmJson.get("template").getAsInt() == 1) {

                    ProxmoxVM vm = new ProxmoxVM();
                    vm.setVmid(vmJson.get("vmid").getAsInt());
                    vm.setName(vmJson.get("name").getAsString());
                    list.add(vm);
                }
            }

        } catch (Exception e) {
            LoggerUtil.error("List templates error", e);
        }

        return list;
    }

    // =========================================================
    // STORAGES
    // =========================================================
    public List<String> listStorages() {
        List<String> list = new ArrayList<>();
        try {
            String res = apiClient.getRequest("/nodes/" + node + "/storage");
            JsonArray data = JsonParser.parseString(res)
                    .getAsJsonObject()
                    .getAsJsonArray("data");

            for (int i = 0; i < data.size(); i++) {
                list.add(data.get(i)
                        .getAsJsonObject()
                        .get("storage")
                        .getAsString());
            }

        } catch (Exception e) {
            LoggerUtil.error("List storages error", e);
        }
        return list;
    }

    // =========================================================
    // NETWORK BRIDGES
    // =========================================================
    public List<String> listBridges() {
        List<String> list = new ArrayList<>();
        try {
            String res = apiClient.getRequest("/nodes/" + node + "/network");
            JsonArray data = JsonParser.parseString(res)
                    .getAsJsonObject()
                    .getAsJsonArray("data");

            for (int i = 0; i < data.size(); i++) {
                JsonObject net = data.get(i).getAsJsonObject();
                if ("bridge".equals(net.get("type").getAsString())) {
                    list.add(net.get("iface").getAsString());
                }
            }

        } catch (Exception e) {
            LoggerUtil.error("List bridges error", e);
        }
        return list;
    }

    // =========================================================
    // STATUS
    // =========================================================
    private VMStatus parseStatus(String s) {
        return switch (s) {
            case "running" -> VMStatus.RUNNING;
            case "stopped" -> VMStatus.STOPPED;
            default -> VMStatus.PENDING;
        };
    }

    public Integer getNextVMID() {
        try {
            String res = apiClient.getRequest("/cluster/nextid");
            return JsonParser.parseString(res)
                    .getAsJsonObject()
                    .get("data").getAsInt();
        } catch (Exception e) {
            LoggerUtil.error("Next VMID error", e);
            return null;
        }
    }
    
    

    public void close() {
        apiClient.close();
    }
}
