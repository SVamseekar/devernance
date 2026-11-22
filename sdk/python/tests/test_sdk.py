"""Unit tests for the SDK and CLI that need no server. Run: python3 -m unittest discover -s tests"""

import base64
import hashlib
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stdout

from devernance import ActionDenied, ApprovalPending, ApprovalRejected, GovernanceClient, GovernanceError
from devernance import cli, manifests, verify
from devernance.errors import GovernanceUnavailable


class FakeClient(GovernanceClient):
    """A client whose HTTP calls are answered from a script instead of a server."""

    def __init__(self, answers):
        super().__init__(server="http://devernance.test", api_key="dvn_test", system="claims-agent")
        self.answers = list(answers)
        self.calls = []

    def request(self, method, path, body=None, query=None, authenticated=True):
        self.calls.append((method, path, body))
        answer = self.answers.pop(0)
        if isinstance(answer, Exception):
            raise answer
        return answer


def decision(status, authorized, approval=None, **extra):
    raw = {"id": "d-1", "status": status, "authorized": authorized, "enforced": True, "reason": "because",
           "rule": "payment.refund", "authorizationToken": "tok" if authorized else None, "approval": approval,
           "action": "payment.refund", "resource": {}, "context": {"amount": 1}}
    raw.update(extra)
    return raw


class GuardTest(unittest.TestCase):
    def test_runs_the_block_only_when_allowed_and_reports_the_outcome(self):
        client = FakeClient([decision("ALLOW", True), decision("ALLOW", True)])
        with client.guard("payment.refund", context={"amount": 1}) as granted:
            self.assertEqual(granted.token, "tok")
        self.assertEqual(client.calls[1][1], "/api/v1/governance/decisions/d-1/execution")
        self.assertEqual(client.calls[1][2], {"status": "EXECUTED"})

    def test_a_denied_action_never_runs(self):
        client = FakeClient([decision("DENY", False)])
        ran = []
        with self.assertRaises(ActionDenied) as raised:
            with client.guard("payment.refund"):
                ran.append(True)
        self.assertEqual(ran, [])
        self.assertIsInstance(raised.exception, PermissionError)
        self.assertEqual(len(client.calls), 1)

    def test_a_review_raises_pending_without_waiting_by_default(self):
        client = FakeClient([decision("REVIEW", False, approval={"id": "a-1", "status": "PENDING"})])
        with self.assertRaises(ApprovalPending) as raised:
            with client.guard("payment.refund"):
                self.fail("must not run")
        self.assertEqual(raised.exception.decision.id, "d-1")

    def test_waits_for_an_approval_and_stops_on_a_rejection(self):
        pending = decision("REVIEW", False, approval={"id": "a-1", "status": "PENDING"})
        approved = decision("REVIEW", True, approval={"id": "a-1", "status": "APPROVED"})
        client = FakeClient([pending, approved, approved])
        with client.guard("payment.refund", context={"amount": 1}, wait_for_approval=5, poll_interval=0.01):
            pass
        self.assertEqual(client.calls[-1][2], {"status": "EXECUTED"})

        rejected = decision("REVIEW", False, approval={"id": "a-1", "status": "REJECTED", "rationale": "no"})
        client = FakeClient([pending, rejected])
        with self.assertRaises(ApprovalRejected):
            with client.guard("payment.refund", wait_for_approval=5, poll_interval=0.01):
                self.fail("must not run")

    def test_a_failing_block_is_reported_as_failed_and_the_error_continues(self):
        client = FakeClient([decision("ALLOW", True), decision("ALLOW", True)])
        with self.assertRaises(ValueError):
            with client.guard("payment.refund"):
                raise ValueError("bank down")
        self.assertEqual(client.calls[1][2]["status"], "FAILED")
        self.assertIn("bank down", client.calls[1][2]["detail"])

    def test_a_reporting_failure_after_the_action_ran_does_not_look_like_a_failed_action(self):
        client = FakeClient([decision("ALLOW", True), GovernanceUnavailable("down")])
        with self.assertLogs("devernance", level="ERROR"):
            with client.guard("payment.refund"):
                pass

    def test_no_answer_means_no_action(self):
        client = FakeClient([GovernanceUnavailable("timeout")])
        with self.assertRaises(GovernanceUnavailable):
            with client.guard("payment.refund"):
                self.fail("must not run")

    def test_resume_refuses_a_decision_made_for_another_request(self):
        client = FakeClient([decision("REVIEW", True, approval={"id": "a", "status": "APPROVED"})])
        with self.assertRaises(GovernanceError):
            with client.guard("payment.refund", context={"amount": 999}, resume="d-1"):
                self.fail("must not run")

    def test_verify_names_the_protected_system(self):
        client = FakeClient([{"valid": True}, {"valid": False}])
        client.verify_authorization("tok", "payment.refund", context={"amount": 1})
        self.assertEqual(client.calls[0][1], "/api/v1/governance/authorizations/verify")
        self.assertEqual(client.calls[0][2]["system"], "claims-agent")
        client.verify_authorization("tok", "payment.refund", system="payments")
        self.assertEqual(client.calls[1][2]["system"], "payments")

    def test_a_client_without_server_or_key_refuses_to_start(self):
        old = {k: os.environ.pop(k, None) for k in ("DEVERNANCE_SERVER", "DEVERNANCE_API_KEY")}
        os.environ["DEVERNANCE_CONFIG"] = os.path.join(tempfile.mkdtemp(), "none.json")
        try:
            with self.assertRaises(GovernanceError):
                GovernanceClient()
        finally:
            os.environ.pop("DEVERNANCE_CONFIG")
            for key, value in old.items():
                if value is not None:
                    os.environ[key] = value


