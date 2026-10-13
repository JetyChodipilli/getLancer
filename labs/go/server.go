// Copyright (c) 2026 getLancer contributors. SPDX-License-Identifier: MIT
// Disposable loopback labs. Only the Go standard library and real Redis are used.
package main

import (
	"bufio"
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"regexp"
	"runtime"
	"runtime/debug"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
	"unicode/utf8"
)

const (
	bodyLimit      = 16 * 1024
	responseLimit  = 4096
	redisBudget    = 250 * time.Millisecond
	runLifetime    = 15 * time.Minute
	cacheTTL       = 5000
	maxEvents      = 100
	maxSequence    = 10_000_000
	maxStoreReads  = 1_000_000
	maxConnections = 8
	paymentKey     = "getlancer-payment-fixture-only"
	identityKey    = "getlancer-identity-fixture-only"
)

var (
	uuidGrammar      = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)
	signatureGrammar = regexp.MustCompile(`^[0-9a-f]{64}$`)
	eventGrammar     = regexp.MustCompile(`^(evt-[1-9][0-9]{0,3})\|order-1\|(payment|refund)\.succeeded$`)
	portGrammar      = regexp.MustCompile(`^[0-9]{1,5}$`)
)

type protocolError struct {
	status        int
	code, message string
}

func deny(status int, code, message string) *protocolError {
	return &protocolError{status, code, message}
}

type eventType string

const (
	cacheMiss              eventType = "CACHE_MISS"
	cacheHit               eventType = "CACHE_HIT"
	cacheExpired           eventType = "CACHE_EXPIRED"
	cacheFallback          eventType = "CACHE_FALLBACK"
	cacheReset             eventType = "CACHE_RESET"
	storeRead              eventType = "STORE_READ"
	authAllowed            eventType = "AUTH_ALLOWED"
	authDenied             eventType = "AUTH_DENIED"
	authRevoked            eventType = "AUTH_REVOKED"
	authReset              eventType = "AUTH_RESET"
	paymentApplied         eventType = "PAYMENT_APPLIED"
	paymentDuplicate       eventType = "PAYMENT_DUPLICATE"
	paymentSignatureDenied eventType = "PAYMENT_SIGNATURE_DENIED"
	paymentConflict        eventType = "PAYMENT_CONFLICT"
	paymentPending         eventType = "PAYMENT_PENDING"
	paymentReset           eventType = "PAYMENT_RESET"
	refundPending          eventType = "REFUND_PENDING"
	refundApplied          eventType = "REFUND_APPLIED"
)

type event struct {
	RunID      string    `json:"runId"`
	SourceHash string    `json:"sourceHash"`
	RequestID  string    `json:"requestId"`
	Sequence   uint64    `json:"sequence"`
	Type       eventType `json:"type"`
	Edge       string    `json:"edge"`
	RecordedAt string    `json:"recordedAt"`
	Summary    string    `json:"summary"`
}
type health struct {
	Mode       string `json:"mode"`
	Language   string `json:"language"`
	RunID      string `json:"runId"`
	SourceHash string `json:"sourceHash"`
}
type response struct {
	RunID      string  `json:"runId"`
	SourceHash string  `json:"sourceHash"`
	RequestID  string  `json:"requestId"`
	Pattern    string  `json:"pattern"`
	Status     int     `json:"status"`
	DurationMS int64   `json:"durationMs"`
	State      any     `json:"state"`
	Events     []event `json:"events"`
}
type cacheState struct {
	Cache      string `json:"cache"`
	StoreReads uint64 `json:"storeReads"`
	TTLMS      int64  `json:"ttlMs"`
}
type securityState struct {
	Decision    string `json:"decision"`
	Reason      string `json:"reason"`
	CurrentRole string `json:"currentRole"`
}
type paymentState struct {
	Payment         string `json:"payment"`
	Entitlements    int    `json:"entitlements"`
	ProcessedEvents int    `json:"processedEvents"`
}
type lab struct {
	mu                                  sync.Mutex
	runID, sourceHash, key              string
	redisPort, failurePort, timeoutPort int
	expiresAt                           time.Time
	sequence, storeReads                uint64
	currentRole                         string
	paymentEvents                       map[string]string
	paid, refunded                      bool
}

