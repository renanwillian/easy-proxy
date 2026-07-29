package cmd

import (
	"os"

	"github.com/spf13/cobra"
)

// rootCmd represents the base command when called without any subcommands
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
}

// Execute adds all child commands to the root command and sets flags appropriately.
// This is called by main.main(). It only needs to happen once to the rootCmd.
func Execute() {
	err := rootCmd.Execute()
	if err != nil {
		os.Exit(1)
	}
}

func init() {
	// Here you will define your flags and configuration settings.
	// Cobra supports persistent flags, which, if defined here,
	// will be global for your application.

	// rootCmd.PersistentFlags().StringVar(&cfgFile, "config", "", "config file (default is $HOME/.easy-proxy.yaml)")

	// Cobra also supports local flags, which will only run
	// when this action is called directly.
	rootCmd.Flags().BoolP("toggle", "t", false, "Help message for toggle")
}
