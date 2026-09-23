#!/usr/bin/env python3
"""Temporary Gate 3B HTTP -> SOCKS5 bridge.

The Android TV emulator is configured with `-http-proxy` to talk to this process. It forwards
HTTP (absolute-form) and HTTPS (CONNECT) through a SOCKS5 tunnel (`ssh -D`) to the approved
Contabo egress host. Binds the runner loopback only; opens no external listener and runs only
for the duration of the Gate 3B job.
"""

import select
import socket
import socketserver
import struct
import sys

LISTEN_HOST = "127.0.0.1"
LISTEN_PORT = 8118
SOCKS_HOST = "127.0.0.1"
SOCKS_PORT = 1080


def recvn(sock, count):
    data = b""
    while len(data) < count:
        chunk = sock.recv(count - len(data))
        if not chunk:
            raise OSError("unexpected eof")
        data += chunk
    return data


def resolve_ipv4(host, port):
    """Resolve the destination to an IPv4 literal on the runner.

    Sending an IPv4 literal (SOCKS5 ATYP=1) makes the SSH server connect to that IPv4
    address, so the egress identity observed by the destination is the approved host's
    IPv4 instead of its preferred IPv6.
    """
    infos = socket.getaddrinfo(host, port, socket.AF_INET, socket.SOCK_STREAM)
    if not infos:
        raise OSError("no IPv4 address for %s" % host)
    return infos[0][4][0]


def socks5_connect(host, port):
    ipv4 = resolve_ipv4(host, port)
    packed = socket.inet_aton(ipv4)
    sock = socket.create_connection((SOCKS_HOST, SOCKS_PORT), timeout=30)
    try:
        sock.sendall(b"\x05\x01\x00")
        if recvn(sock, 2) != b"\x05\x00":
            raise OSError("socks5 greeting rejected")
        sock.sendall(b"\x05\x01\x00\x01" + packed + struct.pack(">H", port))
        head = recvn(sock, 4)
        if head[0] != 5 or head[1] != 0:
            raise OSError("socks5 connect failed: %d" % head[1])
        atyp = head[3]
        if atyp == 1:
            recvn(sock, 4 + 2)
        elif atyp == 4:
            recvn(sock, 16 + 2)
        elif atyp == 3:
            recvn(sock, recvn(sock, 1)[0] + 2)
        else:
            raise OSError("socks5 unknown address type")
        return sock
    except OSError:
        sock.close()
        raise


def relay(client, upstream):
    try:
        while True:
            readable, _, _ = select.select([client, upstream], [], [], 120)
            if not readable:
                break
            for source in readable:
                target = upstream if source is client else client
                data = source.recv(65536)
                if not data:
                    return
                target.sendall(data)
    except OSError:
        pass
    finally:
        for sock in (client, upstream):
            try:
                sock.close()
            except OSError:
                pass


def read_headers(reader):
    while True:
        line = reader.readline(65536)
        if line in (b"\r\n", b"\n", b""):
            return


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        # Connection counter only; never logs destination hostnames.
        sys.stderr.write("proxy-conn\n")
        sys.stderr.flush()
        request_line = self.rfile.readline(65536)
        if not request_line:
            return
        parts = request_line.decode("latin-1").split()
        if len(parts) < 2:
            self.wfile.write(b"HTTP/1.1 400 Bad Request\r\n\r\n")
            return
        method, target = parts[0], parts[1]

        if method.upper() == "CONNECT":
            host, _, port = target.rpartition(":")
            try:
                upstream = socks5_connect(host, int(port))
            except (OSError, ValueError):
                self.wfile.write(b"HTTP/1.1 502 Bad Gateway\r\n\r\n")
                return
            read_headers(self.rfile)
            self.wfile.write(b"HTTP/1.1 200 Connection established\r\n\r\n")
            self.wfile.flush()
            relay(self.connection, upstream)
            return

        if method.upper() in ("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH") and \
                target.lower().startswith("http://"):
            rest = target[7:]
            host_port, _, path = rest.partition("/")
            path = "/" + path
            host, _, port = host_port.partition(":")
            try:
                upstream = socks5_connect(host, int(port or 80))
            except (OSError, ValueError):
                self.wfile.write(b"HTTP/1.1 502 Bad Gateway\r\n\r\n")
                return
            upstream.sendall((method + " " + path + " HTTP/1.1\r\n").encode("latin-1"))
            while True:
                header = self.rfile.readline(65536)
                if header in (b"\r\n", b"\n", b""):
                    break
                upstream.sendall(header)
            upstream.sendall(b"\r\n")
            relay(self.connection, upstream)
            return

        self.wfile.write(b"HTTP/1.1 400 Bad Request\r\n\r\n")


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    server = Server((LISTEN_HOST, LISTEN_PORT), Handler)
    sys.stderr.write("gate3b-http-socks-bridge listening on %s:%d\n" % (LISTEN_HOST, LISTEN_PORT))
    sys.stderr.flush()
    server.serve_forever()


if __name__ == "__main__":
    main()