func uuid() (string, error) {
	var value [16]byte
	if _, err := rand.Read(value[:]); err != nil {
		return "", err
	}
	value[6] = value[6]&0x0f | 0x40
	value[8] = value[8]&0x3f | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", value[0:4], value[4:6], value[6:8], value[8:10], value[10:16]), nil
}
func configuredPort(name string, fallback int) (int, error) {
	value, present := os.LookupEnv(name)
	if !present {
		return fallback, nil
	}
	port, err := strconv.Atoi(value)
	if !portGrammar.MatchString(value) || err != nil || port < 1 || port > 65535 {
		return 0, fmt.Errorf("%s must be a port from 1 to 65535", name)
	}
	return port, nil
}
func newLab() (*lab, error) {
	runID, present := os.LookupEnv("LAB_RUN_ID")
	if !present {
		var err error
		runID, err = uuid()
		if err != nil {
			return nil, errors.New("The local run ID could not be created")
		}
	}
	if !uuidGrammar.MatchString(runID) {
		return nil, errors.New("LAB_RUN_ID must be a canonical lowercase UUID")
	}
	// go run retains this reviewed source path; health identifies the actual bytes.
	_, sourcePath, _, ok := runtime.Caller(0)
	if !ok {
		return nil, errors.New("The reviewed primary source could not be located")
	}
	bytes, err := os.ReadFile(sourcePath)
	if err != nil {
		return nil, errors.New("The reviewed primary source must remain available")
	}
	hash := sha256.Sum256(bytes)
	redisPort, err := configuredPort("LAB_REDIS_PORT", 6379)
	if err != nil {
		return nil, err
	}
	failurePort, err := configuredPort("LAB_REDIS_FAILURE_PORT", 6380)
	if err != nil {
		return nil, err
	}
	timeoutPort, err := configuredPort("LAB_REDIS_TIMEOUT_PORT", 6381)
	if err != nil {
		return nil, err
	}
	return &lab{runID: runID, sourceHash: hex.EncodeToString(hash[:]), key: "getlancer:" + runID + ":item",
		redisPort: redisPort, failurePort: failurePort, timeoutPort: timeoutPort,
		expiresAt: time.Now().Add(runLifetime), currentRole: "editor", paymentEvents: make(map[string]string)}, nil
}
func (l *lab) health() health { return health{"Local execution", "go", l.runID, l.sourceHash} }

