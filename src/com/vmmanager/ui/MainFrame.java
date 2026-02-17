package com.vmmanager.ui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

import com.vmmanager.controllers.VMController;
import com.vmmanager.config.ConfigService;
import com.vmmanager.config.GlobalConfig;

public class MainFrame extends JFrame {

    private VMController vmController;
    private GlobalConfig config;

    private JTable vmTable;
    private DefaultTableModel tableModel;
    private JLabel statusNodeLabel;

    private JTextField nameField;
    private JSpinner idSpinner, cpuSpinner, ramSpinner, diskSpinner;
    private JComboBox<String> templateCombo, storageCombo, networkCombo, nodeCombo;

    // ANSIBLE
    private JList<String> playbookList;
    private JComboBox<String> vmAnsibleCombo;
    private JTextArea extraVarsArea;

    // LOG
    private JTextArea logArea;

    // GLOBAL CONFIG FIELDS
    private JTextField proxHost, proxUser, proxPass;
    private JTextField ansUser, ansKey;
    private JCheckBox realApiBox;

    public MainFrame() {
        config = ConfigService.load();
        vmController = new VMController();
        vmController.setGlobalConfig(config);

        initUI();

        // 🔴 HANDLE CLOSE PROPERLY
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {

                vmController.shutdown();  // kill ansible + proxmox

                dispose();
                System.exit(0);
            }
        });
    }


    private void initUI() {
        setTitle("Proxmox VM Manager");
        setSize(1150, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("🌐 Global Config", createGlobalConfigPanel());
        tabs.addTab("➕ Tạo VM mới", createCreateVMPanel());
        tabs.addTab("📋 Danh sách VM", createListVMPanel());
        tabs.addTab("⚙️ Cấu hình Ansible", createAnsiblePanel());
        tabs.addTab("📊 Logs", createLogPanel());

        add(tabs, BorderLayout.CENTER);
        createStatusBar();
    }

    // ================= GLOBAL CONFIG =================

    private JPanel createGlobalConfigPanel(){

        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Proxmox & Ansible"));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8,8,8,8);
        g.fill = GridBagConstraints.HORIZONTAL;

        int y=0;

        proxHost = new JTextField(config.proxmoxHost);
        proxUser = new JTextField(config.proxmoxUser);
        proxPass = new JTextField(config.proxmoxPassword);

        ansUser  = new JTextField(config.ansibleUser);
        ansKey   = new JTextField(config.ansibleKey);

        realApiBox = new JCheckBox("Use Real API", config.useRealApi);

        g.gridx=0; g.gridy=y; form.add(new JLabel("Proxmox Host"),g);
        g.gridx=1; form.add(proxHost,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("User"),g);
        g.gridx=1; form.add(proxUser,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("Password"),g);
        g.gridx=1; form.add(proxPass,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("Ansible User"),g);
        g.gridx=1; form.add(ansUser,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("SSH Key Path"),g);
        g.gridx=1; form.add(ansKey,g); y++;

        g.gridx=1; g.gridy=y; form.add(realApiBox,g);

        p.add(form,BorderLayout.CENTER);

        JButton save = new JButton("💾 Save Config");
        p.add(save,BorderLayout.SOUTH);

        save.addActionListener(e -> saveGlobalConfig());

        return p;
    }
    
    private void refreshAnsibleTab(){

        if(vmAnsibleCombo != null){
            vmAnsibleCombo.setModel(
                new DefaultComboBoxModel<>(
                    vmController.getRunningVMsForAnsible()
                )
            );
        }

        if(playbookList != null){
            playbookList.setListData(
                vmController.getPlaybooks()
            );
        }
    }
    
    private void refreshCreateTab(){

        if(templateCombo != null){
            templateCombo.setModel(
                new DefaultComboBoxModel<>(
                    vmController.getAllTemplates()
                )
            );
        }

        if(storageCombo != null){
            storageCombo.setModel(
                new DefaultComboBoxModel<>(
                    vmController.getAllStorages()
                )
            );
        }

        if(networkCombo != null){
            networkCombo.setModel(
                new DefaultComboBoxModel<>(
                    vmController.getAllNetworks()
                )
            );
        }

        if(nodeCombo != null){
            nodeCombo.setModel(
                new DefaultComboBoxModel<>(
                    vmController.getAllNodes()
                )
            );

            nodeCombo.setSelectedItem(vmController.getCurrentNode());
        }
    }
    
    



    private void saveGlobalConfig(){

        config.proxmoxHost = proxHost.getText();
        config.proxmoxUser = proxUser.getText();
        config.proxmoxPassword = proxPass.getText();

        config.ansibleUser = ansUser.getText();
        config.ansibleKey = ansKey.getText();

        config.useRealApi = realApiBox.isSelected();

        ConfigService.save(config);
        vmController.setGlobalConfig(config);
        
        // ✅ REFRESH UI
        refreshVMTable();
        refreshAnsibleTab();
        refreshCreateTab();
        updateStatusBar(); 

        appendLog("✅ Saved global config");
    }

    // ================= CREATE VM =================

    private JPanel createCreateVMPanel() {

        JPanel panel = new JPanel(new BorderLayout(10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        JLabel title = new JLabel("TẠO VM TỪ TEMPLATE");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Thông tin VM",
                TitledBorder.LEFT,
                TitledBorder.TOP));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8,8,8,8);
        g.fill = GridBagConstraints.HORIZONTAL;

        int y = 0;

        g.gridx=0; g.gridy=y; form.add(new JLabel("Tên VM"),g);
        g.gridx=1; nameField = new JTextField(); form.add(nameField,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("VMID"),g);
        g.gridx=1; idSpinner = new JSpinner(new SpinnerNumberModel(100,100,9999,1)); form.add(idSpinner,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("CPU"),g);
        g.gridx=1; cpuSpinner = new JSpinner(new SpinnerNumberModel(2,1,32,1)); form.add(cpuSpinner,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("RAM GB"),g);
        g.gridx=1; ramSpinner = new JSpinner(new SpinnerNumberModel(4,1,128,1)); form.add(ramSpinner,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("Disk GB"),g);
        g.gridx=1; diskSpinner = new JSpinner(new SpinnerNumberModel(32,10,2000,10)); form.add(diskSpinner,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("Template"),g);
        g.gridx=1; templateCombo = new JComboBox<>(vmController.getAllTemplates()); form.add(templateCombo,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("Storage"),g);
        g.gridx=1; storageCombo = new JComboBox<>(vmController.getAllStorages()); form.add(storageCombo,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("Network"),g);
        g.gridx=1; networkCombo = new JComboBox<>(vmController.getAllNetworks()); form.add(networkCombo,g); y++;

        g.gridx=0; g.gridy=y; form.add(new JLabel("Node"),g);
        g.gridx=1;
        nodeCombo = new JComboBox<>(vmController.getAllNodes());
        nodeCombo.setSelectedItem(vmController.getCurrentNode());
        form.add(nodeCombo,g);

        panel.add(form, BorderLayout.CENTER);

        JPanel btns = new JPanel();
        JButton createBtn = new JButton("🚀 Tạo VM");
        JButton clearBtn  = new JButton("Clear");

        btns.add(createBtn);
        btns.add(clearBtn);
        panel.add(btns, BorderLayout.SOUTH);

        createBtn.addActionListener(e -> createVMAction());
        clearBtn.addActionListener(e -> clearForm());

        return panel;
    }

    private void createVMAction() {

        String name = nameField.getText();
        int vmid = (int) idSpinner.getValue();
        int cpu  = (int) cpuSpinner.getValue();
        int ram  = (int) ramSpinner.getValue();
        int disk = (int) diskSpinner.getValue();

        String template = (String) templateCombo.getSelectedItem();
        String storage  = (String) storageCombo.getSelectedItem();
        String net      = (String) networkCombo.getSelectedItem();
        String node     = (String) nodeCombo.getSelectedItem();

        boolean ok = vmController.createVM(
                name, vmid, cpu, ram, disk,
                "", template, storage, net, node);

        if(ok){
            clearForm();
            refreshVMTable();
            appendLog("✅ Đã tạo VM: " + name);
        }
    }

    private void clearForm(){
        nameField.setText("");
        idSpinner.setValue(100);
        cpuSpinner.setValue(2);
        ramSpinner.setValue(4);
        diskSpinner.setValue(32);
    }

    // ================= LIST =================

    private JPanel createListVMPanel() {

        JPanel panel = new JPanel(new BorderLayout(10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        JLabel title = new JLabel("DANH SÁCH VM");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);

        String[] cols = {"ID","Name","CPU","RAM","Disk","IP","Status","Node"};
        tableModel = new DefaultTableModel(cols,0){
            public boolean isCellEditable(int r,int c){ return false; }
        };

        vmTable = new JTable(tableModel);
        vmTable.setRowHeight(28);

        panel.add(new JScrollPane(vmTable), BorderLayout.CENTER);

        JPanel bar = new JPanel();
        JButton refreshBtn = new JButton("🔄 Refresh");
        JButton startBtn   = new JButton("▶ Start");
        JButton stopBtn    = new JButton("⏹ Stop");
        JButton deleteBtn  = new JButton("🗑 Delete");

        bar.add(refreshBtn);
        bar.add(startBtn);
        bar.add(stopBtn);
        bar.add(deleteBtn);
        panel.add(bar, BorderLayout.SOUTH);

        refreshBtn.addActionListener(e -> refreshVMTable());
        startBtn.addActionListener(e -> actionVM(vmController::startVM));
        stopBtn.addActionListener(e -> actionVM(vmController::stopVM));
        deleteBtn.addActionListener(e -> actionVM(vmController::deleteVM));

        refreshVMTable();
        return panel;
    }

    private interface VMAction { boolean run(String id); }

    private void actionVM(VMAction act){
        int r = vmTable.getSelectedRow();
        if(r<0){ JOptionPane.showMessageDialog(this,"Chọn VM"); return; }
        String id = tableModel.getValueAt(r,0).toString();
        act.run(id);
        refreshVMTable();
    }

    private void refreshVMTable(){
        if(tableModel==null) return;
        tableModel.setRowCount(0);
        Object[][] data = vmController.getVMTableData();
        for(Object[] row : data) tableModel.addRow(row);
    }

    // ================= ANSIBLE =================

    private JPanel createAnsiblePanel() {

        JPanel panel = new JPanel(new BorderLayout(10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        JLabel title = new JLabel("ANSIBLE PLAYBOOK");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);

        JPanel main = new JPanel(new GridLayout(1,2,20,0));

        // ===== LEFT: PLAYBOOK LIST + BUTTONS =====
        JPanel left = new JPanel(new BorderLayout(5,5));
        left.setBorder(BorderFactory.createTitledBorder("Playbooks"));

        playbookList = new JList<>(vmController.getPlaybooks());
        left.add(new JScrollPane(playbookList), BorderLayout.CENTER);

        JPanel pbBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addPb = new JButton("➕ Custom");
        JButton editPb = new JButton("✏ Edit");
        JButton delPb = new JButton("🗑 Delete");

        pbBtns.add(addPb);
        pbBtns.add(editPb);
        pbBtns.add(delPb);

        left.add(pbBtns, BorderLayout.SOUTH);

        main.add(left);

        // ===== RIGHT: RUN =====
        JPanel right = new JPanel(new GridBagLayout());
        right.setBorder(BorderFactory.createTitledBorder("Run"));

        GridBagConstraints g = new GridBagConstraints();
        g.insets=new Insets(8,8,8,8);
        g.fill=GridBagConstraints.HORIZONTAL;
        int y=0;

        g.gridx=0; g.gridy=y; right.add(new JLabel("VM"),g);
        g.gridx=1;
        vmAnsibleCombo = new JComboBox<>(vmController.getRunningVMsForAnsible());
        right.add(vmAnsibleCombo,g); y++;

        g.gridx=0; g.gridy=y; right.add(new JLabel("Extra Vars"),g);
        g.gridx=1;
        extraVarsArea = new JTextArea(6,20);
        right.add(new JScrollPane(extraVarsArea),g);

        main.add(right);

        panel.add(main,BorderLayout.CENTER);

        // ===== RUN BUTTON =====
        JPanel btns = new JPanel();
        JButton run = new JButton("▶ Run Playbook");
        btns.add(run);
        panel.add(btns,BorderLayout.SOUTH);

        // actions
        run.addActionListener(e -> runAnsible());
        addPb.addActionListener(e -> addCustomPlaybook());
        editPb.addActionListener(e -> editCustomPlaybook());
        delPb.addActionListener(e -> deleteCustomPlaybook());

        return panel;
    }
    
    private void addCustomPlaybook() {

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select YAML playbook");

        // ✅ chỉ cho chọn YAML
        fc.setFileFilter(
            new javax.swing.filechooser.FileNameExtensionFilter(
                "YAML playbooks (*.yml, *.yaml)", "yml", "yaml"
            )
        );

        int result = fc.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        java.io.File src = fc.getSelectedFile();

        // ✅ validate extension
        String name = src.getName().toLowerCase();
        if (!(name.endsWith(".yml") || name.endsWith(".yaml"))) {
            JOptionPane.showMessageDialog(this,
                    "Chỉ chấp nhận file .yml hoặc .yaml");
            return;
        }

        java.io.File dstDir = new java.io.File("ansible/custom");

        if (!dstDir.exists() && !dstDir.mkdirs()) {
            JOptionPane.showMessageDialog(this,
                    "Không thể tạo thư mục ansible/custom");
            return;
        }

        java.io.File dst = new java.io.File(dstDir, src.getName());

        try {
            java.nio.file.Files.copy(
                    src.toPath(),
                    dst.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            appendLog("➕ Added custom playbook: " + dst.getName());

            // ✅ refresh UI đúng
            refreshAnsibleTab();

            JOptionPane.showMessageDialog(this,
                    "Đã thêm playbook: " + dst.getName());

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Copy failed: " + ex.getMessage());
        }
    }

    
    private void editCustomPlaybook() {

        String sel = playbookList.getSelectedValue();
        if (sel == null || !sel.startsWith("[Custom] ")) {
            JOptionPane.showMessageDialog(this,"Chọn custom playbook");
            return;
        }

        String name = sel.replace("[Custom] ", "");
        java.io.File file = new java.io.File("ansible/custom/" + name);

        try {
            Desktop.getDesktop().open(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Open failed: "+ex.getMessage());
        }
    }
    
    private void deleteCustomPlaybook() {

        String sel = playbookList.getSelectedValue();
        if (sel == null || !sel.startsWith("[Custom] ")) {
            JOptionPane.showMessageDialog(this,"Chọn custom playbook");
            return;
        }

        String name = sel.replace("[Custom] ", "");

        int c = JOptionPane.showConfirmDialog(
                this,
                "Delete custom playbook " + name + "?",
                "Confirm",
                JOptionPane.YES_NO_OPTION
        );

        if (c != JOptionPane.YES_OPTION) return;

        java.io.File file = new java.io.File("ansible/custom/" + name);
        file.delete();

        refreshPlaybookList();
        appendLog("🗑 Deleted custom playbook: " + name);
    }
    
    private void refreshPlaybookList(){
        playbookList.setListData(vmController.getPlaybooks());
    }

    private void runAnsible(){

        String vmSel = (String) vmAnsibleCombo.getSelectedItem();
        String play  = playbookList.getSelectedValue();

        if(vmSel==null || play==null){
            JOptionPane.showMessageDialog(this,"Chọn VM và playbook");
            return;
        }

        String vmId = vmSel.split(" ")[0];
        String extra = extraVarsArea.getText();

        boolean ok = vmController.runPlaybookOnVM(vmId, play, extra);

        if(ok) appendLog("✅ Ansible OK: "+play+" → "+vmSel);
        else appendLog("❌ Ansible FAIL");
    }

    // ================= LOG =================

    private JPanel createLogPanel(){
        JPanel p=new JPanel(new BorderLayout());
        logArea=new JTextArea();
        logArea.setFont(new Font("Monospaced",Font.PLAIN,12));
        p.add(new JScrollPane(logArea));
        return p;
    }

    private void appendLog(String s){
        if(logArea==null) return;
        logArea.append(s+"\n");
    }
    
    private void updateStatusBar(){

        if(statusNodeLabel == null) return;

        String node = vmController.getCurrentNode();

        if(node == null || node.isBlank())
            node = "not connected";

        statusNodeLabel.setText("Node: " + node);
    }

    private void createStatusBar(){
        JPanel s = new JPanel(new FlowLayout(FlowLayout.LEFT));

        statusNodeLabel = new JLabel("Node: not connected");
        s.add(statusNodeLabel);

        add(s, BorderLayout.SOUTH);
    }

   
}
