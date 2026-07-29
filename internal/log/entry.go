package log

import "time"

type Entry struct {
	Method          string
	Path            string
	StatusCode      int
	ResponseMessage string
	Duration        time.Duration
	Timestamp       time.Time
	RequestHeaders  map[string]string
	RequestBody     []byte
	ResponseHeaders map[string]string
	ResponseBody    []byte
}
