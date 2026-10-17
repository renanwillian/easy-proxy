# easy-proxy

A dead-simple CLI tool that instantly maps external servers to your localhost.

Fast, elegant, and perfect for modern development workflows.

## 📦 Installation
> Note: easy-proxy is currently available only for Linux systems (x86_64 architecture).

You can install it easily by running the following commands:
```bash
curl -sL "https://github.com/renanwillian/easy-proxy/releases/latest/download/easy-proxy-linux-x86_64.tar.gz" | tar -xz && sudo install -D easy-proxy -t /usr/local/bin/
```

Confirm that easy-proxy was installed correctly by checking the version:
```bash
easy-proxy --version
```

## 📖 Usage
```text
$ easy-proxy --help
Usage: easy-proxy [-hV] [--details] [--headers] [--host=<host>] [--port=<port>]
                  [--timeout=<seconds>] TARGET_URL
Starts a reverse proxy server.

  TARGET_URL        The target URL for the proxy.
  --details         Show the details of each request/response (default: false).
  --headers         Show the headers of each request/response (default: false).
  --host=<host>     The address the server binds to. Use 0.0.0.0 to accept
                      connections from other machines (default: 127.0.0.1).
  --port=<port>     The port on which the server will run (default: 8000).
  --timeout=<seconds>
                    How long to wait for the target to start responding
                      (default: 10).
  -h, --help        Show this help message and exit.
  -V, --version     Print version information and exit.
```

## 🚀 Example
```text
$ easy-proxy https://httpbin.org/ --port 8080
Proxy server running on http://localhost:8080 and redirecting to https://httpbin.org/
```

## ✨ Quick Tip
You can customize the server behavior using the --details, --headers, and --port flags.
Use -h or --help at any time to see all available options.


## 🛠 Development

### Building a native package
```shell
./mvnw -Pnative package
./target/easy-proxy --version
```

### Creating a release archive
```shell
cd ./target
tar -czvf easy-proxy-linux-x86_64.tar.gz easy-proxy
```
