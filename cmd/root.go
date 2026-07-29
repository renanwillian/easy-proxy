package cmd

import (
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"easy-proxy/internal/log"
	"easy-proxy/internal/proxy"

	"github.com/spf13/cobra"
)

var (
	targetURL string
	port      int
	details   bool
	headers   bool
)

var rootCmd = &cobra.Command{
	Use:   "easy-proxy <target-url>",
	Short: "Expose any HTTP server locally through a reverse proxy.",
	Long: `easy-proxy is a dead-simple reverse proxy for local development.

It lets you expose any HTTP or HTTPS server through a local port,
making it easy to switch between development, staging, QA, and
production environments without changing application code.

Features:
  • Instant reverse proxy setup
  • Request and response logging
  • Optional detailed inspection of payloads and headers
  • Lightweight single-binary CLI

Examples:
  easy-proxy https://httpbin.org
  easy-proxy https://httpbin.org --port 8080
  easy-proxy https://api.example.com --details`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		targetURL = args[0]
		return run()
	},
}

func Execute() {
	err := rootCmd.Execute()
	if err != nil {
		os.Exit(1)
	}
}

func init() {
	rootCmd.Flags().IntVarP(&port, "port", "p", 8000, "Port the proxy will listen on")
	rootCmd.Flags().BoolVar(&details, "details", false, "Show request/response details")
	rootCmd.Flags().BoolVar(&headers, "headers", false, "Show request/response headers")
}

func run() error {
	logSvc := log.NewService(headers, details)
	srv := proxy.NewServer(port, targetURL, logSvc)

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-sigCh
		fmt.Println("\nShutting down...")
		srv.Stop()
	}()

	fmt.Printf("Proxy server running on http://localhost:%d and redirecting to %s\n", srv.Port(), srv.TargetURL())
	fmt.Println()

	return srv.Start()
}
