package main

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

const testProject = "yt-data-api-key-creation"

// fakeCloud is the three APIs for one user, in memory
type fakeCloud struct {
	t  *testing.T
	mu sync.Mutex

	projectState  string // "" when the user has no project with the ID
	projectID     string // the ID of the project used, testProject unless it's found by name
	listed        []project
	projectTaken  bool // another account has the ID
	services      map[string]bool
	keys          []apiKey
	apiKeysNeedOn bool // the API Keys API has to be on in the billed project

	posts         []string
	createKeyBody map[string]any
	userProjects  []string
}

func newFakeCloud(t *testing.T) *fakeCloud {
	return &fakeCloud{t: t, services: map[string]bool{}, projectID: testProject}
}

func (f *fakeCloud) client(t *testing.T) *client {
	server := httptest.NewServer(f)
	t.Cleanup(server.Close)

	c := newClient("token")
	c.resourceManager = server.URL
	c.serviceUsage = server.URL
	c.apiKeys = server.URL
	c.pollInterval = time.Millisecond
	c.timeout = time.Second

	return c
}

func (f *fakeCloud) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()

	if r.Header.Get("Authorization") != "Bearer token" {
		f.t.Errorf("%s %s without the token", r.Method, r.URL.Path)
	}

	if r.Method == http.MethodPost {
		f.posts = append(f.posts, r.URL.Path)
	}

	var body map[string]any
	if r.Body != nil {
		data, _ := io.ReadAll(r.Body)
		if len(data) > 0 {
			_ = json.Unmarshal(data, &body)
		}
	}

	path := r.URL.Path
	servicePrefix := "/v1/projects/" + f.projectID + "/services/"
	keysPath := "/v2/projects/" + f.projectID + "/locations/global/keys"

	// Each operation is done on its first poll
	if r.Method == http.MethodGet && strings.HasPrefix(path, "/v1/operations/") {
		writeJSON(w, map[string]any{"name": strings.TrimPrefix(path, "/v1/"), "done": true})
		return
	}

	switch {
	case path == "/v1/projects/"+testProject && r.Method == http.MethodGet:
		if f.projectState == "" {
			writeError(w, http.StatusForbidden, "PERMISSION_DENIED", "")
			return
		}
		writeJSON(w, map[string]any{"projectId": testProject, "lifecycleState": f.projectState})

	case path == "/v1/projects" && r.Method == http.MethodGet:
		if filter := r.URL.Query().Get("filter"); filter != "name:"+testProject+" lifecycleState:ACTIVE" {
			f.t.Errorf("projects listed with %q", filter)
		}
		writeJSON(w, map[string]any{"projects": f.listed})

	case path == "/v1/projects" && r.Method == http.MethodPost:
		if f.projectTaken {
			writeError(w, http.StatusConflict, "ALREADY_EXISTS", "")
			return
		}
		if body["projectId"] != testProject || body["name"] != testProject {
			f.t.Errorf("project created with %v", body)
		}
		f.projectState = "ACTIVE"
		writeJSON(w, map[string]any{"name": "operations/cp.1", "done": false})

	case strings.HasPrefix(path, servicePrefix) && r.Method == http.MethodGet:
		service := strings.TrimPrefix(path, servicePrefix)
		state := "DISABLED"
		if f.services[service] {
			state = "ENABLED"
		}
		writeJSON(w, map[string]any{"state": state})

	case strings.HasPrefix(path, servicePrefix) && strings.HasSuffix(path, ":enable") && r.Method == http.MethodPost:
		f.services[strings.TrimSuffix(strings.TrimPrefix(path, servicePrefix), ":enable")] = true
		writeJSON(w, map[string]any{"name": "operations/acf.1", "done": false})

	case strings.HasPrefix(path, "/v2/"):
		userProject := r.Header.Get("X-Goog-User-Project")
		f.userProjects = append(f.userProjects, userProject)
		if f.apiKeysNeedOn && (userProject != f.projectID || !f.services[apiKeysService]) {
			writeError(w, http.StatusForbidden, "PERMISSION_DENIED", "SERVICE_DISABLED")
			return
		}
		f.serveAPIKeys(w, r, path, keysPath, body)

	default:
		f.t.Errorf("unexpected %s %s", r.Method, path)
		http.NotFound(w, r)
	}
}

func (f *fakeCloud) serveAPIKeys(w http.ResponseWriter, r *http.Request, path, keysPath string, body map[string]any) {
	switch {
	case path == keysPath && r.Method == http.MethodGet:
		writeJSON(w, map[string]any{"keys": f.keys})

	case path == keysPath && r.Method == http.MethodPost:
		f.createKeyBody = body
		// Key names are resource names, without the version
		key := apiKey{Name: strings.TrimPrefix(keysPath, "/v2/") + "/new", DisplayName: body["displayName"].(string)}
		f.keys = append(f.keys, key)
		// The response doesn't always carry the key string
		writeJSON(w, map[string]any{"name": "operations/akmf.1", "done": true, "response": key})

	case strings.HasSuffix(path, "/keyString") && r.Method == http.MethodGet:
		writeJSON(w, map[string]any{"keyString": "AIza-" + strings.TrimSuffix(strings.TrimPrefix(path, keysPath+"/"), "/keyString")})

	case strings.HasPrefix(path, "/v2/operations/"):
		writeJSON(w, map[string]any{"name": strings.TrimPrefix(path, "/v2/"), "done": true})

	default:
		f.t.Errorf("unexpected %s %s", r.Method, path)
		http.NotFound(w, r)
	}
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, code int, status, reason string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)

	e := map[string]any{"code": code, "message": status, "status": status}
	if reason != "" {
		e["details"] = []map[string]any{{"@type": "type.googleapis.com/google.rpc.ErrorInfo", "reason": reason}}
	}

	_ = json.NewEncoder(w).Encode(map[string]any{"error": e})
}

