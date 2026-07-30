package proxy

import (
	"io"
	"net/http"
	"testing"
)

func TestServerLifecycle(t *testing.T) {
	env := NewTestEnv(t)

	if !env.Proxy.IsRunning() {
		t.Fatal("server should be running")
	}

	err := env.Proxy.Stop()
	if err != nil {
		t.Fatalf("failed to stop server: %v", err)
	}

	if env.Proxy.IsRunning() {
		t.Fatal("server should not be running")
	}
}

func TestForwardHTTPMethods(t *testing.T) {
	tests := []struct {
		name   string
		method string
		status int
		body   string
	}{
		{"GET", http.MethodGet, http.StatusOK, "ok"},
		{"POST", http.MethodPost, http.StatusCreated, "created"},
		{"PUT", http.MethodPut, http.StatusOK, "updated"},
		{"PATCH", http.MethodPatch, http.StatusOK, "updated"},
		{"DELETE", http.MethodDelete, http.StatusNoContent, ""},
		{"HEAD", http.MethodHead, http.StatusOK, ""},
		{"OPTION", http.MethodOptions, http.StatusOK, ""},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			env := NewTestEnv(t)

			env.Mock.AddEndpoint(MockEndpoint{
				Method: tt.method,
				Path:   "/api",
				Status: tt.status,
				Body:   []byte(tt.body),
			})

			resp, err := env.Do(tt.method, "/api", nil)
			if err != nil {
				t.Fatal(err)
			}
			defer resp.Body.Close()

			if resp.StatusCode != tt.status {
				t.Fatalf("expected status %d, got %d", tt.status, resp.StatusCode)
			}

			body, err := io.ReadAll(resp.Body)
			if err != nil {
				t.Fatal(err)
			}

			if string(body) != tt.body {
				t.Fatalf("expected body %q, got %q", tt.body, string(body))
			}
		})
	}
}
