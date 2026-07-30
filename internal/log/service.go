package log

import (
	"fmt"
	"strings"

	"easy-proxy/internal/output"
)

type Logger interface {
	Log(entry Entry)
}

type Service struct {
	showHeaders bool
	showDetails bool
}

func NewService(showHeaders, showDetails bool) *Service {
	return &Service{
		showHeaders: showHeaders,
		showDetails: showDetails,
	}
}

func (s *Service) Log(entry Entry) {
	var b strings.Builder

	b.WriteString(output.Cyan(entry.Method))
	b.WriteString(" ")
	b.WriteString(entry.Path)

	if entry.StatusCode == 0 {
		b.WriteString(output.RedBold(" -> " + entry.ResponseMessage))
	} else if entry.StatusCode >= 400 {
		b.WriteString(output.RedBold(fmt.Sprintf(" -> %d %s", entry.StatusCode, entry.ResponseMessage)))
	} else {
		b.WriteString(output.GreenBold(fmt.Sprintf(" -> %d %s", entry.StatusCode, entry.ResponseMessage)))
	}

	ms := entry.Duration.Milliseconds()
	if entry.Duration.Microseconds() < 1000 {
		ms = 1
	}
	b.WriteString(output.PurpleBold(fmt.Sprintf(" (%dms)", ms)))

	if s.showDetails {
		b.WriteString(output.White("\n-------------------------------------------------------------\n"))

		b.WriteString(output.WhiteBold("Timestamp: "))
		b.WriteString(output.White(entry.Timestamp.Format("2006-01-02T15:04:05.999")))
		b.WriteString("\n")

		if s.showHeaders && entry.RequestHeaders != nil {
			b.WriteString(output.WhiteBold("Headers:"))
			for k, v := range entry.RequestHeaders {
				b.WriteString(output.White(fmt.Sprintf("\n  %s: %s", k, v)))
			}
			b.WriteString("\n")
		}

		if len(entry.RequestBody) > 0 {
			b.WriteString(output.WhiteBold("Request Body: "))
			b.WriteString(output.White(getBodyAsString(entry.RequestBody, entry.RequestHeaders)))
			b.WriteString("\n")
		}

		if len(entry.ResponseBody) > 0 {
			b.WriteString(output.WhiteBold("Response Body: "))
			b.WriteString(output.White(getBodyAsString(entry.ResponseBody, entry.ResponseHeaders)))
			b.WriteString("\n")
		}

		b.WriteString(output.White("-------------------------------------------------------------\n"))
	}

	b.WriteString("\n")
	fmt.Print(b.String())
}

func getBodyAsString(body []byte, headers map[string]string) string {
	if len(body) == 0 {
		return "(empty)"
	}
	contentEncoding := headers["Content-Encoding"]
	data, err := uncompressIfGzipped(body, contentEncoding)
	if err != nil {
		return "(error decompressing body) " + string(body)
	}
	return string(data)
}
