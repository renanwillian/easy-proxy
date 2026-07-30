package proxy

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"sync/atomic"
	"time"

	"easy-proxy/internal/log"
)

type Server struct {
	port      int
	targetURL string
	logSvc    log.Logger
	srv       *http.Server
	listener  net.Listener
	running   atomic.Bool
}

func NewServer(port int, targetURL string, logSvc log.Logger) *Server {
	return &Server{
		port:      port,
		targetURL: sanitizeURL(targetURL),
		logSvc:    logSvc,
	}
}

func (s *Server) Start() error {
	handler := NewHandler(s.targetURL, s.logSvc)
	s.srv = &http.Server{Handler: handler}

	addr := fmt.Sprintf(":%d", s.port)
	listener, err := net.Listen("tcp", addr)
	if err != nil {
		return err
	}
	s.listener = listener
	if s.port == 0 {
		s.port = listener.Addr().(*net.TCPAddr).Port
	}
	s.running.Store(true)

	return s.srv.Serve(listener)
}

func (s *Server) Stop() error {
	if s.srv != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		s.running.Store(false)
		return s.srv.Shutdown(ctx)
	}
	return nil
}

func (s *Server) IsRunning() bool {
	return s.running.Load()
}

func (s *Server) TargetURL() string {
	return s.targetURL
}

func (s *Server) Port() int {
	return s.port
}