func parseForm(raw []byte) (map[string]string, *protocolError) {
	if len(raw) == 0 || !utf8.Valid(raw) {
		return nil, deny(400, "INVALID_FORM", "Form fields must be unique and valid UTF-8.")
	}
	fields := make(map[string]string)
	for _, pair := range strings.Split(string(raw), "&") {
		name, value, found := strings.Cut(pair, "=")
		if !found {
			return nil, deny(400, "INVALID_FORM", "Form fields must have a name and value.")
		}
		name, err := url.QueryUnescape(name)
		if err != nil || name == "" || !utf8.ValidString(name) {
			return nil, deny(400, "INVALID_FORM", "Form field names must be valid UTF-8.")
		}
		value, err = url.QueryUnescape(value)
		if err != nil || !utf8.ValidString(value) {
			return nil, deny(400, "INVALID_FORM", "Form field values must be valid UTF-8.")
		}
		if _, duplicate := fields[name]; duplicate {
			return nil, deny(400, "INVALID_FORM", "Duplicate form fields are not accepted.")
		}
		fields[name] = value
	}
	return fields, nil
}
func (l *lab) validate(fields map[string]string) *protocolError {
	for _, name := range []string{"runId", "pattern", "operationId"} {
		if _, found := fields[name]; !found {
			return deny(400, "INVALID_FIELDS", "Required operation fields are missing.")
		}
	}
	if !uuidGrammar.MatchString(fields["runId"]) {
		return deny(400, "INVALID_RUN", "The run ID must be a canonical UUID.")
	}
	if fields["runId"] != l.runID {
		return deny(403, "FOREIGN_RUN", "This request belongs to a different local run.")
	}
	allowed := map[string]bool{"runId": true, "pattern": true, "operationId": true}
	operation := fields["operationId"]
	switch fields["pattern"] {
	case "cache":
		if operation != "read" && operation != "expire" && operation != "read-unavailable" && operation != "read-timeout" && operation != "reset" {
			return deny(400, "INVALID_OPERATION", "Choose a supported cache operation.")
		}
	case "security":
		if operation == "authorize" {
			allowed["identity"] = true
		} else if operation != "revoke" && operation != "reset" {
			return deny(400, "INVALID_OPERATION", "Choose a supported authorization operation.")
		}
	case "payment":
		if operation == "deliver" {
			allowed["eventBody"], allowed["signature"] = true, true
		} else if operation != "timeout" && operation != "reset" {
			return deny(400, "INVALID_OPERATION", "Choose a supported emulator operation.")
		}
	default:
		return deny(400, "INVALID_OPERATION", "Choose a supported pattern and operation.")
	}
	if len(fields) != len(allowed) {
		return deny(400, "INVALID_FIELDS", "This operation requires its exact allowed fields.")
	}
	for name := range allowed {
		if _, found := fields[name]; !found {
			return deny(400, "INVALID_FIELDS", "This operation requires its exact allowed fields.")
		}
	}
	if fields["pattern"] == "security" && operation == "authorize" {
		switch fields["identity"] {
		case "editor", "viewer", "other-tenant", "expired", "tampered":
		default:
			return deny(400, "INVALID_IDENTITY", "Choose a supported synthetic identity.")
		}
	}
	if fields["pattern"] == "payment" && operation == "deliver" && !signatureGrammar.MatchString(fields["signature"]) {
		return deny(400, "INVALID_SIGNATURE", "The signature must be 64 lowercase hexadecimal characters.")
	}
	if !time.Now().Before(l.expiresAt) {
		return deny(503, "RUN_EXPIRED", "Restart this expired disposable local run.")
	}
	if l.sequence > maxSequence-4 || l.storeReads >= maxStoreReads {
		return deny(503, "RUN_LIMIT", "Restart this disposable local run.")
	}
	return nil
}

type emitEvent func(eventType, string, string)

func (l *lab) execute(fields map[string]string) (*response, *protocolError) {
	// One admission point serializes facts, Redis effects, revocation and event IDs.
	l.mu.Lock()
	defer l.mu.Unlock()
	if problem := l.validate(fields); problem != nil {
		return nil, problem
	}
	started := time.Now()
	requestID, err := uuid()
	if err != nil {
		return nil, deny(503, "REQUEST_ID", "The local request could not be created.")
	}
	events := make([]event, 0, 4)
	emit := func(kind eventType, edge, summary string) {
		l.sequence++
		events = append(events, event{l.runID, l.sourceHash, requestID, l.sequence, kind, edge,
			time.Now().UTC().Format("2006-01-02T15:04:05.000Z"), summary})
	}
	var status int
	var state any
	var problem *protocolError
	switch fields["pattern"] {
	case "cache":
		status, state, problem = l.cache(fields["operationId"], emit)
	case "security":
		status, state = l.security(fields["operationId"], fields["identity"], emit)
	case "payment":
		status, state, problem = l.payment(fields, emit)
	}
	if problem != nil {
		return nil, problem
	}
	return &response{l.runID, l.sourceHash, requestID, fields["pattern"], status,
		time.Since(started).Milliseconds(), state, events}, nil
}

type redisReply struct {
	kind   byte
	text   string
	number int64
}
type redisConnection struct {
	conn     net.Conn
	reader   *bufio.Reader
	received int
}

