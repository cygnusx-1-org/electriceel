package main

import (
	"bufio"
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"html"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"runtime"
	"strings"
	"time"
)

const (
	// The Google Cloud CLI's own installed-app client. Both values are public, in its source
	// (googlecloudsdk/core/config.py): an installed app's secret can't be kept, so Google doesn't treat it as one.
	// Signing in with it is what `gcloud auth login` does, so no OAuth client of the user's own is needed.
	oauthClientID     = "32555940559.apps.googleusercontent.com"
	oauthClientSecret = "ZmssLNjJy2998hD4CTg2ejr2"

	oauthAuthURL  = "https://accounts.google.com/o/oauth2/auth"
	oauthTokenURL = "https://oauth2.googleapis.com/token"

	cloudPlatformScope = "https://www.googleapis.com/auth/cloud-platform"
	oauthScopes        = "openid https://www.googleapis.com/auth/userinfo.email " + cloudPlatformScope

	signInTimeout = 5 * time.Minute
)

// signIn gets an access token for the user's Google Cloud account: the browser signs in and comes back to
// a server on the loopback address, or the user pastes the address it came back to (a browser on another machine)
func signIn(ctx context.Context, openBrowser bool) (string, error) {
	ctx, cancel := context.WithTimeout(ctx, signInTimeout)
	defer cancel()

	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return "", fmt.Errorf("can't listen for the sign-in: %w", err)
	}
	defer listener.Close()

	redirectURL := fmt.Sprintf("http://127.0.0.1:%d/", listener.Addr().(*net.TCPAddr).Port)
	state := randomString(24)
	verifier := randomString(64)

	codes := make(chan result, 2)

	server := &http.Server{
		Handler:           callbackHandler(state, codes),
		ReadHeaderTimeout: 10 * time.Second,
	}
	go server.Serve(listener)
	defer server.Close()

	go readPastedURL(state, codes)

	authURL := authorizationURL(redirectURL, state, verifier)

	logf("Sign in with the Google account to make the key in:\n\n  %s\n", authURL)
	logf("If the browser is on another machine, the page it lands on won't load: paste its address here.")

	if openBrowser {
		// Best effort: the address is printed anyway
		_ = startBrowser(authURL)
	}

	var code string
	select {
	case r := <-codes:
		if r.err != nil {
			return "", r.err
		}
		code = r.code
	case <-ctx.Done():
		return "", fmt.Errorf("no sign-in: %w", ctx.Err())
	}

	return exchangeCode(ctx, code, redirectURL, verifier)
}

type result struct {
	code string
	err  error
}

func authorizationURL(redirectURL, state, verifier string) string {
	challenge := sha256.Sum256([]byte(verifier))

	query := url.Values{
		"client_id":             {oauthClientID},
		"redirect_uri":          {redirectURL},
		"response_type":         {"code"},
		"scope":                 {oauthScopes},
		"state":                 {state},
		"code_challenge":        {base64.RawURLEncoding.EncodeToString(challenge[:])},
		"code_challenge_method": {"S256"},
		// One run needs one token, never a refresh token
		"access_type": {"online"},
		"prompt":      {"select_account"},
	}

	return oauthAuthURL + "?" + query.Encode()
}

func callbackHandler(state string, codes chan<- result) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Favicon and the like
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}

		code, err := codeFromQuery(r.URL.Query(), state)

		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		if err != nil {
			fmt.Fprintf(w, "<p>Sign-in failed: %s</p>", html.EscapeString(err.Error()))
		} else {
			fmt.Fprint(w, "<p>Signed in. You can close this tab and go back to the terminal.</p>")
		}

		select {
		case codes <- result{code, err}:
		default:
		}
	})
}

// readPastedURL takes the address the browser landed on from stdin, for a browser that can't reach the loopback server
func readPastedURL(state string, codes chan<- result) {
	scanner := bufio.NewScanner(os.Stdin)

	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		code, err := codeFromURL(line, state)
		if err != nil {
			logf("That's not the address the sign-in landed on (%v): paste the whole address, starting with http://127.0.0.1", err)
			continue
		}

		select {
		case codes <- result{code: code}:
		default:
		}

		return
	}
}

func codeFromURL(raw, state string) (string, error) {
	parsed, err := url.Parse(raw)
	if err != nil {
		return "", err
	}

	return codeFromQuery(parsed.Query(), state)
}

func codeFromQuery(query url.Values, state string) (string, error) {
	if errorCode := query.Get("error"); errorCode != "" {
		if errorCode == "access_denied" {
			return "", errors.New("access wasn't allowed on the Google sign-in page")
		}

		return "", fmt.Errorf("Google sign-in: %s", errorCode)
	}

	if query.Get("state") != state {
		return "", errors.New("the address is from another sign-in")
	}

	code := query.Get("code")
	if code == "" {
		return "", errors.New("the address has no sign-in code")
	}

	return code, nil
}

func exchangeCode(ctx context.Context, code, redirectURL, verifier string) (string, error) {
	form := url.Values{
		"code":          {code},
		"client_id":     {oauthClientID},
		"client_secret": {oauthClientSecret},
		"redirect_uri":  {redirectURL},
		"grant_type":    {"authorization_code"},
		"code_verifier": {verifier},
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, oauthTokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("can't get the access token: %w", err)
	}
	defer resp.Body.Close()

	var token struct {
		AccessToken      string `json:"access_token"`
		Scope            string `json:"scope"`
		Error            string `json:"error"`
		ErrorDescription string `json:"error_description"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&token); err != nil {
		return "", fmt.Errorf("can't read the access token (HTTP %d): %w", resp.StatusCode, err)
	}

	if token.Error != "" {
		return "", fmt.Errorf("can't get the access token: %s: %s", token.Error, token.ErrorDescription)
	}

	if token.AccessToken == "" {
		return "", fmt.Errorf("no access token (HTTP %d)", resp.StatusCode)
	}

	// The consent page lets the user untick scopes
	if !strings.Contains(" "+token.Scope+" ", " "+cloudPlatformScope+" ") {
		return "", errors.New("access to Google Cloud wasn't allowed on the sign-in page: run again and leave it ticked")
	}

	return token.AccessToken, nil
}

func startBrowser(target string) error {
	var cmd *exec.Cmd

	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", target)
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", target)
	default:
		cmd = exec.Command("xdg-open", target)
	}

	if err := cmd.Start(); err != nil {
		return err
	}

	// Don't leave a zombie behind
	go cmd.Wait()

	return nil
}

func randomString(bytes int) string {
	b := make([]byte, bytes)
	// Never fails on a supported platform (crypto/rand panics rather than return an error since Go 1.24)
	_, _ = rand.Read(b)

	return base64.RawURLEncoding.EncodeToString(b)
}