class GovernedDecoratorTest(unittest.TestCase):
    def test_sends_facts_from_the_call_and_hands_over_the_decision(self):
        client = FakeClient([decision("ALLOW", True), decision("ALLOW", True)])

        @client.governed("payment.refund",
                         resource=lambda session, customer_id, amount: {"customer_id": customer_id},
                         context=lambda session, customer_id, amount: {"amount": amount})
        def refund(session, customer_id, amount, governance_decision=None):
            return governance_decision.token

        self.assertEqual(refund({}, "CUST-102", 10), "tok")
        sent = client.calls[0][2]
        self.assertEqual(sent["resource"], {"customer_id": "CUST-102"})
        self.assertEqual(sent["context"], {"amount": 10})
        self.assertEqual(refund.__name__, "refund")

    def test_a_denied_call_never_runs_the_function(self):
        client = FakeClient([decision("DENY", False)])
        ran = []

        @client.governed("customer.export")
        def export():
            ran.append(True)

        with self.assertRaises(ActionDenied):
            export()
        self.assertEqual(ran, [])


class ManifestTest(unittest.TestCase):
    def test_loads_resources_and_tests_and_keeps_versions_that_look_like_dates_as_text(self):
        folder = tempfile.mkdtemp()
        with open(os.path.join(folder, "a.yaml"), "w") as handle:
            handle.write("kind: AISystem\nspec:\n  model: {name: gpt, version: 2025-04-14}\n---\n"
                         "kind: PolicyTests\ncases:\n  - {name: x, system: s, action: a.b}\n")
        resources, cases = manifests.load([folder])
        self.assertEqual(resources[0]["spec"]["model"]["version"], "2025-04-14")
        self.assertEqual(cases[0]["action"], "a.b")

    def test_rejects_an_unknown_kind(self):
        folder = tempfile.mkdtemp()
        with open(os.path.join(folder, "a.yaml"), "w") as handle:
            handle.write("kind: Deployment\n")
        with self.assertRaises(GovernanceError):
            manifests.load([folder])

    def test_init_writes_templates_that_load(self):
        folder = os.path.join(tempfile.mkdtemp(), "governance")
        with redirect_stdout(io.StringIO()):
            self.assertEqual(cli.main(["init", folder, "--name", "demo-agent"]), 0)
        resources, cases = manifests.load([folder])
        self.assertEqual(sorted(r["kind"] for r in resources), ["AISystem", "GovernancePolicy"])
        self.assertEqual(resources[0]["metadata"]["name"] if resources[0]["kind"] == "AISystem"
                         else resources[1]["metadata"]["name"], "demo-agent")
        self.assertEqual(len(cases), 4)