func openRedis(port int) (*redisConnection, error) {
	deadline := time.Now().Add(redisBudget)
	conn, err := net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)), redisBudget)
	if err != nil {
		return nil, err
	}
	if err = conn.SetDeadline(deadline); err != nil {
		conn.Close()
		return nil, err
	}
	return &redisConnection{conn: conn, reader: bufio.NewReaderSize(conn, 512)}, nil
}
func (r *redisConnection) command(parts ...string) (redisReply, error) {
	var wire strings.Builder
	fmt.Fprintf(&wire, "*%d\r\n", len(parts))
	for _, part := range parts {
		fmt.Fprintf(&wire, "$%d\r\n%s\r\n", len(part), part)
	}
	n, err := io.WriteString(r.conn, wire.String())
	if err != nil || n != wire.Len() {
		return redisReply{}, errors.New("Redis request did not complete")
	}
	line, err := r.reader.ReadSlice('\n')
	if err != nil || len(line) < 3 || len(line) > 256 || line[len(line)-2] != '\r' {
		return redisReply{}, errors.New("Redis reply is invalid")
	}
	r.received += len(line)
	if r.received > 2048 {
		return redisReply{}, errors.New("Redis reply exceeded its bound")
	}
	text := string(line[1 : len(line)-2])
	switch line[0] {
	case '+':
		return redisReply{kind: '+', text: text}, nil
	case ':':
		value, err := strconv.ParseInt(text, 10, 64)
		if err != nil {
			return redisReply{}, errors.New("Redis integer is invalid")
		}
		return redisReply{kind: ':', number: value}, nil
	case '$':
		length, err := strconv.ParseInt(text, 10, 64)
		if err != nil || length < -1 || length > 1024 {
			return redisReply{}, errors.New("Redis bulk reply exceeded its bound")
		}
		if length == -1 {
			return redisReply{kind: '$', number: -1}, nil
		}
		r.received += int(length) + 2
		if r.received > 2048 {
			return redisReply{}, errors.New("Redis reply exceeded its bound")
		}
		body := make([]byte, int(length)+2)
		if _, err := io.ReadFull(r.reader, body); err != nil || body[len(body)-2] != '\r' || body[len(body)-1] != '\n' {
			return redisReply{}, errors.New("Redis bulk reply is invalid")
		}
		return redisReply{kind: '$', text: string(body[:len(body)-2]), number: length}, nil
	default:
		return redisReply{}, errors.New("Redis reply type is invalid")
	}
}
func (l *lab) storeRead(emit emitEvent) {
	l.storeReads++
	emit(storeRead, "service-store", "The synthetic store returned its fixed fixture item.")
}
func (l *lab) cache(operation string, emit emitEvent) (int, any, *protocolError) {
	if operation == "reset" || operation == "expire" {
		conn, err := openRedis(l.redisPort)
		if err != nil {
			return 0, nil, deny(503, "REDIS_UNAVAILABLE", "The Redis fixture action could not complete.")
		}
		defer conn.conn.Close()
		var reply redisReply
		if operation == "reset" {
			reply, err = conn.command("DEL", l.key)
		} else {
			reply, err = conn.command("PEXPIRE", l.key, "0")
		}
		if err != nil || reply.kind != ':' || reply.number < 0 || reply.number > 1 {
			return 0, nil, deny(503, "REDIS_UNAVAILABLE", "The Redis fixture action could not complete.")
		}
		if operation == "reset" {
			l.storeReads = 0
			emit(cacheReset, "service-redis", "Redis cleared only the current run's fixture key.")
			return 200, cacheState{"RESET", 0, 0}, nil
		}
		emit(cacheExpired, "service-redis", "Redis completed expiry of the current run's fixture key.")
		return 200, cacheState{"EXPIRED", l.storeReads, 0}, nil
	}
	port := l.redisPort
	if operation == "read-unavailable" {
		port = l.failurePort
	} else if operation == "read-timeout" {
		port = l.timeoutPort
	}
	readStore := false
	fallback := func() (int, any, *protocolError) {
		emit(cacheFallback, "service-redis", "The bounded Redis action failed; cache fallback was selected.")
		if !readStore {
			l.storeRead(emit)
		}
		return 200, cacheState{"FALLBACK", l.storeReads, 0}, nil
	}
	conn, err := openRedis(port)
	if err != nil {
		return fallback()
	}
	defer conn.conn.Close()
	value, err := conn.command("GET", l.key)
	if err != nil || value.kind != '$' {
		return fallback()
	}
	cache := "HIT"
	if value.number == -1 {
		cache = "MISS"
		emit(cacheMiss, "service-redis", "Redis missed the current run's fixture key.")
		l.storeRead(emit)
		readStore = true
		reply, err := conn.command("SET", l.key, "fixture-item", "PX", strconv.Itoa(cacheTTL))
		if err != nil || reply.kind != '+' || reply.text != "OK" {
			return fallback()
		}
	} else if value.text != "fixture-item" {
		return fallback()
	}
	ttl, err := conn.command("PTTL", l.key)
	if err != nil || ttl.kind != ':' || ttl.number < -2 || ttl.number > cacheTTL {
		return fallback()
	}
	if cache == "HIT" {
		emit(cacheHit, "service-redis", "Redis returned the current run's cached fixture item.")
	}
	if ttl.number < 0 {
		ttl.number = 0
	}
	return 200, cacheState{cache, l.storeReads, ttl.number}, nil
}

