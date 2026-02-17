package com.vmmanager.services.ansible;

import com.vmmanager.utils.LoggerUtil;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class AnsibleService {

    private final String sshUser;
    private final String sshKey;

    private final Path ansibleDir;
    private final Path playbookDir;
    private final Path customDir;
    private final Path inventoryDir;

    // ⭐ process đang chạy (để kill khi tắt app)
    private Process currentProcess;

    public AnsibleService(String sshUser, String sshKey) {

        this.sshUser = sshUser;
        this.sshKey = sshKey;

        this.ansibleDir = Paths.get("ansible");
        this.playbookDir = ansibleDir.resolve("playbooks");
        this.customDir = ansibleDir.resolve("custom");
        this.inventoryDir = ansibleDir.resolve("inventory");

        try {
            Files.createDirectories(playbookDir);
            Files.createDirectories(customDir);
            Files.createDirectories(inventoryDir);
        } catch (IOException e) {
            LoggerUtil.error("Create ansible dirs error", e);
        }
    }

    // ======================================================
    // LIST PLAYBOOKS (builtin + custom)
    // ======================================================
    public List<String> listPlaybooks() {

        List<String> list = new ArrayList<>();

        try {

            if (Files.exists(playbookDir)) {
                try (DirectoryStream<Path> s = Files.newDirectoryStream(playbookDir, "*.yml")) {
                    for (Path p : s)
                        list.add(p.getFileName().toString());
                }
            }

            if (Files.exists(customDir)) {
                try (DirectoryStream<Path> s = Files.newDirectoryStream(customDir, "*.yml")) {
                    for (Path p : s)
                        list.add("custom/" + p.getFileName().toString());
                }
            }

        } catch (IOException e) {
            LoggerUtil.error("List playbooks error", e);
        }

        Collections.sort(list);
        return list;
    }

    // ======================================================
    // RUN PLAYBOOK
    // ======================================================
    public boolean runPlaybook(String vmIP, String playbookName, String extraVars) {

        try {

            Path playbookPath = resolvePlaybookPath(playbookName);
            Path inventory = createRuntimeInventory(vmIP);

            List<String> cmd = new ArrayList<>();
            cmd.add("ansible-playbook");
            cmd.add("-i");
            cmd.add(inventory.toString());
            cmd.add(playbookPath.toString());
            cmd.add("--private-key");
            cmd.add(sshKey);

            // ⭐ tránh host key error + askpass
            cmd.add("--ssh-common-args");
            cmd.add("-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null");

            if (extraVars != null && !extraVars.isBlank()) {
                cmd.add("--extra-vars");
                cmd.add(extraVars.replace("\n", " "));
            }

            LoggerUtil.info("▶ Running Ansible: " + cmd);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            currentProcess = pb.start();

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(currentProcess.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                LoggerUtil.info("[ANSIBLE] " + line);
            }

            int exit = currentProcess.waitFor();
            currentProcess = null;

            return exit == 0;

        } catch (Exception e) {
            LoggerUtil.error("Run playbook error", e);
            return false;
        }
    }

    // ======================================================
    // RESOLVE PLAYBOOK PATH
    // ======================================================
    private Path resolvePlaybookPath(String name) {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Playbook null");

        Path p = Path.of(name);

        // absolute
        if (p.isAbsolute())
            return p;

        // custom/xxx.yml
        if (name.startsWith("custom/"))
            return customDir.resolve(name.substring("custom/".length()));

        // builtin
        return playbookDir.resolve(name);
    }

    // ======================================================
    // CREATE INVENTORY
    // ======================================================
    private Path createRuntimeInventory(String ip) throws IOException {

        Path file = inventoryDir.resolve("runtime.ini");

        String content =
                "[targets]\n" +
                ip +
                " ansible_user=" + sshUser +
                " ansible_ssh_private_key_file=" + sshKey +
                " ansible_ssh_common_args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null'\n";

        Files.writeString(
                file,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        return file;
    }

    // ======================================================
    // SHUTDOWN (kill running ansible)
    // ======================================================
    public void shutdown() {

        try {
            if (currentProcess != null && currentProcess.isAlive()) {
                LoggerUtil.info("Stopping ansible process...");
                currentProcess.destroyForcibly();
            }
        } catch (Exception e) {
            LoggerUtil.error("Stop ansible error", e);
        }
    }
    
    
}
