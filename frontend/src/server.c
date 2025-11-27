#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <time.h>

#define PORT 8080
#define BUFFER_SIZE 4096

void log_client_info(struct sockaddr_in *client_addr) {
    char ip[INET_ADDRSTRLEN];
    inet_ntop(AF_INET, &(client_addr->sin_addr), ip, INET_ADDRSTRLEN);
    int port = ntohs(client_addr->sin_port);
    
    time_t now = time(NULL);
    char *timestamp = ctime(&now);
    timestamp[strlen(timestamp) - 1] = '\0'; // Remove newline
    
    printf("[%s] Client connected from %s:%d\n", timestamp, ip, port);
}

void send_response(int client_sock, const char *client_ip, int client_port) {
    char response[BUFFER_SIZE];
    char body[512];
    
    // Tạo HTML response
    snprintf(body, sizeof(body),
        "<!DOCTYPE html>\n"
        "<html>\n"
        "<head>\n"
        "    <meta charset='UTF-8'>\n"
        "    <title>Client Info</title>\n"
        "    <style>\n"
        "        body { font-family: Arial; margin: 50px; background: #f0f0f0; }\n"
        "        .info { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n"
        "        h1 { color: #333; }\n"
        "        .data { color: #0066cc; font-weight: bold; }\n"
        "    </style>\n"
        "</head>\n"
        "<body>\n"
        "    <div class='info'>\n"
        "        <h1>Thông tin kết nối</h1>\n"
        "        <p>IP của bạn: <span class='data'>%s</span></p>\n"
        "        <p>Port của bạn: <span class='data'>%d</span></p>\n"
        "        <p>Server đã nhận được kết nối từ bạn!</p>\n"
        "    </div>\n"
        "</body>\n"
        "</html>",
        client_ip, client_port);
    
    // Tạo HTTP header
    snprintf(response, sizeof(response),
        "HTTP/1.1 200 OK\r\n"
        "Content-Type: text/html; charset=UTF-8\r\n"
        "Content-Length: %zu\r\n"
        "Connection: close\r\n"
        "\r\n"
        "%s",
        strlen(body), body);
    
    send(client_sock, response, strlen(response), 0);
}

int main() {
    int server_sock, client_sock;
    struct sockaddr_in server_addr, client_addr;
    socklen_t client_len = sizeof(client_addr);
    char buffer[BUFFER_SIZE];
    
    // Tạo socket
    server_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (server_sock < 0) {
        perror("Không thể tạo socket");
        exit(1);
    }
    
    // Cho phép reuse address
    int opt = 1;
    setsockopt(server_sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    
    // Cấu hình địa chỉ server
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = INADDR_ANY;
    server_addr.sin_port = htons(PORT);
    
    // Bind socket
    if (bind(server_sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        perror("Bind thất bại");
        close(server_sock);
        exit(1);
    }
    
    // Listen
    if (listen(server_sock, 10) < 0) {
        perror("Listen thất bại");
        close(server_sock);
        exit(1);
    }
    
    printf("Server đang chạy trên port %d...\n", PORT);
    printf("Truy cập: http://localhost:%d\n\n", PORT);
    
    // Accept loop
    while (1) {
        client_sock = accept(server_sock, (struct sockaddr *)&client_addr, &client_len);
        if (client_sock < 0) {
            perror("Accept thất bại");
            continue;
        }
        
        // Lấy thông tin client
        char client_ip[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &(client_addr.sin_addr), client_ip, INET_ADDRSTRLEN);
        int client_port = ntohs(client_addr.sin_port);
        
        // Log thông tin
        log_client_info(&client_addr);
        
        // Đọc request
        memset(buffer, 0, BUFFER_SIZE);
        read(client_sock, buffer, BUFFER_SIZE - 1);
        
        // Gửi response
        send_response(client_sock, client_ip, client_port);
        
        close(client_sock);
    }
    
    close(server_sock);
    return 0;
}