func sign(key string, body []byte) []byte {
	mac := hmac.New(sha256.New, []byte(key))
	mac.Write(body)
	return mac.Sum(nil)
}
func (l *lab) security(operation, identity string, emit emitEvent) (int, any) {
	if operation == "reset" {
		l.currentRole = "editor"
		emit(authReset, "policy-store", "The current run's synthetic editor policy was restored.")
		return 200, securityState{"RESET", "RESET", l.currentRole}
	}
	if operation == "revoke" {
		l.currentRole = "viewer"
		emit(authRevoked, "policy-store", "The current role policy revoked editor authority.")
		return 200, securityState{"DENY", "REVOKED", l.currentRole}
	}
	tenant, role := "fixture", "editor"
	if identity == "other-tenant" {
		tenant = "other"
	}
	if identity == "viewer" {
		role = "viewer"
	}
	expires := time.Now().Add(5 * time.Minute).Unix()
	if identity == "expired" {
		expires = time.Now().Add(-time.Minute).Unix()
	}
	claims := []byte(fmt.Sprintf("%s|%s|%d", tenant, role, expires))
	signature := sign(identityKey, claims)
	if identity == "tampered" {
		signature[0] ^= 1
	}
	verified := hmac.Equal(signature, sign(identityKey, claims))
	status, reason, edge, summary := 200, "ALLOW", "policy-store", "The verified synthetic editor has current authority."
	switch {
	case !verified:
		status, reason, edge, summary = 401, "SIGNATURE", "service-policy", "The synthetic identity signature did not verify."
	case expires <= time.Now().Unix():
		status, reason, edge, summary = 401, "EXPIRED", "service-policy", "The synthetic identity expired."
	case tenant != "fixture":
		status, reason, edge, summary = 403, "TENANT", "service-policy", "The synthetic identity belongs to another tenant."
	case role != "editor":
		status, reason, summary = 403, "ROLE", "The synthetic identity lacks the required editor role."
	case l.currentRole != "editor":
		status, reason, summary = 403, "REVOKED", "The current role policy has revoked editor authority."
	}
	decision, kind := "ALLOW", authAllowed
	if status != 200 {
		decision, kind = "DENY", authDenied
	}
	emit(kind, edge, summary)
	return status, securityState{decision, reason, l.currentRole}
}
func (l *lab) paymentState() paymentState {
	state, entitlement := "PENDING", 0
	if l.paid {
		state = "PAID"
		entitlement = 1
		if l.refunded {
			state = "REFUNDED"
			entitlement = 0
		}
	}
	return paymentState{state, entitlement, len(l.paymentEvents)}
}
func (l *lab) payment(fields map[string]string, emit emitEvent) (int, any, *protocolError) {
	switch fields["operationId"] {
	case "reset":
		clear(l.paymentEvents)
		l.paid, l.refunded = false, false
		emit(paymentReset, "emulator-ledger", "The current run's synthetic payment ledger was cleared.")
		return 200, l.paymentState(), nil
	case "timeout":
		// Only this synthetic emulator deadline is simulated; Redis timeout is real.
		time.Sleep(25 * time.Millisecond)
		emit(paymentPending, "service-emulator", "The emulator timed out; no new ledger effect was applied.")
		status := 200
		if !l.paid {
			status = 202
		}
		return status, l.paymentState(), nil
	}
	body := fields["eventBody"]
	signature, err := hex.DecodeString(fields["signature"])
	if err != nil || !hmac.Equal(signature, sign(paymentKey, []byte(body))) {
		emit(paymentSignatureDenied, "service-emulator", "The exact event body signature did not verify.")
		return 401, l.paymentState(), nil
	}
	match := eventGrammar.FindStringSubmatch(body)
	if match == nil {
		return 0, nil, deny(400, "INVALID_EVENT", "The signed event must use the fixed fixture grammar.")
	}
	id, kind := match[1], match[2]
	if previous, exists := l.paymentEvents[id]; exists {
		if previous != body {
			emit(paymentConflict, "emulator-ledger", "The event identifier already belongs to a different verified body.")
			return 409, l.paymentState(), nil
		}
		emit(paymentDuplicate, "emulator-ledger", "The verified event was already applied; no additional effect occurred.")
		return 200, l.paymentState(), nil
	}
	if len(l.paymentEvents) >= maxEvents {
		return 0, nil, deny(409, "EVENT_LIMIT", "The current run has reached its 100 event limit.")
	}
	l.paymentEvents[id] = body
	if kind == "payment" {
		l.paid = true
		emit(paymentApplied, "emulator-ledger", "The verified payment was recorded once in the synthetic ledger.")
		if l.refunded {
			emit(refundApplied, "emulator-ledger", "The recorded refund reconciled without granting an entitlement.")
		}
	} else {
		l.refunded = true
		if l.paid {
			emit(refundApplied, "emulator-ledger", "The verified refund was recorded; the synthetic entitlement is absent.")
		} else {
			emit(refundPending, "emulator-ledger", "The verified refund awaits payment; no entitlement was granted.")
		}
	}
	status := 200
	if !l.paid {
		status = 202
	}
	return status, l.paymentState(), nil
}

