package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const (
	youtubeService = "youtube.googleapis.com"
	apiKeysService = "apikeys.googleapis.com"
)

// client calls the Google Cloud APIs as the signed-in user
type client struct {
	http  *http.Client
	token string

	// Base URLs, the fake server's in tests
	resourceManager string
	serviceUsage    string
	apiKeys         string

	// Where the API Keys calls are billed once the API had to be turned on in the project (see apiKeysCall)
	userProject string

	pollInterval time.Duration
	// How long an operation, or a change that has to reach every Google server, can take
	timeout time.Duration
}

func newClient(token string) *client {
	return &client{
		http:            &http.Client{Timeout: time.Minute},
		token:           token,
		resourceManager: "https://cloudresourcemanager.googleapis.com",
		serviceUsage:    "https://serviceusage.googleapis.com",
		apiKeys:         "https://apikeys.googleapis.com",
		pollInterval:    time.Second,
		timeout:         3 * time.Minute,
	}
}

// run makes whatever of the project, the YouTube Data API and the key is missing, and returns the key
func (c *client) run(ctx context.Context, project, keyName string) (string, error) {
	projectID, created, err := c.ensureProject(ctx, project)
	if err != nil {
		return "", err
	}

	if err := c.ensureService(ctx, projectID, youtubeService, created); err != nil {
		return "", err
	}

	return c.ensureKey(ctx, projectID, keyName)
}

type project struct {
	ProjectID      string `json:"projectId"`
	Name           string `json:"name"`
	LifecycleState string `json:"lifecycleState"`
}

// ensureProject finds the user's project with the ID, or else with it as the name (the Console gives a project
// another ID when the one typed is taken), or makes it. It returns the project's ID and whether it's new.
func (c *client) ensureProject(ctx context.Context, name string) (string, bool, error) {
	var existing project

	err := c.do(ctx, http.MethodGet, c.resourceManager+"/v1/projects/"+name, nil, &existing, "")
	if err == nil {
		switch existing.LifecycleState {
		case "ACTIVE":
			logf("Using project %s", name)
			return name, false, nil
		case "DELETE_REQUESTED":
			return "", false, fmt.Errorf("project %s is pending deletion: restore it at https://console.cloud.google.com/cloud-resource-manager, or pick another with -project", name)
		default:
			return "", false, fmt.Errorf("project %s can't be used, its state is %s", name, existing.LifecycleState)
		}
	}

	// A project of another account is a 403, same as a missing one
	var apiErr *apiError
	if !errors.As(err, &apiErr) || (apiErr.HTTPStatus != http.StatusForbidden && apiErr.HTTPStatus != http.StatusNotFound) {
		return "", false, fmt.Errorf("can't look up project %s: %w", name, err)
	}

	named, err := c.findProjectByName(ctx, name)
	if err != nil {
		return "", false, fmt.Errorf("can't look for a project named %s: %w", name, err)
	}
	if named != "" {
		logf("Using project %s, named %s", named, name)
		return named, false, nil
	}

	logf("Creating project %s", name)

	var op operation
	body := map[string]any{"projectId": name, "name": name}
	if err := c.do(ctx, http.MethodPost, c.resourceManager+"/v1/projects", body, &op, ""); err != nil {
		if errors.As(err, &apiErr) && apiErr.HTTPStatus == http.StatusConflict {
			return "", false, fmt.Errorf("the project ID %s is taken by another Google account (project IDs are global): pick another with -project", name)
		}

		return "", false, fmt.Errorf("can't create project %s: %w", name, err)
	}

	if _, err := c.wait(ctx, c.resourceManager+"/v1/", op, ""); err != nil {
		return "", false, fmt.Errorf("can't create project %s: %w", name, err)
	}

	return name, true, nil
}

// findProjectByName returns the ID of the user's active project with the name, or "" when there's none
func (c *client) findProjectByName(ctx context.Context, name string) (string, error) {
	// The filter matches the name ignoring case: the exact match is picked below
	query := url.Values{"filter": {"name:" + name + " lifecycleState:ACTIVE"}}
	var found []project

	for {
		var page struct {
			Projects      []project `json:"projects"`
			NextPageToken string    `json:"nextPageToken"`
		}
		if err := c.do(ctx, http.MethodGet, c.resourceManager+"/v1/projects?"+query.Encode(), nil, &page, ""); err != nil {
			return "", err
		}

		found = append(found, page.Projects...)

		if page.NextPageToken == "" {
			break
		}
		query.Set("pageToken", page.NextPageToken)
	}

	var ids []string
	for _, p := range found {
		if p.Name == name && p.LifecycleState == "ACTIVE" {
			ids = append(ids, p.ProjectID)
		}
	}

	if len(ids) > 1 {
		logf("Projects named %s: %s. Using the first, pick another with -project", name, strings.Join(ids, ", "))
	}

	if len(ids) == 0 {
		return "", nil
	}

	return ids[0], nil
}

