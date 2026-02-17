package com.vmmanager.services.proxmox;

import java.io.IOException;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.vmmanager.utils.LoggerUtil;

public class ProxmoxAPIClient {
    private String apiUrl;
    private String ticket;
    private String csrfToken;
    private CloseableHttpClient httpClient;
    private String nodeName; // Lưu tên node sau khi lấy được
    
    public ProxmoxAPIClient(String host, String user, String password, String realm) {
        this.apiUrl = "https://" + host + ":8006/api2/json";
        this.httpClient = createHttpClientAcceptingAllCerts();
        authenticate(user, password, realm);
        
        // Sau khi đăng nhập thành công, lấy thông tin node
        this.nodeName = getFirstNodeName();
    }
    
    // Tạo HTTP Client chấp nhận tất cả chứng chỉ SSL
    private CloseableHttpClient createHttpClientAcceptingAllCerts() {
        try {
            // Tạo TrustStrategy chấp nhận tất cả certificate
            TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
            
            // Tạo SSLContext với trust strategy trên
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();
            
            // Tạo socket factory bỏ qua hostname verification
            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                    sslContext, 
                    NoopHostnameVerifier.INSTANCE);
            
            // Tạo HttpClient với socket factory
            return HttpClients.custom()
                    .setSSLSocketFactory(socketFactory)
                    .setConnectionTimeToLive(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            
        } catch (Exception e) {
            LoggerUtil.error("Lỗi tạo HTTP client với SSL", e);
            return HttpClients.createDefault(); // fallback
        }
    }
    
