package log

import (
	"bytes"
	"compress/gzip"
	"io"
)

func uncompressIfGzipped(data []byte, contentEncoding string) ([]byte, error) {
	if contentEncoding != "gzip" {
		return data, nil
	}
	return uncompress(data)
}

func uncompress(data []byte) ([]byte, error) {
	r, err := gzip.NewReader(bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	defer r.Close()
	return io.ReadAll(r)
}