func TestMakesEverythingForANewUser(t *testing.T) {
	f := newFakeCloud(t)

	key, err := f.client(t).run(context.Background(), testProject, defaultKeyName)
	if err != nil {
		t.Fatal(err)
	}

	if key != "AIza-new" {
		t.Errorf("key %q", key)
	}
	if f.projectState != "ACTIVE" || !f.services[youtubeService] {
		t.Errorf("project %q, services %v", f.projectState, f.services)
	}
	// Nothing more than the key needs
	if f.services[apiKeysService] {
		t.Error("the API Keys API was turned on without being needed")
	}

	targets := f.createKeyBody["restrictions"].(map[string]any)["apiTargets"].([]any)
	if len(targets) != 1 || targets[0].(map[string]any)["service"] != youtubeService {
		t.Errorf("key restricted to %v", targets)
	}
	if f.createKeyBody["displayName"] != defaultKeyName {
		t.Errorf("key named %v", f.createKeyBody["displayName"])
	}
}

func TestReusesWhatExists(t *testing.T) {
	f := newFakeCloud(t)
	f.projectState = "ACTIVE"
	f.services[youtubeService] = true
	f.keys = []apiKey{
		{Name: "projects/" + testProject + "/locations/global/keys/other", DisplayName: "Other"},
		{Name: "projects/" + testProject + "/locations/global/keys/mine", DisplayName: defaultKeyName},
	}

	key, err := f.client(t).run(context.Background(), testProject, defaultKeyName)
	if err != nil {
		t.Fatal(err)
	}

	if key != "AIza-mine" {
		t.Errorf("key %q", key)
	}
	if len(f.posts) != 0 {
		t.Errorf("made %v", f.posts)
	}
}

func TestUsesTheProjectWithTheName(t *testing.T) {
	f := newFakeCloud(t)
	f.projectID = testProject + "-473012"
	f.listed = []project{
		// The filter ignores case
		{ProjectID: "other-1", Name: strings.ToUpper(testProject), LifecycleState: "ACTIVE"},
		{ProjectID: f.projectID, Name: testProject, LifecycleState: "ACTIVE"},
	}

	key, err := f.client(t).run(context.Background(), testProject, defaultKeyName)
	if err != nil {
		t.Fatal(err)
	}

	if key != "AIza-new" {
		t.Errorf("key %q", key)
	}
	if !f.services[youtubeService] {
		t.Error("the YouTube Data API wasn't turned on")
	}
	for _, post := range f.posts {
		if post == "/v1/projects" {
			t.Error("a project was created")
		}
	}
}

func TestProjectIDOfAnotherAccount(t *testing.T) {
	f := newFakeCloud(t)
	f.projectTaken = true

	_, err := f.client(t).run(context.Background(), testProject, defaultKeyName)
	if err == nil || !strings.Contains(err.Error(), "taken by another Google account") {
		t.Fatalf("error %v", err)
	}
}

func TestProjectPendingDeletion(t *testing.T) {
	f := newFakeCloud(t)
	f.projectState = "DELETE_REQUESTED"

	_, err := f.client(t).run(context.Background(), testProject, defaultKeyName)
	if err == nil || !strings.Contains(err.Error(), "pending deletion") {
		t.Fatalf("error %v", err)
	}
	if len(f.posts) != 0 {
		t.Errorf("made %v", f.posts)
	}
}

func TestTurnsOnAPIKeysOnlyWhenNeeded(t *testing.T) {
	f := newFakeCloud(t)
	f.apiKeysNeedOn = true

	key, err := f.client(t).run(context.Background(), testProject, defaultKeyName)
	if err != nil {
		t.Fatal(err)
	}

	if key != "AIza-new" {
		t.Errorf("key %q", key)
	}
	if !f.services[apiKeysService] {
		t.Error("the API Keys API wasn't turned on")
	}
	// Billed to the project from the first refusal on
	if last := f.userProjects[len(f.userProjects)-1]; last != testProject {
		t.Errorf("last API Keys call billed to %q", last)
	}
}

func TestErrorShowsLinks(t *testing.T) {
	var e apiError
	data := `{"code":400,"message":"Terms not accepted","status":"FAILED_PRECONDITION","details":[{"links":[{"description":"Accept the terms","url":"https://console.cloud.google.com/tos"}]}]}`
	if err := json.Unmarshal([]byte(data), &e); err != nil {
		t.Fatal(err)
	}

	if got := e.Error(); !strings.Contains(got, "https://console.cloud.google.com/tos") || !strings.Contains(got, "FAILED_PRECONDITION") {
		t.Errorf("error %q", got)
	}
}