// ensureService turns the API on in the project unless it's on. A new project takes a while to reach Service Usage.
func (c *client) ensureService(ctx context.Context, project, service string, isNewProject bool) error {
	serviceURL := c.serviceUsage + "/v1/projects/" + project + "/services/" + service

	var state struct {
		State string `json:"state"`
	}

	get := func() error { return c.do(ctx, http.MethodGet, serviceURL, nil, &state, "") }

	var err error
	if isNewProject {
		err = c.retry(ctx, get, func(e *apiError) bool {
			return e.HTTPStatus == http.StatusForbidden || e.HTTPStatus == http.StatusNotFound
		})
	} else {
		err = get()
	}
	if err != nil {
		return fmt.Errorf("can't read the state of %s in %s: %w", service, project, err)
	}

	if state.State == "ENABLED" {
		logf("%s is already on", service)
		return nil
	}

	logf("Turning on %s", service)

	var op operation
	if err := c.do(ctx, http.MethodPost, serviceURL+":enable", struct{}{}, &op, ""); err != nil {
		return fmt.Errorf("can't turn on %s in %s: %w", service, project, err)
	}

	if _, err := c.wait(ctx, c.serviceUsage+"/v1/", op, ""); err != nil {
		return fmt.Errorf("can't turn on %s in %s: %w", service, project, err)
	}

	return nil
}

type apiKey struct {
	Name        string `json:"name"`
	DisplayName string `json:"displayName"`
	KeyString   string `json:"keyString"`
}

// ensureKey returns the key with the display name, made if there's none
func (c *client) ensureKey(ctx context.Context, project, keyName string) (string, error) {
	keysURL := c.apiKeys + "/v2/projects/" + project + "/locations/global/keys"

	keys, err := c.listKeys(ctx, project, keysURL)
	if err != nil {
		return "", fmt.Errorf("can't list the API keys of %s: %w", project, err)
	}

	for _, key := range keys {
		if key.DisplayName == keyName {
			logf("Using the existing API key %q", keyName)
			return c.keyString(ctx, project, key.Name)
		}
	}

	logf("Creating API key %q, restricted to the YouTube Data API", keyName)

	body := map[string]any{
		"displayName": keyName,
		"restrictions": map[string]any{
			"apiTargets": []map[string]string{{"service": youtubeService}},
		},
	}

	var op operation
	if err := c.apiKeysCall(ctx, project, http.MethodPost, keysURL, body, &op); err != nil {
		return "", fmt.Errorf("can't create the API key: %w", err)
	}

	response, err := c.wait(ctx, c.apiKeys+"/v2/", op, c.userProject)
	if err != nil {
		return "", fmt.Errorf("can't create the API key: %w", err)
	}

	var key apiKey
	if err := json.Unmarshal(response, &key); err != nil {
		return "", fmt.Errorf("can't read the new API key: %w", err)
	}

	if key.KeyString != "" {
		return key.KeyString, nil
	}

	if key.Name == "" {
		return "", errors.New("the new API key has no name")
	}

	return c.keyString(ctx, project, key.Name)
}

func (c *client) listKeys(ctx context.Context, project, keysURL string) ([]apiKey, error) {
	var keys []apiKey
	pageToken := ""

	for {
		pageURL := keysURL
		if pageToken != "" {
			pageURL += "?pageToken=" + url.QueryEscape(pageToken)
		}

		var page struct {
			Keys          []apiKey `json:"keys"`
			NextPageToken string   `json:"nextPageToken"`
		}
		if err := c.apiKeysCall(ctx, project, http.MethodGet, pageURL, nil, &page); err != nil {
			return nil, err
		}

		keys = append(keys, page.Keys...)

		if page.NextPageToken == "" {
			return keys, nil
		}
		pageToken = page.NextPageToken
	}
}

func (c *client) keyString(ctx context.Context, project, keyName string) (string, error) {
	var key apiKey
	if err := c.apiKeysCall(ctx, project, http.MethodGet, c.apiKeys+"/v2/"+keyName+"/keyString", nil, &key); err != nil {
		return "", fmt.Errorf("can't read the API key: %w", err)
	}

	if key.KeyString == "" {
		return "", errors.New("the API key came back empty")
	}

	return key.KeyString, nil
}