func sendJSON(w http.ResponseWriter, status int, value any) {
	bytes, err := json.Marshal(value)
	if err != nil || len(bytes) > responseLimit {
		status = 500
		bytes = []byte(`{"error":{"code":"RESPONSE_LIMIT","message":"The bounded response could not be produced."}}`)
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Content-Length", strconv.Itoa(len(bytes)))
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("Connection", "close")
	w.WriteHeader(status)
	_, _ = w.Write(bytes)
}
func sendDenial(w http.ResponseWriter, problem *protocolError) {
	sendJSON(w, problem.status, struct {
		Error struct {
			Code    string `json:"code"`
			Message string `json:"message"`
		} `json:"error"`
	}{
		Error: struct {
			Code    string `json:"code"`
			Message string `json:"message"`
		}{problem.code, problem.message},
	})
}
func (l *lab) ServeHTTP(w http.ResponseWriter, request *http.Request) {
	defer request.Body.Close()
	fail := func(status int, code, message string) { sendDenial(w, deny(status, code, message)) }
	if strings.Contains(request.RequestURI, "?") {
		fail(400, "QUERY_NOT_ALLOWED", "Query strings are not accepted.")
		return
	}
	if request.RequestURI != "/health" && request.RequestURI != "/request" {
		fail(404, "NOT_FOUND", "Choose a supported local lab path.")
		return
	}
	if (request.RequestURI == "/health" && request.Method != "GET") || (request.RequestURI == "/request" && request.Method != "POST") {
		fail(405, "METHOD_NOT_ALLOWED", "The method is not supported for this path.")
		return
	}
	if request.RequestURI == "/health" {
		if request.ContentLength != 0 || len(request.TransferEncoding) != 0 {
			fail(400, "INVALID_BODY", "Health requests must not include a body.")
			return
		}
		if !time.Now().Before(l.expiresAt) {
			fail(503, "RUN_EXPIRED", "Restart this expired disposable local run.")
			return
		}
		sendJSON(w, 200, l.health())
		return
	}
	if values := request.Header.Values("Content-Type"); len(values) != 1 || values[0] != "application/x-www-form-urlencoded" {
		fail(415, "CONTENT_TYPE", "Use application/x-www-form-urlencoded.")
		return
	}
	if request.ContentLength < 0 || len(request.TransferEncoding) != 0 || len(request.Header.Values("Content-Length")) != 1 {
		fail(400, "INVALID_LENGTH", "One valid content length is required.")
		return
	}
	if request.ContentLength > bodyLimit {
		fail(413, "BODY_LIMIT", "Requests are limited to 16 KiB.")
		return
	}
	raw, err := io.ReadAll(io.LimitReader(request.Body, bodyLimit+1))
	if err != nil || int64(len(raw)) != request.ContentLength {
		fail(400, "INCOMPLETE_BODY", "The request body did not complete.")
		return
	}
	if len(raw) > bodyLimit {
		fail(413, "BODY_LIMIT", "Requests are limited to 16 KiB.")
		return
	}
	fields, problem := parseForm(raw)
	if problem != nil {
		sendDenial(w, problem)
		return
	}
	result, problem := l.execute(fields)
	if problem != nil {
		sendDenial(w, problem)
		return
	}
	sendJSON(w, result.Status, result)
}

// net/http bounds protocol parsing; this listener also bounds live connections.
type boundedListener struct {
	net.Listener
	slots chan struct{}
	done  chan struct{}
	once  sync.Once
}
type boundedConnection struct {
	net.Conn
	slots chan struct{}
	once  sync.Once
}

func (c *boundedConnection) Close() error {
	err := c.Conn.Close()
	c.once.Do(func() { <-c.slots })
	return err
}
func (l *boundedListener) Accept() (net.Conn, error) {
	select {
	case l.slots <- struct{}{}:
	case <-l.done:
		return nil, net.ErrClosed
	}
	connection, err := l.Listener.Accept()
	if err != nil {
		<-l.slots
		return nil, err
	}
	return &boundedConnection{Conn: connection, slots: l.slots}, nil
}
func (l *boundedListener) Close() error {
	l.once.Do(func() { close(l.done) })
	return l.Listener.Close()
}
func serve() error {
	l, err := newLab()
	if err != nil {
		return err
	}
	port, err := configuredPort("LAB_PORT", 8100)
	if err != nil {
		return err
	}
	base, err := net.Listen("tcp4", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)))
	if err != nil {
		return errors.New("The local HTTP fixture could not bind its loopback port")
	}
	listener := &boundedListener{Listener: base, slots: make(chan struct{}, maxConnections), done: make(chan struct{})}
	server := &http.Server{Handler: l, ReadHeaderTimeout: time.Second, ReadTimeout: time.Second,
		WriteTimeout: 3 * time.Second, IdleTimeout: time.Second, MaxHeaderBytes: 4096,
		ErrorLog: log.New(io.Discard, "", 0)}
	server.SetKeepAlivesEnabled(false)
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	parent := os.Getppid()
	shutdownDone := make(chan struct{})
	go func() {
		defer close(shutdownDone)
		expiry := time.NewTimer(time.Until(l.expiresAt))
		defer expiry.Stop()
		watchParent := time.NewTicker(time.Second)
		defer watchParent.Stop()
		for {
			select {
			case <-ctx.Done():
			case <-expiry.C:
			case <-watchParent.C:
				if parent <= 1 || os.Getppid() == parent {
					continue
				}
			}
			shutdown, cancel := context.WithTimeout(context.Background(), 3*time.Second)
			if err := server.Shutdown(shutdown); err != nil {
				_ = server.Close()
			}
			cancel()
			return
		}
	}()
	startup, err := json.Marshal(l.health())
	if err != nil {
		_ = listener.Close()
		return errors.New("The bounded startup health could not be produced")
	}
	fmt.Println(string(startup))
	err = server.Serve(listener)
	stop()
	_ = listener.Close()
	// Serve returns when the listener closes; wait for in-flight requests before cleanup.
	<-shutdownDone
	// Redis is bounded and per-run. Expiry still removes this key if it is offline.
	if conn, cleanupErr := openRedis(l.redisPort); cleanupErr == nil {
		_, _ = conn.command("DEL", l.key)
		_ = conn.conn.Close()
	}
	if errors.Is(err, http.ErrServerClosed) {
		return nil
	}
	return errors.New("The local HTTP fixture stopped unexpectedly")
}
func main() {
	runtime.GOMAXPROCS(2)
	debug.SetMemoryLimit(64 * 1024 * 1024) // GC target, not a production OS isolation boundary.
	if err := serve(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
