package proxy

import (
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"sync"
)

type MockEndpoint struct {
	Method string
	Path   string

	Status  int
	Headers http.Header
	Body    []byte
}

type CapturedRequest struct {
	Method string
	Path   string
	Query  url.Values
	Header http.Header
	Body   []byte
}

type MockServer struct {
	server *httptest.Server

	mu        sync.Mutex
	endpoints map[string]MockEndpoint
	requests  []CapturedRequest
}

func NewMockServer() *MockServer {
	m := &MockServer{
		endpoints: make(map[string]MockEndpoint),
	}

	m.server = httptest.NewServer(http.HandlerFunc(m.handle))

	return m
}

func (m *MockServer) AddEndpoint(e MockEndpoint) {
	m.mu.Lock()
	defer m.mu.Unlock()

	key := e.Method + ":" + e.Path
	m.endpoints[key] = e
}

func (m *MockServer) URL() string {
	return m.server.URL
}

func (m *MockServer) Close() {
	m.server.Close()
}

func (m *MockServer) LastRequest() CapturedRequest {
	m.mu.Lock()
	defer m.mu.Unlock()

	if len(m.requests) == 0 {
		return CapturedRequest{}
	}

	return m.requests[len(m.requests)-1]
}

func (m *MockServer) Requests() []CapturedRequest {
	m.mu.Lock()
	defer m.mu.Unlock()

	reqs := make([]CapturedRequest, len(m.requests))
	copy(reqs, m.requests)

	return reqs
}

func (m *MockServer) RequestCount() int {
	m.mu.Lock()
	defer m.mu.Unlock()

	return len(m.requests)
}

func (m *MockServer) Reset() {
	m.mu.Lock()
	defer m.mu.Unlock()

	m.requests = nil
}

func (m *MockServer) handle(w http.ResponseWriter, r *http.Request) {
	body, _ := io.ReadAll(r.Body)

	req := CapturedRequest{
		Method: r.Method,
		Path:   r.URL.Path,
		Query:  r.URL.Query(),
		Header: r.Header.Clone(),
		Body:   body,
	}

	m.mu.Lock()
	m.requests = append(m.requests, req)

	key := r.Method + ":" + r.URL.Path
	endpoint, ok := m.endpoints[key]
	m.mu.Unlock()

	if !ok {
		http.NotFound(w, r)
		return
	}

	for k, values := range endpoint.Headers {
		for _, v := range values {
			w.Header().Add(k, v)
		}
	}

	w.WriteHeader(endpoint.Status)

	if len(endpoint.Body) > 0 {
		_, _ = w.Write(endpoint.Body)
	}
}