    private void authenticate(String user, String password, String realm) {
        try {
            HttpPost post = new HttpPost(apiUrl + "/access/ticket");
            
            String body = String.format("username=%s&password=%s&realm=%s", 
                user, password, realm);
            post.setEntity(new StringEntity(body));
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            
            LoggerUtil.info("Đang đăng nhập Proxmox: " + user + "@" + realm);
            
            CloseableHttpResponse response = httpClient.execute(post);
            String jsonResponse = EntityUtils.toString(response.getEntity());
            
            // Log response để debug
            LoggerUtil.debug("Proxmox auth response: " + jsonResponse);
            
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            
            if (!json.has("data")) {
                String errorMsg = json.has("errors") ? 
                    json.getAsJsonObject("errors").toString() : 
                    "Không có data trong response";
                throw new RuntimeException("Đăng nhập thất bại: " + errorMsg);
            }
            
            JsonObject data = json.getAsJsonObject("data");
            
            this.ticket = data.get("ticket").getAsString();
            this.csrfToken = data.get("CSRFPreventionToken").getAsString();
            
            LoggerUtil.info("Đăng nhập Proxmox thành công: " + user);
            
        } catch (Exception e) {
            LoggerUtil.error("Lỗi đăng nhập Proxmox", e);
            throw new RuntimeException("Không thể xác thực với Proxmox: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lấy tên node đầu tiên từ Proxmox
     * @return Tên node hoặc null nếu không lấy được
     */
    public String getFirstNodeName() {
        try {
            String response = getRequest("/nodes");
            LoggerUtil.debug("Nodes response: " + response);
            
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            
            if (!json.has("data")) {
                LoggerUtil.error("Không có data trong response nodes");
                return null;
            }
            
            JsonArray data = json.getAsJsonArray("data");
            
            if (data != null && data.size() > 0) {
                JsonObject firstNode = data.get(0).getAsJsonObject();
                String nodeName = firstNode.get("node").getAsString();
                String nodeStatus = firstNode.has("status") ? firstNode.get("status").getAsString() : "unknown";
                
                LoggerUtil.info("Tìm thấy node Proxmox: " + nodeName + " (status: " + nodeStatus + ")");
                return nodeName;
            } else {
                LoggerUtil.error("Không tìm thấy node nào trong Proxmox");
                return null;
            }
            
        } catch (IOException e) {
            LoggerUtil.error("Lỗi IO khi lấy danh sách node", e);
            return null;
        } catch (JsonSyntaxException e) {
            LoggerUtil.error("Lỗi parse JSON khi lấy danh sách node", e);
            return null;
        } catch (Exception e) {
            LoggerUtil.error("Lỗi không xác định khi lấy danh sách node", e);
            return null;
        }
    }
    
    /**
     * Lấy danh sách tất cả tên node
     * @return Mảng tên các node
     */
    public String[] getAllNodeNames() {
        try {
            String response = getRequest("/nodes");
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            
            if (!json.has("data")) {
                return new String[0];
            }
            
            JsonArray data = json.getAsJsonArray("data");
            String[] nodeNames = new String[data.size()];
            
            for (int i = 0; i < data.size(); i++) {
                JsonObject node = data.get(i).getAsJsonObject();
                nodeNames[i] = node.get("node").getAsString();
            }
            
            return nodeNames;
            
        } catch (Exception e) {
            LoggerUtil.error("Lỗi lấy danh sách node", e);
            return new String[0];
        }
    }
    
    /**
     * Kiểm tra xem node có tồn tại không
     * @param nodeName Tên node cần kiểm tra
     * @return true nếu node tồn tại
     */
    public boolean nodeExists(String nodeName) {
        try {
            String response = getRequest("/nodes/" + nodeName + "/status");
            return true; // Nếu không có exception là node tồn tại
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Lấy tên node đang được sử dụng
     * @return Tên node
     */
    public String getNodeName() {
        return nodeName;
    }
    
    public String getRequest(String endpoint) throws IOException {
        HttpGet get = new HttpGet(apiUrl + endpoint);
        get.setHeader("Cookie", "PVEAuthCookie=" + ticket);
        
        CloseableHttpResponse response = httpClient.execute(get);
        return EntityUtils.toString(response.getEntity());
    }
    
    public String postRequest(String endpoint, JsonObject data) throws IOException {
        HttpPost post = new HttpPost(apiUrl + endpoint);
        post.setHeader("Cookie", "PVEAuthCookie=" + ticket);
        post.setHeader("CSRFPreventionToken", csrfToken);
        post.setHeader("Content-Type", "application/json");
        
        if (data != null) {
            post.setEntity(new StringEntity(data.toString()));
        }
        
        CloseableHttpResponse response = httpClient.execute(post);
        return EntityUtils.toString(response.getEntity());
    }
    
    public String putRequest(String endpoint, JsonObject data) throws IOException {
        HttpPut put = new HttpPut(apiUrl + endpoint);
        put.setHeader("Cookie", "PVEAuthCookie=" + ticket);
        put.setHeader("CSRFPreventionToken", csrfToken);
        put.setHeader("Content-Type", "application/json");
        
        if (data != null) {
            put.setEntity(new StringEntity(data.toString()));
        }
        
        CloseableHttpResponse response = httpClient.execute(put);
        return EntityUtils.toString(response.getEntity());
    }
    
    public String deleteRequest(String endpoint) throws IOException {
        HttpDelete delete = new HttpDelete(apiUrl + endpoint);
        delete.setHeader("Cookie", "PVEAuthCookie=" + ticket);
        delete.setHeader("CSRFPreventionToken", csrfToken);
        
        CloseableHttpResponse response = httpClient.execute(delete);
        return EntityUtils.toString(response.getEntity());
    }
    
    /**
     * Kiểm tra kết nối đến Proxmox
     * @return true nếu kết nối thành công
     */
    public boolean testConnection() {
        try {
            String response = getRequest("/version");
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            String version = json.getAsJsonObject("data").get("version").getAsString();
            LoggerUtil.info("Kết nối Proxmox thành công, version: " + version);
            return true;
        } catch (Exception e) {
            LoggerUtil.error("Kết nối Proxmox thất bại", e);
            return false;
        }
    }
    
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            LoggerUtil.error("Lỗi đóng HTTP client", e);
        }
    }
}