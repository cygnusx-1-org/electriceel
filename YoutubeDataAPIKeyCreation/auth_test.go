package main

import (
	"crypto/sha256"
	"encoding/base64"
	"net/url"
	"testing"
)

func TestCodeFromPastedURL(t *testing.T) {
	tests := []struct {
		raw     string
		code    string
		wantErr bool
	}{
		{"http://127.0.0.1:41234/?state=s1&code=4/abc&scope=x", "4/abc", false},
		{"http://127.0.0.1:41234/?state=other&code=4/abc", "", true},
		{"http://127.0.0.1:41234/?state=s1", "", true},
		{"http://127.0.0.1:41234/?error=access_denied&state=s1", "", true},
		{"not a url %zz", "", true},
	}

	for _, test := range tests {
		code, err := codeFromURL(test.raw, "s1")
		if (err != nil) != test.wantErr || code != test.code {
			t.Errorf("%q: code %q, error %v", test.raw, code, err)
		}
	}
}

func TestAuthorizationURLUsesPKCE(t *testing.T) {
	raw := authorizationURL("http://127.0.0.1:1/", "state", "verifier")

	parsed, err := url.Parse(raw)
	if err != nil {
		t.Fatal(err)
	}
	query := parsed.Query()

	sum := sha256.Sum256([]byte("verifier"))
	if query.Get("code_challenge") != base64.RawURLEncoding.EncodeToString(sum[:]) || query.Get("code_challenge_method") != "S256" {
		t.Errorf("challenge %q %q", query.Get("code_challenge"), query.Get("code_challenge_method"))
	}
	if query.Get("client_id") != oauthClientID || query.Get("redirect_uri") != "http://127.0.0.1:1/" || query.Get("state") != "state" {
		t.Errorf("query %v", query)
	}
}
