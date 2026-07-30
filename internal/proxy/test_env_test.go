package proxy

import (
	"io"
	"net/http"
	"strconv"
	"testing"
	"time"
)

type TestEnv struct {
	t *testing.T

	Mock   *MockServer
	Logger *SpyLogger
	Proxy  *Server
	Client *http.Client
}

func NewTestEnv(t *testing.T) *TestEnv {
	t.Helper()

	mock := NewMockServer()
	logger := NewSpyLogger()

	proxy := NewServer(0, mock.URL(), logger)

	go func() {
		if err := proxy.Start(); err != nil {
			t.Errorf("proxy start failed: %v", err)
		}
	}()

	waitForPort(t, proxy)

	env := &TestEnv{
		t:      t,
		Mock:   mock,
		Logger: logger,
		Proxy:  proxy,
		Client: &http.Client{Timeout: 2 * time.Second},
	}

	t.Cleanup(func() {
		_ = proxy.Stop()
		mock.Close()
	})

	return env
}

func (e *TestEnv) Do(method, path string, body io.Reader) (*http.Response, error) {
	req, err := http.NewRequest(
		method,
		"http://localhost:"+strconv.Itoa(e.Proxy.Port())+path,
		body,
	)
	if err != nil {
		return nil, err
	}

	return e.Client.Do(req)
}

func waitForPort(t *testing.T, proxy *Server) {
	t.Helper()

	timeout := time.After(time.Second)

	for proxy.Port() == 0 {
		select {
		case <-timeout:
			t.Fatal("proxy did not start")
		default:
			time.Sleep(time.Millisecond)
		}
	}
}
