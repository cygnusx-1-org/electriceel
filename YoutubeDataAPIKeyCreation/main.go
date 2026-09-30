// Command ydakc makes a YouTube Data API key in the user's Google Cloud account, with no
// OAuth client of their own and nothing else installed: it signs in with the Google Cloud CLI's
// public client, compiled in (see auth.go).
//
// It makes only what the key needs, and reuses whatever is already there:
//   - the project (yt-data-api-key-creation by default), no billing account;
//   - the YouTube Data API turned on in it;
//   - one API key, restricted to the YouTube Data API.
//
// The key goes to stdout, everything else to stderr.
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"regexp"
	"strings"
)

const (
	defaultProject = "yt-data-api-key-creation"
	defaultKeyName = "YouTube Data API key"
	// Also what gcloud and Terraform read: a token from anywhere skips the sign-in
	tokenEnv = "GOOGLE_OAUTH_ACCESS_TOKEN"
)

// https://cloud.google.com/resource-manager/docs/creating-managing-projects#before_you_begin
var projectIDPattern = regexp.MustCompile(`^[a-z][a-z0-9-]{4,28}[a-z0-9]$`)

func main() {
	project := flag.String("project", defaultProject, "ID of the Google Cloud project to use, made if it doesn't exist")
	keyName := flag.String("name", defaultKeyName, "display name of the API key, reused if a key with it exists")
	noBrowser := flag.Bool("no-browser", false, "don't open the sign-in page, only print its address")
	flag.Usage = func() {
		fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s [-project ID] [-name NAME] [-no-browser]\n\n", os.Args[0])
		fmt.Fprintf(flag.CommandLine.Output(), "Signs in with Google in the browser, or takes an access token from $%s.\n\n", tokenEnv)
		flag.PrintDefaults()
	}
	flag.Parse()

	if !projectIDPattern.MatchString(*project) {
		fatalf("%q isn't a valid project ID: 6 to 30 lowercase letters, digits or hyphens, starting with a letter and not ending with a hyphen", *project)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	token := strings.TrimSpace(os.Getenv(tokenEnv))
	if token == "" {
		var err error
		if token, err = signIn(ctx, !*noBrowser); err != nil {
			fatalf("%v", err)
		}
	}

	key, err := newClient(token).run(ctx, *project, *keyName)
	if err != nil {
		fatalf("%v", err)
	}

	fmt.Println(key)
}

func logf(format string, args ...any) {
	fmt.Fprintf(os.Stderr, format+"\n", args...)
}

func fatalf(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "error: "+format+"\n", args...)
	os.Exit(1)
}