class CliTest(unittest.TestCase):
    def test_key_value_pairs_use_json_for_numbers_and_text_otherwise(self):
        self.assertEqual(cli._pairs(["amount=75000", "id=CUST-102", "ok=true", "x=1.5"]),
                         {"amount": 75000, "id": "CUST-102", "ok": True, "x": 1.5})
        with self.assertRaises(GovernanceError):
            cli._pairs(["novalue"])

    def test_usage_errors_exit_3(self):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(cli.main([]), cli.EXIT_USAGE)


class BundleTest(unittest.TestCase):
    def test_refuses_paths_that_leave_the_bundle_folder(self):
        for bad in ("../x", "/etc/passwd", "a/../../b", "a//b", "a\\b", ""):
            with self.assertRaises(verify.VerificationError, msg=bad):
                verify.safe_join("/tmp/bundle", bad)
        self.assertTrue(verify.safe_join("/tmp/bundle", "decisions/x.json").endswith("decisions/x.json"))

    def test_rs256_rejects_a_wrong_signature(self):
        # A small textbook key cannot pass: keys under 2048 bits are refused outright.
        self.assertFalse(verify.rs256_verify(b"data", b"\x00" * 128, (1 << 1023) + 1, 65537))

    def test_rs256_accepts_a_signature_made_with_the_matching_key(self):
        # Checks the PKCS#1 v1.5 encoding, not RSA itself: with exponent 1 the signature is the encoded block.
        # The real RSA path is covered end to end by scripts/governance-e2e.sh against the server's keys.
        modulus = (1 << 2047) + 159
        exponent = 1  # with e = 1, the signature equals the encoded message
        digest_info = verify._SHA256_DIGEST_INFO + hashlib.sha256(b"payload").digest()
        length = 256
        block = b"\x00\x01" + b"\xff" * (length - len(digest_info) - 3) + b"\x00" + digest_info
        self.assertTrue(verify.rs256_verify(b"payload", block, modulus, exponent))
        self.assertFalse(verify.rs256_verify(b"other", block, modulus, exponent))

    def test_detects_a_changed_file_an_extra_file_and_a_changed_manifest(self):
        folder = tempfile.mkdtemp()
        content = '{"a":1}'
        manifest = json.dumps({"files": [{"path": "decisions/x.json",
                                          "sha256": hashlib.sha256(content.encode()).hexdigest()}]})
        verify.write_bundle({"files": {"decisions/x.json": content}, "manifest": manifest, "signature": "x.y.z"},
                            folder)
        original = verify.verify_jws
        verify.verify_jws = lambda token, jwks: {"purpose": "governance-evidence-bundle",
                                                 "manifestSha256": hashlib.sha256(manifest.encode()).hexdigest()}
        try:
            self.assertTrue(verify.verify_bundle(folder, jwks={"keys": []})["valid"])
            with open(os.path.join(folder, "extra.json"), "w") as handle:
                handle.write("{}")
            with self.assertRaisesRegex(verify.VerificationError, "not covered"):
                verify.verify_bundle(folder, jwks={"keys": []})
            os.remove(os.path.join(folder, "extra.json"))
            with open(os.path.join(folder, "decisions", "x.json"), "w") as handle:
                handle.write('{"a":2}')
            with self.assertRaisesRegex(verify.VerificationError, "changed after export"):
                verify.verify_bundle(folder, jwks={"keys": []})
            with open(os.path.join(folder, "manifest.json"), "w") as handle:
                handle.write(manifest + " ")
            with self.assertRaisesRegex(verify.VerificationError, "changed after it was signed"):
                verify.verify_bundle(folder, jwks={"keys": []})
        finally:
            verify.verify_jws = original

    def test_jws_with_an_unknown_key_or_algorithm_is_refused(self):
        header = base64.urlsafe_b64encode(json.dumps({"alg": "none"}).encode()).decode().rstrip("=")
        with self.assertRaisesRegex(verify.VerificationError, "algorithm"):
            verify.verify_jws(header + ".e30.", {"keys": []})
        header = base64.urlsafe_b64encode(json.dumps({"alg": "RS256", "kid": "k"}).encode()).decode().rstrip("=")
        with self.assertRaisesRegex(verify.VerificationError, "No public key"):
            verify.verify_jws(header + ".e30.AA", {"keys": []})


if __name__ == "__main__":
    unittest.main()
