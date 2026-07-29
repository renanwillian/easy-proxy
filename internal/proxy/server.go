package proxy

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"easy-proxy/internal/log"
)

type Server struct {
	port      int
	targetURL string
	logSvc    *log.Service
	srv       *http.Server
	running   bool
}

func NewServer(port int, targetURL string, logSvc *log.Service) *Server {
	return &Server{
		port:      port,
		targetURL: sanitizeURL(targetURL),
		logSvc:    logSvc,
	}
}

func (s *Server) Start() error {
	handler := NewHandler(s.targetURL, s.logSvc)
	s.srv = &http.Server{
		Addr:    fmt.Sprintf(":%d", s.port),
		Handler: handler,
	}
	s.running = true
	return s.srv.ListenAndServe()
}

func (s *Server) Stop() error {
	if s.srv != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		s.running = false
		return s.srv.Shutdown(ctx)
	}
	return nil
}

func (s *Server) IsRunning() bool {
	return s.running
}

func (s *Server) TargetURL() string {
	return s.targetURL
}

func (s *Server) Port() int {
	return s.port
}
