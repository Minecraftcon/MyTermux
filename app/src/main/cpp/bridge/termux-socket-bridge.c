#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <poll.h>

static volatile int running = 1;
static void sig_handler(int sig) {
    (void)sig;
    running = 0;
}

static int connect_abstract(const char *name) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return -1;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    size_t len = strlen(name);
    if (len > sizeof(addr.sun_path) - 2) len = sizeof(addr.sun_path) - 2;
    memcpy(addr.sun_path + 1, name, len);

    socklen_t addr_len = (socklen_t)(sizeof(sa_family_t) + 1 + len);
    if (connect(fd, (struct sockaddr *)&addr, addr_len) < 0) {
        close(fd);
        return -1;
    }
    return fd;
}

static void relay_loop(int tcp_fd, int unix_fd) {
    struct pollfd fds[2];
    fds[0].fd = tcp_fd;
    fds[0].events = POLLIN;
    fds[1].fd = unix_fd;
    fds[1].events = POLLIN;

    char buf[16384];
    while (running) {
        int ret = poll(fds, 2, 5000);
        if (ret < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (ret == 0) continue;

        if (fds[0].revents & (POLLERR | POLLHUP | POLLNVAL) ||
            fds[1].revents & (POLLERR | POLLHUP | POLLNVAL)) {
            break;
        }

        if (fds[0].revents & POLLIN) {
            ssize_t n = read(tcp_fd, buf, sizeof(buf));
            if (n <= 0) break;
            ssize_t sent = 0;
            while (sent < n) {
                ssize_t w = write(unix_fd, buf + sent, (size_t)(n - sent));
                if (w <= 0) goto done;
                sent += w;
            }
        }

        if (fds[1].revents & POLLIN) {
            ssize_t n = read(unix_fd, buf, sizeof(buf));
            if (n <= 0) break;
            ssize_t sent = 0;
            while (sent < n) {
                ssize_t w = write(tcp_fd, buf + sent, (size_t)(n - sent));
                if (w <= 0) goto done;
                sent += w;
            }
        }
    }
done:
    close(tcp_fd);
    close(unix_fd);
}

static int send_unix_command(const char *sock_path, const char *cmd) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return 1;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    socklen_t addr_len;
    if (sock_path[0] == '@') {
        addr.sun_path[0] = '\0';
        size_t len = strlen(sock_path + 1);
        if (len > sizeof(addr.sun_path) - 2) len = sizeof(addr.sun_path) - 2;
        memcpy(addr.sun_path + 1, sock_path + 1, len);
        addr_len = (socklen_t)(sizeof(sa_family_t) + 1 + len);
    } else {
        strncpy(addr.sun_path, sock_path, sizeof(addr.sun_path) - 1);
        addr_len = sizeof(addr);
    }

    if (connect(fd, (struct sockaddr *)&addr, addr_len) < 0) {
        close(fd);
        return 2;
    }

    size_t cmd_len = strlen(cmd);
    ssize_t written = 0;
    while (written < (ssize_t)cmd_len) {
        ssize_t w = write(fd, cmd + written, cmd_len - written);
        if (w <= 0) {
            close(fd);
            return 3;
        }
        written += w;
    }

    shutdown(fd, SHUT_WR);

    char resp[512];
    ssize_t n = read(fd, resp, sizeof(resp) - 1);
    close(fd);
    if (n > 0) {
        resp[n] = '\0';
        return atoi(resp);
    }
    return 0;
}

int main(int argc, char *argv[]) {
    if (argc >= 4 && strcmp(argv[1], "send") == 0) {
        return send_unix_command(argv[2], argv[3]);
    }

    if (argc < 3) {
        fprintf(stderr, "Usage: %s <abstract_socket_name> <tcp_port> [--daemon]\n", argv[0]);
        fprintf(stderr, "   or: %s send <socket_path> <command_string>\n", argv[0]);
        return 1;
    }

    const char *sock_name = argv[1];
    if (sock_name[0] == '@') sock_name++; // Strip leading @
    int port = atoi(argv[2]);
    if (port <= 0 || port > 65535) {
        fprintf(stderr, "Error: Invalid port: %s\n", argv[2]);
        return 1;
    }

    int daemon_mode = (argc > 3 && strcmp(argv[3], "--daemon") == 0);

    signal(SIGPIPE, SIG_IGN);
    signal(SIGCHLD, SIG_IGN);
    signal(SIGHUP, SIG_IGN);
    signal(SIGINT, sig_handler);
    signal(SIGTERM, sig_handler);

    int srv = socket(AF_INET, SOCK_STREAM, 0);
    if (srv < 0) {
        perror("socket");
        return 1;
    }

    int opt = 1;
    setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
#ifdef SO_REUSEPORT
    setsockopt(srv, SOL_SOCKET, SO_REUSEPORT, &opt, sizeof(opt));
#endif

    struct sockaddr_in srv_addr;
    memset(&srv_addr, 0, sizeof(srv_addr));
    srv_addr.sin_family = AF_INET;
    srv_addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    srv_addr.sin_port = htons((uint16_t)port);

    if (bind(srv, (struct sockaddr *)&srv_addr, sizeof(srv_addr)) < 0) {
        perror("bind");
        close(srv);
        return 1;
    }

    if (listen(srv, 10) < 0) {
        perror("listen");
        close(srv);
        return 1;
    }

    if (daemon_mode) {
        pid_t pid = fork();
        if (pid < 0) {
            perror("fork");
            close(srv);
            return 1;
        }
        if (pid > 0) {
            // Parent prints confirmation and exits cleanly immediately
            printf("Forwarding 127.0.0.1:%d -> @%s (PID: %d)\n", port, sock_name, pid);
            fflush(stdout);
            return 0;
        }

        // Child process detaches from terminal
        signal(SIGHUP, SIG_IGN);
        setsid();
        close(STDIN_FILENO);
        close(STDOUT_FILENO);
        close(STDERR_FILENO);
        int devnull = open("/dev/null", O_RDWR);
        if (devnull >= 0) {
            dup2(devnull, STDIN_FILENO);
            dup2(devnull, STDOUT_FILENO);
            dup2(devnull, STDERR_FILENO);
            if (devnull > STDERR_FILENO) close(devnull);
        }
    } else {
        printf("Forwarding 127.0.0.1:%d -> @%s (Press Ctrl+C to stop)\n", port, sock_name);
        fflush(stdout);
    }

    while (running) {
        struct sockaddr_in cli_addr;
        socklen_t cli_len = sizeof(cli_addr);
        int cli_fd = accept(srv, (struct sockaddr *)&cli_addr, &cli_len);
        if (cli_fd < 0) {
            if (errno == EINTR) continue;
            break;
        }

        int unix_fd = connect_abstract(sock_name);
        if (unix_fd < 0) {
            close(cli_fd);
            continue;
        }

        pid_t worker = fork();
        if (worker == 0) {
            close(srv);
            relay_loop(cli_fd, unix_fd);
            exit(0);
        } else {
            close(cli_fd);
            close(unix_fd);
        }
    }

    close(srv);
    return 0;
}
