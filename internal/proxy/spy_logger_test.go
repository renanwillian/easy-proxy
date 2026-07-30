package proxy

import (
	"sync"

	"easy-proxy/internal/log"
)

type SpyLogger struct {
	mu      sync.Mutex
	entries []log.Entry
}

func NewSpyLogger() *SpyLogger {
	return &SpyLogger{}
}

func (s *SpyLogger) Log(entry log.Entry) {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.entries = append(s.entries, entry)
}

func (s *SpyLogger) Last() *log.Entry {
	s.mu.Lock()
	defer s.mu.Unlock()

	if len(s.entries) == 0 {
		return nil
	}

	entry := s.entries[len(s.entries)-1]
	return &entry
}

func (s *SpyLogger) Entries() []log.Entry {
	s.mu.Lock()
	defer s.mu.Unlock()

	entries := make([]log.Entry, len(s.entries))
	copy(entries, s.entries)

	return entries
}

func (s *SpyLogger) Len() int {
	s.mu.Lock()
	defer s.mu.Unlock()

	return len(s.entries)
}
