module iacGUI {
    requires java.desktop;
    requires com.google.gson;
    requires jsch;
    requires org.apache.httpcomponents.httpclient;
    requires org.apache.httpcomponents.httpcore;
    requires org.apache.httpcomponents.httpmime;
    requires commons.logging;
    opens com.vmmanager.config to com.google.gson;
}
