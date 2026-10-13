// Copyright (c) 2026 getLancer contributors. SPDX-License-Identifier: MIT
package main

import (
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http/httptest"
	"net/url"
	"sort"
	"strings"
	"sync"
	"testing"
	"time"
)

func fixtureLab() *lab {
	return &lab{runID: "11111111-1111-4111-8111-111111111111", sourceHash: strings.Repeat("a", 64),
		key: "getlancer:11111111-1111-4111-8111-111111111111:item", expiresAt: time.Now().Add(runLifetime),
		currentRole: "editor", paymentEvents: make(map[string]string)}
}
func fields(l *lab, pattern, operation string) map[string]string {
	return map[string]string{"runId": l.runID, "pattern": pattern, "operationId": operation}
}
func paymentFields(l *lab, body string) map[string]string {
	value := fields(l, "payment", "deliver")
	value["eventBody"], value["signature"] = body, hex.EncodeToString(sign(paymentKey, []byte(body)))
	return value
}
func TestFormStrictEncoding(t *testing.T) {
	for _, raw := range [][]byte{nil, []byte("missing"), []byte("=empty"), []byte("a=1&a=2"), []byte("runId=1&run%49d=2"), []byte("a=%FF"), []byte("a=%Q0"), []byte("a=%"), []byte{'a', '=', 255}} {
		if _, problem := parseForm(raw); problem == nil || problem.status != 400 {
			t.Fatalf("invalid form accepted: %q", raw)
		}
	}
	parsed, problem := parseForm([]byte("a=%E2%82%AC&b=x%2By+z&c=x%3Dy"))
	if problem != nil || parsed["a"] != "€" || parsed["b"] != "x+y z" || parsed["c"] != "x=y" {
		t.Fatalf("valid UTF-8 form changed: %#v %#v", parsed, problem)
	}
}
func TestAdmissionNoEffects(t *testing.T) {
	for _, input := range []map[string]string{
		{"runId": "22222222-2222-4222-8222-222222222222", "pattern": "payment", "operationId": "reset"},
		{"runId": "not-a-run", "pattern": "payment", "operationId": "reset"},
		{"runId": "11111111-1111-4111-8111-111111111111", "pattern": "security", "operationId": "authorize", "identity": "editor", "token": "account-token"},
		{"runId": "11111111-1111-4111-8111-111111111111", "pattern": "payment", "operationId": "deliver", "signature": strings.Repeat("0", 64)},
	} {
		l := fixtureLab()
		result, problem := l.execute(input)
		if problem == nil || result != nil || l.sequence != 0 || len(l.paymentEvents) != 0 || l.currentRole != "editor" {
			t.Fatalf("rejected admission changed facts: %#v %#v", result, problem)
		}
	}
}
func TestCurrentPolicyAndIdentity(t *testing.T) {
	l := fixtureLab()
	for _, test := range []struct {
		identity, reason string
		status           int
	}{{"editor", "ALLOW", 200}, {"viewer", "ROLE", 403}, {"other-tenant", "TENANT", 403}, {"expired", "EXPIRED", 401}, {"tampered", "SIGNATURE", 401}} {
		input := fields(l, "security", "authorize")
		input["identity"] = test.identity
		result, problem := l.execute(input)
		if problem != nil || result.Status != test.status || result.State.(securityState).Reason != test.reason {
			t.Fatalf("identity %s: %#v %#v", test.identity, result, problem)
		}
	}
	if _, problem := l.execute(fields(l, "security", "revoke")); problem != nil {
		t.Fatal(problem)
	}
	input := fields(l, "security", "authorize")
	input["identity"] = "editor"
	result, problem := l.execute(input)
	if problem != nil || result.Status != 403 || result.State.(securityState).Reason != "REVOKED" {
		t.Fatalf("signed editor bypassed current policy: %#v %#v", result, problem)
	}
	sequence := l.sequence
	if _, problem = l.execute(fields(l, "security", "reset")); problem != nil {
		t.Fatal(problem)
	}
	result, problem = l.execute(input)
	if problem != nil || result.Status != 200 || result.Events[0].Sequence <= sequence {
		t.Fatalf("reset did not preserve monotonic current authority: %#v %#v", result, problem)
	}
}
func TestRawSignatureGrammarAndConflict(t *testing.T) {
	l := fixtureLab()
	body := "evt-1|order-1|payment.succeeded"
	result, problem := l.execute(paymentFields(l, body))
	if problem != nil || result.State.(paymentState) != (paymentState{"PAID", 1, 1}) {
		t.Fatalf("valid payment: %#v %#v", result, problem)
	}
	oldSignature := paymentFields(l, body)
	oldSignature["eventBody"] = "evt-2|order-1|refund.succeeded"
	result, problem = l.execute(oldSignature)
	if problem != nil || result.Status != 401 || result.State.(paymentState) != (paymentState{"PAID", 1, 1}) {
		t.Fatalf("raw-body tampering changed payment: %#v %#v", result, problem)
	}
	for _, invalid := range []string{"malformed", body + "\n", body + "\r\n", "evt-0|order-1|payment.succeeded", "evt-10000|order-1|payment.succeeded"} {
		sequence := l.sequence
		result, problem = l.execute(paymentFields(l, invalid))
		if problem == nil || problem.status != 400 || result != nil || l.sequence != sequence || len(l.paymentEvents) != 1 {
			t.Fatalf("signed invalid body executed: %q %#v %#v", invalid, result, problem)
		}
	}
	result, problem = l.execute(paymentFields(l, "evt-1|order-1|refund.succeeded"))
	if problem != nil || result.Status != 409 || result.State.(paymentState) != (paymentState{"PAID", 1, 1}) {
		t.Fatalf("conflict changed facts: %#v %#v", result, problem)
	}
}
func TestOutOfOrderRefundConverges(t *testing.T) {
	l := fixtureLab()
	result, problem := l.execute(paymentFields(l, "evt-2|order-1|refund.succeeded"))
	if problem != nil || result.Status != 202 || result.State.(paymentState) != (paymentState{"PENDING", 0, 1}) {
		t.Fatalf("early refund: %#v %#v", result, problem)
	}
	result, problem = l.execute(paymentFields(l, "evt-1|order-1|payment.succeeded"))
	if problem != nil || result.Status != 200 || result.State.(paymentState) != (paymentState{"REFUNDED", 0, 2}) || len(result.Events) != 2 {
		t.Fatalf("late payment resurrected access: %#v %#v", result, problem)
	}
	result, problem = l.execute(fields(l, "payment", "timeout"))
	if problem != nil || result.State.(paymentState) != (paymentState{"REFUNDED", 0, 2}) {
		t.Fatalf("timeout resurrected access: %#v %#v", result, problem)
	}
}
func TestRetryAtCapacityAndOverflow(t *testing.T) {
	l := fixtureLab()
	for id := 1; id <= maxEvents; id++ {
		result, problem := l.execute(paymentFields(l, fmt.Sprintf("evt-%d|order-1|payment.succeeded", id)))
		if problem != nil || result.State.(paymentState).ProcessedEvents != id {
			t.Fatalf("event %d: %#v %#v", id, result, problem)
		}
	}
	sequence := l.sequence
	result, problem := l.execute(paymentFields(l, "evt-101|order-1|payment.succeeded"))
	if problem == nil || problem.status != 409 || result != nil || l.sequence != sequence || len(l.paymentEvents) != maxEvents {
		t.Fatalf("overflow changed ledger: %#v %#v", result, problem)
	}
	result, problem = l.execute(paymentFields(l, "evt-1|order-1|payment.succeeded"))
	if problem != nil || result.Status != 200 || result.Events[0].Type != paymentDuplicate || result.State.(paymentState) != (paymentState{"PAID", 1, 100}) {
		t.Fatalf("retry at capacity: %#v %#v", result, problem)
	}
}
func TestConcurrentDeliverySingleEffectAndMonotonicEvents(t *testing.T) {
	l := fixtureLab()
	const count = 32
	sequences := make(chan uint64, count)
	var wait sync.WaitGroup
	for n := 0; n < count; n++ {
		wait.Add(1)
		go func() {
			defer wait.Done()
			result, problem := l.execute(paymentFields(l, "evt-1|order-1|payment.succeeded"))
			if problem != nil || result.State.(paymentState) != (paymentState{"PAID", 1, 1}) {
				t.Errorf("concurrent delivery: %#v %#v", result, problem)
				return
			}
			sequences <- result.Events[0].Sequence
		}()
	}
	wait.Wait()
	close(sequences)
	ordered := make([]int, 0, count)
	for sequence := range sequences {
		ordered = append(ordered, int(sequence))
	}
	sort.Ints(ordered)
	if len(ordered) != count {
		t.Fatalf("missing requests: %d", len(ordered))
	}
	for n, sequence := range ordered {
		if sequence != n+1 {
			t.Fatalf("sequence race: %v", ordered)
		}
	}
	if len(l.paymentEvents) != 1 || l.sequence != count {
		t.Fatalf("retained duplicate effects: %#v", l)
	}
}
func TestExpiryAndCounterBoundsFailClosed(t *testing.T) {
	for _, bound := range []string{"expiry", "sequence", "store"} {
		l := fixtureLab()
		switch bound {
		case "expiry":
			l.expiresAt = time.Now().Add(-time.Second)
		case "sequence":
			l.sequence = maxSequence - 3
		case "store":
			l.storeReads = maxStoreReads
		}
		sequence := l.sequence
		result, problem := l.execute(paymentFields(l, "evt-1|order-1|payment.succeeded"))
		if problem == nil || problem.status != 503 || result != nil || l.sequence != sequence || len(l.paymentEvents) != 0 {
			t.Fatalf("%s failed open: %#v %#v", bound, result, problem)
		}
	}
}
func TestHTTPDenialsAndSafeProjection(t *testing.T) {
	l := fixtureLab()
	body := url.Values{"runId": {l.runID}, "pattern": {"security"}, "operationId": {"authorize"}, "identity": {"editor"}}.Encode()
	for _, test := range []struct {
		method, path, content, body string
		status                      int
	}{
		{"PUT", "/request", "application/x-www-form-urlencoded", body, 405},
		{"GET", "/request", "application/x-www-form-urlencoded", "", 405},
		{"POST", "/request?target=other", "application/x-www-form-urlencoded", body, 400},
		{"POST", "//request", "application/x-www-form-urlencoded", body, 404},
		{"POST", "/request", "application/json", body, 415},
		{"POST", "/request", "application/x-www-form-urlencoded", strings.Repeat("x", bodyLimit+1), 413},
		{"POST", "/request", "application/x-www-form-urlencoded", body + "&identity=editor", 400},
	} {
		request := httptest.NewRequest(test.method, test.path, strings.NewReader(test.body))
		request.Header.Set("Content-Type", test.content)
		request.Header.Set("Content-Length", fmt.Sprint(len(test.body)))
		recorder := httptest.NewRecorder()
		l.ServeHTTP(recorder, request)
		var projected map[string]any
		if err := json.Unmarshal(recorder.Body.Bytes(), &projected); err != nil {
			t.Fatal(err)
		}
		if recorder.Code != test.status || projected["error"] == nil || projected["events"] != nil || l.sequence != 0 {
			t.Fatalf("HTTP %s %s: %d %s", test.method, test.path, recorder.Code, recorder.Body)
		}
	}
	request := httptest.NewRequest("POST", "/request", strings.NewReader(body))
	request.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	request.Header.Set("Content-Length", fmt.Sprint(len(body)))
	recorder := httptest.NewRecorder()
	l.ServeHTTP(recorder, request)
	if recorder.Code != 200 || recorder.Body.Len() > responseLimit || strings.Contains(recorder.Body.String(), identityKey) || strings.Contains(recorder.Body.String(), paymentKey) {
		t.Fatalf("unsafe response: %d %s", recorder.Code, recorder.Body)
	}
	if recorder.Header().Get("Cache-Control") != "no-store" {
		t.Fatal("execution projection must not be cached")
	}
}
