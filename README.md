# easy-proxy

A dead-simple CLI tool that instantly maps external servers to your localhost.

Fast, elegant, and perfect for modern development workflows.

## 📦 Installation
Pre-built binaries are available for Linux (x86_64 and aarch64) and macOS (Apple Silicon).

Install the latest release by running the command for your platform:
```bash
# Linux x86_64
curl -sL "https://github.com/renanwillian/easy-proxy/releases/latest/download/easy-proxy-linux-x86_64.tar.gz" | tar -xz && sudo install -D easy-proxy -t /usr/local/bin/

# Linux aarch64
curl -sL "https://github.com/renanwillian/easy-proxy/releases/latest/download/easy-proxy-linux-aarch64.tar.gz" | tar -xz && sudo install -D easy-proxy -t /usr/local/bin/

# macOS (Apple Silicon)
curl -sL "https://github.com/renanwillian/easy-proxy/releases/latest/download/easy-proxy-macos-aarch64.tar.gz" | tar -xz && sudo install easy-proxy /usr/local/bin/
```

Confirm that easy-proxy was installed correctly by checking the version:
```bash
easy-proxy --version
```

## 📖 Usage
```text
$ easy-proxy --help
Usage: easy-proxy [-hkV] [--details] [--headers] [--host=<host>]
                  [--max-body-size=<bytes>] [--no-color] [--port=<port>]
                  [--show-secrets]
                  [--timeout=<seconds>] TARGET_URL
Starts a reverse proxy server.

  TARGET_URL        The target URL for the proxy.
  --details         Show the details of each request/response (default: false).
  --headers         Show the headers of each request/response (default: false).
  --host=<host>     The address the server binds to. Use 0.0.0.0 to accept
                      connections from other machines (default: 127.0.0.1).
  --max-body-size=<bytes>
                    Maximum number of body bytes shown with --details; 0
                      means no limit (default: 4096).
  --no-color        Disable colored output. Also disabled when NO_COLOR is set
                      or the output is not a terminal.
  --port=<port>     The port on which the server will run (default: 8000).
  --show-secrets    Show credentials (Authorization, cookies, API keys) in
                      logged headers instead of masking them (default: false).
  --timeout=<seconds>
                    How long to wait for the target to start responding
                      (default: 10).
  -k, --insecure    Accept any TLS certificate from the target, e.g. self-signed
                      ones (default: false).
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
