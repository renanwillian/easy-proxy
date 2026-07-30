package proxy

import (
	"bytes"
	"errors"
	"io"
	"net"
	"net/http"
	"strings"
	"syscall"
	"time"

	"easy-proxy/internal/log"
)

type Handler struct {
	targetURL string
	logSvc    log.Logger
	client    *http.Client
}

func NewHandler(targetURL string, logSvc log.Logger, client *http.Client) *Handler {
	if client == nil {
		client = &http.Client{
			Timeout: 10 * time.Second,
		}
	}

	return &Handler{
		targetURL: targetURL,
		logSvc:    logSvc,
		client:    client,
	}
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	start := time.Now()
	entry := log.Entry{
		Method:    r.Method,
		Path:      r.URL.RequestURI(),
		Timestamp: time.Now(),
	}

	entry.RequestHeaders = make(map[string]string)
	for k, v := range r.Header {
		entry.RequestHeaders[k] = strings.Join(v, ",")
	}

	reqBody, _ := io.ReadAll(r.Body)
	r.Body.Close()
	entry.RequestBody = reqBody

	upstreamURL := h.targetURL + r.URL.RequestURI()

	upstreamReq, err := http.NewRequest(r.Method, upstreamURL, bytes.NewReader(reqBody))
	if err != nil {
		writeError(w, http.StatusInternalServerError, "Internal Server Error", &entry, h.logSvc)
		return
	}

	upstreamReq.Header = r.Header.Clone()

	resp, err := h.client.Do(upstreamReq)
	if err != nil {
		handleError(w, err, &entry, h.logSvc)
		return
	}
	defer resp.Body.Close()

	entry.StatusCode = resp.StatusCode
	entry.ResponseMessage = http.StatusText(resp.StatusCode)

	entry.ResponseHeaders = make(map[string]string)
	for k, v := range resp.Header {
		entry.ResponseHeaders[k] = strings.Join(v, ",")
	}

	for k, v := range resp.Header {
		w.Header()[k] = v
	}

	respBody, readErr := io.ReadAll(resp.Body)
	if readErr != nil {
		w.WriteHeader(http.StatusInternalServerError)
	} else {
		entry.ResponseBody = respBody
		w.WriteHeader(resp.StatusCode)
		w.Write(respBody)
	}

	entry.Duration = time.Since(start)
	h.logSvc.Log(entry)
}

func writeError(w http.ResponseWriter, status int, msg string, entry *log.Entry, logSvc log.Logger) {
	entry.StatusCode = status
	entry.ResponseMessage = msg
	http.Error(w, msg, status)
	logSvc.Log(*entry)
}

func handleError(w http.ResponseWriter, err error, entry *log.Entry, logSvc log.Logger) {
	var netErr net.Error
	if errors.As(err, &netErr) && netErr.Timeout() {
		writeError(w, http.StatusGatewayTimeout, "Gateway Timeout: Upstream server took too long to respond.", entry, logSvc)
		return
	}

	if errors.Is(err, syscall.ECONNREFUSED) || strings.Contains(err.Error(), "connection refused") {
		writeError(w, http.StatusBadGateway, "Bad Gateway: Unable to connect to upstream server.", entry, logSvc)
		return
	}

	writeError(w, http.StatusInternalServerError, "Internal Server Error", entry, logSvc)
}