// apiKeysCall calls the API Keys API. It has to be on in the project the call is billed to: when it's off
// there, it's turned on in the project and the calls are billed to it from then on. Otherwise it stays off.
func (c *client) apiKeysCall(ctx context.Context, project, method, callURL string, body, out any) error {
	err := c.do(ctx, method, callURL, body, out, c.userProject)

	var apiErr *apiError
	if c.userProject != "" || !errors.As(err, &apiErr) || apiErr.reason() != "SERVICE_DISABLED" {
		return err
	}

	logf("The API Keys API is off where the call is billed")

	if err := c.ensureService(ctx, project, apiKeysService, false); err != nil {
		return err
	}

	c.userProject = project

	// Turning an API on takes a while to reach every server
	return c.retry(ctx, func() error { return c.do(ctx, method, callURL, body, out, c.userProject) }, func(e *apiError) bool {
		return e.reason() == "SERVICE_DISABLED" || e.HTTPStatus == http.StatusForbidden
	})
}

// operation is a long-running operation, the same in all three APIs
type operation struct {
	Name     string          `json:"name"`
	Done     bool            `json:"done"`
	Error    *apiError       `json:"error"`
	Response json.RawMessage `json:"response"`
}

// wait polls the operation until it's done, and returns its response
func (c *client) wait(ctx context.Context, versionURL string, op operation, userProject string) (json.RawMessage, error) {
	deadline := time.Now().Add(c.timeout)
	interval := c.pollInterval

	for !op.Done {
		if op.Name == "" {
			return nil, errors.New("the operation has no name")
		}

		if time.Now().After(deadline) {
			return nil, fmt.Errorf("operation %s isn't done after %s", op.Name, c.timeout)
		}

		if err := sleep(ctx, interval); err != nil {
			return nil, err
		}
		interval = min(interval*2, 5*c.pollInterval)

		name := op.Name
		op = operation{}
		if err := c.do(ctx, http.MethodGet, versionURL+name, nil, &op, userProject); err != nil {
			return nil, fmt.Errorf("can't check operation %s: %w", name, err)
		}
		if op.Name == "" {
			op.Name = name
		}
	}

	if op.Error != nil {
		return nil, op.Error
	}

	return op.Response, nil
}

// retry repeats the call while it fails with a retryable error, until the timeout
func (c *client) retry(ctx context.Context, call func() error, retryable func(*apiError) bool) error {
	deadline := time.Now().Add(c.timeout)
	interval := c.pollInterval

	for {
		err := call()

		var apiErr *apiError
		if err == nil || !errors.As(err, &apiErr) || !retryable(apiErr) || time.Now().After(deadline) {
			return err
		}

		if err := sleep(ctx, interval); err != nil {
			return err
		}
		interval = min(interval*2, 10*c.pollInterval)
	}
}

func (c *client) do(ctx context.Context, method, callURL string, body, out any, userProject string) error {
	var reader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return err
		}
		reader = bytes.NewReader(data)
	}

	req, err := http.NewRequestWithContext(ctx, method, callURL, reader)
	if err != nil {
		return err
	}

	req.Header.Set("Authorization", "Bearer "+c.token)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if userProject != "" {
		req.Header.Set("X-Goog-User-Project", userProject)
	}

	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(io.LimitReader(resp.Body, 10<<20))
	if err != nil {
		return err
	}

	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		var wrapper struct {
			Error *apiError `json:"error"`
		}
		if json.Unmarshal(data, &wrapper) == nil && wrapper.Error != nil {
			wrapper.Error.HTTPStatus = resp.StatusCode
			return wrapper.Error
		}

		return &apiError{HTTPStatus: resp.StatusCode, Code: resp.StatusCode, Message: strings.TrimSpace(string(data))}
	}

	if out == nil {
		return nil
	}

	if err := json.Unmarshal(data, out); err != nil {
		return fmt.Errorf("can't read the response of %s %s: %w", method, callURL, err)
	}

	return nil
}

func sleep(ctx context.Context, d time.Duration) error {
	timer := time.NewTimer(d)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

// apiError is a Google API error (google.rpc.Status), from a response or a failed operation
type apiError struct {
	HTTPStatus int           `json:"-"`
	Code       int           `json:"code"`
	Message    string        `json:"message"`
	Status     string        `json:"status"`
	Details    []errorDetail `json:"details"`
}

type errorDetail struct {
	Type     string            `json:"@type"`
	Reason   string            `json:"reason"`
	Metadata map[string]string `json:"metadata"`
	Links    []struct {
		Description string `json:"description"`
		URL         string `json:"url"`
	} `json:"links"`
}

func (e *apiError) Error() string {
	var b strings.Builder

	b.WriteString(e.Message)
	if e.Status != "" {
		fmt.Fprintf(&b, " (%s)", e.Status)
	}

	// e.g. where to accept the Google Cloud terms
	for _, detail := range e.Details {
		for _, link := range detail.Links {
			if link.URL != "" {
				fmt.Fprintf(&b, "\n  %s: %s", link.Description, link.URL)
			}
		}
	}

	return b.String()
}

func (e *apiError) reason() string {
	for _, detail := range e.Details {
		if detail.Reason != "" {
			return detail.Reason
		}
	}

	return ""
}
