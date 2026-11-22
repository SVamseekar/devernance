"""The `devernance` command line. Run `devernance --help`, or see docs/GOVERNANCE_CLI.md.

Exit codes (stable, for scripts and CI):
    0  success / valid / PASS
    1  a check failed (validation errors, failed policy tests, BLOCKED, DENY, bundle does not verify)
    2  REVIEW (release check needs a person; an authorization waits for approval)
    3  usage or configuration error
    4  Devernance could not be reached, or answered with an error
"""

import argparse
import getpass
import json
import os
import sys
import urllib.request

from . import __version__, config, manifests, verify
from .client import GovernanceClient
from .errors import GovernanceApiError, GovernanceError, GovernanceUnavailable

EXIT_OK = 0
EXIT_FAILED = 1
EXIT_REVIEW = 2
EXIT_USAGE = 3
EXIT_UNAVAILABLE = 4

DEFAULT_DIR = "governance"


# ---- output helpers -------------------------------------------------------------------------------------

def _out(text=""):
    sys.stdout.write(text + "\n")


def _err(text):
    sys.stderr.write(text + "\n")


def _json(value):
    _out(json.dumps(value, indent=2, sort_keys=True))


def _table(rows, headers, indent=""):
    """Prints rows (lists of values) as aligned columns, each line prefixed with `indent`."""
    cells = [[str(header) for header in headers]] + [["" if v is None else str(v) for v in row] for row in rows]
    widths = [max(len(row[i]) for row in cells) for i in range(len(headers))]
    for row in cells:
        _out(indent + "  ".join(value.ljust(widths[i]) for i, value in enumerate(row)).rstrip())


def _findings(findings):
    for finding in findings:
        _out("  %-7s %-18s %s" % (finding["severity"], finding["code"], finding.get("subject") or ""))
        _out("          %s" % finding["message"])


def _pairs(items):
    """Turns ["amount=75000", "currency=INR"] into {"amount": 75000, "currency": "INR"}.

    A value that is valid JSON (a number, true, false, a quoted string, a list) is used as JSON; anything
    else is text. So amount=75000 is a number and customer_id=CUST-102 is text.
    """
    result = {}
    for item in items or []:
        if "=" not in item:
            raise GovernanceError("Expected key=value, got %r" % item)
        key, raw = item.split("=", 1)
        try:
            value = json.loads(raw)
        except ValueError:
            value = raw
        result[key] = value
    return result


def _client(args, system=None):
    return GovernanceClient(server=args.server, api_key=args.api_key, system=system)


def _paths(args):
    if args.file:
        return args.file
    if os.path.isdir(DEFAULT_DIR):
        return [DEFAULT_DIR]
    raise GovernanceError("No ./%s folder here. Pass -f <file or folder>, or run `devernance init`." % DEFAULT_DIR)


# ---- commands -------------------------------------------------------------------------------------------

def cmd_login(args):
    server = args.server or input("Devernance server URL: ").strip()
    api_key = args.api_key or os.environ.get(config.ENV_API_KEY) or getpass.getpass("API key: ").strip()
    if not server or not api_key:
        raise GovernanceError("Both a server URL and an API key are needed.")
    server = server.rstrip("/")
    client = GovernanceClient(server=server, api_key=api_key)
    try:
        client.status()
        note = "The key works."
    except GovernanceApiError as error:
        if error.status == 401:
            _err("Login failed: the server rejected this API key.")
            return EXIT_FAILED
        if error.status != 403:
            raise
        # A RUNTIME or RELEASE_GATE key may not read status; it authenticated, which is all login needs.
        note = "The key works (it is a scoped key: only its own calls are allowed)."
    path = config.save_file(server, api_key)
    _out("Saved to %s. %s" % (path, note))
    return EXIT_OK


def cmd_init(args):
    directory = args.directory
    name = args.name or os.path.basename(os.path.abspath(os.getcwd())).lower().replace(" ", "-").replace("_", "-")
    os.makedirs(directory, exist_ok=True)
    written = []
    for filename, template in (("system.yaml", manifests.SYSTEM_TEMPLATE),
                               ("policy.yaml", manifests.POLICY_TEMPLATE),
                               ("tests.yaml", manifests.TESTS_TEMPLATE)):
        path = os.path.join(directory, filename)
        if os.path.exists(path):
            _out("kept     %s (already exists)" % path)
            continue
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(template.format(name=name))
        written.append(path)
        _out("created  %s" % path)
    if written:
        _out("")
        _out("Next: edit the files, then run")
        _out("  devernance validate     # do system and policy fit together?")
        _out("  devernance test         # does the policy answer as expected?")
        _out("  devernance apply        # make it the approved state")
    return EXIT_OK


def cmd_apply(args):
    resources, _ = manifests.load(_paths(args))
    if not resources:
        raise GovernanceError("No AISystem or GovernancePolicy manifests found.")
    answer = _client(args).apply(resources)
    if args.output == "json":
        _json(answer)
        return EXIT_OK
    for item in answer["resources"]:
        _out("%s/%s %s (version %s)" % (item["kind"], item["name"], item["result"], item["version"]))
    errors = [f for f in answer.get("findings", []) if f["severity"] == "ERROR"]
    if answer.get("findings"):
        _out("")
        _out("Applied, with findings (the release check fails while errors remain):" if errors
             else "Applied, with warnings:")
        _findings(answer["findings"])
    return EXIT_OK


def cmd_validate(args):
    client = _client(args)
    if args.applied is not None:
        answer = client.validate(system=args.applied or None)
    else:
        resources, _ = manifests.load(_paths(args))
        if not resources:
            raise GovernanceError("No AISystem or GovernancePolicy manifests found.")
        answer = client.validate(manifests=resources)
    if args.output == "json":
        _json(answer)
    else:
        if answer["findings"]:
            _findings(answer["findings"])
        _out("%s: %d error(s), %d warning(s)" % (
            "Valid" if answer["valid"] else "Governance check failed", answer["errors"], answer["warnings"]))
    return EXIT_OK if answer["valid"] else EXIT_FAILED


def cmd_test(args):
    resources, cases = manifests.load(_paths(args))
    if not cases:
        raise GovernanceError("No PolicyTests cases found. Add a tests.yaml (see `devernance init`).")
    answer = _client(args).simulate(cases, manifests=None if args.applied else resources)
    if args.output == "json":
        _json(answer)
    else:
        if answer.get("findings"):
            _findings(answer["findings"])
        for result in answer["results"]:
            if result["passed"] is None:
                mark = "    "
            else:
                mark = "ok  " if result["passed"] else "FAIL"
            expected = "" if result["expect"] is None or result["passed"] else " (expected %s)" % result["expect"]
            _out("%s %-8s %s%s" % (mark, result["status"], result["name"], expected))
            if result["passed"] is False:
                _out("              %s" % result["reason"])
        failed = sum(1 for result in answer["results"] if result["passed"] is False)
        _out("%d case(s), %d failed" % (len(answer["results"]), failed))
    return EXIT_OK if answer["passed"] else EXIT_FAILED


def cmd_release_check(args):
    answer = _client(args).release_check(args.system)
    if args.output == "json":
        _json(answer)
    else:
        _out(answer["summary"])
        if answer["findings"]:
            _findings(answer["findings"])
        for report in answer["drift"]:
            _out("  DRIFT   %s" % report["environment"])
            for difference in report["differences"]:
                _out("          %s: approved %s, observed %s" % (
                    difference["property"], difference["approved"], difference["observed"]))
        gate = answer["releaseGate"]
        for blocker in gate.get("blockers") or []:
            _out("  GATE    blocker: %s" % blocker)
        for reason in gate.get("reviewReasons") or []:
            _out("  GATE    review: %s" % reason)
    decision = answer["decision"]
    if decision == "PASS":
        return EXIT_OK
    if decision == "REVIEW" and not args.fail_on_review:
        return EXIT_REVIEW
    return EXIT_FAILED


def cmd_status(args):
    answer = _client(args).status(args.system)
    if args.output == "json":
        _json(answer)
        return EXIT_OK
    if not answer:
        _out("No governed systems. Run `devernance apply` first.")
        return EXIT_OK
    rows = []
    for item in answer:
        policy = item.get("policy")
        drifted = [r["environment"] for r in item["drift"] if not r["inSync"]]
        counts = item["decisionsLast24h"]
        rows.append([
            item["system"], "v%s" % item["systemVersion"], item.get("model") or "-",
            "%s v%s" % (policy["name"], policy["version"]) if policy else "none",
            item["runtimeEnforcement"],
            "ok" if item["validation"]["valid"] else "%d error(s)" % item["validation"]["errors"],
            ", ".join(drifted) if drifted else ("in sync" if item["drift"] else "not reported"),
            item["pendingApprovals"],
            "%s/%s/%s" % (counts.get("ALLOW", 0), counts.get("DENY", 0), counts.get("REVIEW", 0)),
        ])
    _table(rows, ["SYSTEM", "VERSION", "MODEL", "POLICY", "RUNTIME", "VALIDATION", "DRIFT", "PENDING",
                  "24H ALLOW/DENY/REVIEW"])
    return EXIT_OK


def cmd_get(args):
    answer = _client(args).resources(args.system, active_only=not args.all)
    if args.output == "json":
        _json(answer)
        return EXIT_OK
    _table([[r["kind"], r["name"], r["version"], "yes" if r["active"] else "no", r["createdAt"],
             r["contentSha256"][:12]] for r in answer],
           ["KIND", "NAME", "VERSION", "ACTIVE", "APPLIED", "SHA256"])
    return EXIT_OK


def _print_decision(decision):
    _out("Decision   %s" % (decision.get("id") or "(dry run, not recorded)"))
    _out("Status     %s%s" % (decision["status"], "" if decision["enforced"] else " (monitor mode: not enforced)"))
    _out("Authorized %s" % ("yes" if decision["authorized"] else "no"))
    _out("Reason     %s" % decision["reason"])
    _out("Rule       %s" % decision["rule"])
    policy = decision.get("policy")
    _out("Policy     %s" % ("%s v%s" % (policy["name"], policy["version"]) if policy else "none"))
    _out("System     %s v%s" % (decision["system"], decision["systemVersion"]))
    _out("Agent      %s" % decision["agent"])
    _out("Action     %s" % decision["action"])
    _out("Resource   %s" % json.dumps(decision["resource"], sort_keys=True))
    _out("Context    %s" % json.dumps(decision["context"], sort_keys=True))
    if decision.get("model"):
        _out("Model      %s" % decision["model"])
    _out("When       %s" % decision["createdAt"])
    approval = decision.get("approval")
    if approval:
        _out("Approval   %s (roles: %s; expires %s)" % (
            approval["status"], ", ".join(approval["approverRoles"]), approval["expiresAt"]))
        if approval.get("decidedBy"):
            _out("           decided by %s at %s%s" % (
                approval["decidedBy"], approval["decidedAt"],
                ": " + approval["rationale"] if approval.get("rationale") else ""))
    execution = decision.get("execution")
    if execution:
        _out("Execution  %s at %s%s" % (
            execution["status"], execution["recordedAt"],
            " (%s)" % execution["reference"] if execution.get("reference") else ""))
    elif decision["authorized"] and decision.get("id"):
        _out("Execution  not reported")


def cmd_explain(args):
    answer = _client(args).request("GET", "/api/v1/governance/decisions/" + args.decision_id)
    if args.output == "json":
        answer.pop("authorizationToken", None)
        _json(answer)
    else:
        _print_decision(answer)
    return EXIT_OK


def cmd_authorize(args):
    client = _client(args, system=args.system)
    decision = client.authorize(args.action, resource=_pairs(args.resource), context=_pairs(args.context),
                                model=args.model, environment=args.environment, dry_run=args.dry_run)
    if args.output == "json":
        _json(decision.raw)
    else:
        _print_decision(decision.raw)
    if decision.authorized:
        return EXIT_OK
    return EXIT_REVIEW if decision.status == "REVIEW" else EXIT_FAILED


def cmd_decisions(args):
    answer = _client(args).decisions(system=args.system, action=args.action, status=args.status,
                                     since=args.since, until=args.until, limit=args.limit)
    if args.output == "json":
        _json(answer)
        return EXIT_OK
    rows = []
    for d in answer:
        approval = d.get("approval") or {}
        execution = d.get("execution") or {}
        rows.append([d["createdAt"], d["id"], d["system"], d["agent"], d["action"], d["status"],
                     approval.get("status") or "-", execution.get("status") or "-"])
    _table(rows, ["WHEN", "DECISION", "SYSTEM", "AGENT", "ACTION", "STATUS", "APPROVAL", "EXECUTION"])
    return EXIT_OK


def cmd_approvals(args):
    answer = _client(args).approvals(status=args.status, limit=args.limit)
    if args.output == "json":
        _json(answer)
        return EXIT_OK
    _table([[a["requestedAt"], a["id"], a["system"], a["action"], a["status"], ", ".join(a["approverRoles"]),
             a["reason"]] for a in answer],
           ["REQUESTED", "APPROVAL", "SYSTEM", "ACTION", "STATUS", "ROLES", "REASON"])
    if args.output != "json" and answer:
        _out("")
        _out("Approve or reject in the dashboard (Governance > Approvals). An API key cannot approve.")
    return EXIT_OK


def _print_drift(reports):
    if not reports:
        _out("No deployment has reported its state yet.")
        return
    for report in reports:
        state = "IN SYNC" if report["inSync"] else "DRIFT"
        _out("%s  %s / %s  (reported %s by %s)" % (
            state, report["system"], report["environment"], report["observedAt"], report["source"]))
        if report["differences"]:
            _table([[d["property"], d["approved"], d["observed"]] for d in report["differences"]],
                   ["PROPERTY", "APPROVED", "OBSERVED"], indent="  ")
            for note in report["reassessment"]["notes"]:
                _out("  -> %s" % note)


def cmd_drift(args):
    answer = _client(args).drift(args.system)
    if args.output == "json":
        _json(answer)
    else:
        _print_drift(answer)
    return EXIT_FAILED if any(not report["inSync"] for report in answer) else EXIT_OK


def cmd_report_state(args):
    client = _client(args, system=args.system)
    tools = [tool for tool in args.tools.split(",") if tool] if args.tools is not None else None
    answer = client.report_state(model=args.model, prompt_hash=args.prompt_hash, tools=tools,
                                 policy_version=args.policy_version, source=args.source or "devernance-cli",
                                 environment=args.environment)
    if args.output == "json":
        _json(answer)
    else:
        _print_drift([answer])
    return EXIT_OK if answer["inSync"] else EXIT_FAILED


def cmd_graph(args):
    answer = _client(args).graph(args.system)
    if args.output == "json":
        _json(answer)
        return EXIT_OK
    labels = {node["id"]: "%s %s" % (node["type"], node["label"]) for node in answer["nodes"]}
    for edge in answer["edges"]:
        _out("%s  --%s-->  %s" % (labels.get(edge["from"], edge["from"]), edge["relation"],
                                  labels.get(edge["to"], edge["to"])))
    return EXIT_OK


def cmd_closure(args):
    answer = _client(args).closure(args.system, since=args.since, until=args.until)
    if args.output == "json":
        _json(answer)
    else:
        _out("Evidence closure for %s: %s" % (answer["system"], answer["status"]))
        _out("Audit chain: %s" % ("verifies" if answer["auditChainValid"] else "DOES NOT VERIFY"))
        for gap in answer["gaps"]:
            _out("  gap: %s" % gap)
        for rule in answer["rules"]:
            _out("")
            _out("%-6s %s" % (rule["status"], rule["rule"]))
            _out("       requirement: %s" % rule["requirement"])
            _out("       decisions %d (allow %d, deny %d, review %d); executed %d, failed %d, unreported %d" % (
                rule["decisions"], rule["allowed"], rule["denied"], rule["reviewed"], rule["executed"],
                rule["failed"], rule["unreported"]))
            for gap in rule["gaps"]:
                _out("       gap: %s" % gap)
        _out("")
        _out(answer["limitation"])
    return EXIT_OK if answer["status"] == "CLOSED" else EXIT_FAILED


def cmd_evidence_export(args):
    client = _client(args)
    if os.path.exists(args.out) and os.listdir(args.out):
        raise GovernanceError("%s is not empty. Export into a new folder." % args.out)
    bundle = client.evidence_bundle(args.system, since=args.since, until=args.until)
    count = verify.write_bundle(bundle, args.out, jwks=client.jwks())
    summary = verify.verify_bundle(args.out)
    if args.output == "json":
        _json(summary)
    else:
        _out("Exported %d file(s) to %s" % (count, args.out))
        _out("Decisions: %s   manifest sha256: %s" % (summary["counts"]["decisions"], summary["manifestSha256"]))
        _out("Verify any time with: devernance evidence verify %s --jwks %s/.well-known/jwks.json" % (
            args.out, client.server))
    return EXIT_OK


def _load_jwks(source):
    if source.startswith(("http://", "https://")):
        with urllib.request.urlopen(source, timeout=15) as response:  # noqa: S310 - scheme checked above
            return json.loads(response.read().decode("utf-8"))
    with open(source, "r", encoding="utf-8") as handle:
        return json.load(handle)


def cmd_evidence_verify(args):
    try:
        jwks = _load_jwks(args.jwks) if args.jwks else None
        summary = verify.verify_bundle(args.directory, jwks=jwks)
    except verify.VerificationError as error:
        if args.output == "json":
            _json({"valid": False, "reason": str(error)})
        else:
            _out("NOT VERIFIED: %s" % error)
        return EXIT_FAILED
    except (OSError, ValueError) as error:
        raise GovernanceError("Could not read the public keys: %s" % error) from None
    if args.output == "json":
        _json(summary)
    else:
        _out("VERIFIED: %d file(s) match the signed manifest." % summary["files"])
        _out("System %s, generated %s, %s decision(s)." % (
            summary["system"], summary["generatedAt"], summary["counts"]["decisions"]))
        if not summary["independentKeys"]:
            _out("Note: checked against the key copy inside the bundle. For an independent check pass")
            _out("      --jwks https://<issuer>/.well-known/jwks.json")
        _out(summary["limitation"])
    return EXIT_OK


# ---- argument parsing -----------------------------------------------------------------------------------

def build_parser():
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--server", help="Devernance URL (default: DEVERNANCE_SERVER or `devernance login`)")
    common.add_argument("--api-key", help="API key (default: DEVERNANCE_API_KEY or `devernance login`)")
    common.add_argument("-o", "--output", choices=["text", "json"], default="text", help="output format")

    files = argparse.ArgumentParser(add_help=False)
    files.add_argument("-f", "--file", action="append",
                       help="manifest file or folder (repeatable; default: ./governance)")

    period = argparse.ArgumentParser(add_help=False)
    period.add_argument("--from", dest="since", help="start, ISO-8601, e.g. 2026-11-01T00:00:00Z")
    period.add_argument("--to", dest="until", help="end (exclusive), ISO-8601")

    parser = argparse.ArgumentParser(
        prog="devernance",
        description="Define, check, enforce and verify governance for AI systems.",
        epilog="Exit codes: 0 ok, 1 check failed, 2 review needed, 3 usage error, 4 server unreachable or error.")
    parser.add_argument("--version", action="version", version="devernance " + __version__)
    sub = parser.add_subparsers(dest="command", metavar="<command>")

    def add(name, handler, help_text, parents=()):
        command = sub.add_parser(name, help=help_text, description=help_text, parents=[common, *parents])
        command.set_defaults(handler=handler)
        return command

    add("login", cmd_login, "Save the server URL and API key for later commands")

    init = add("init", cmd_init, "Create starter manifests in ./governance")
    init.add_argument("directory", nargs="?", default=DEFAULT_DIR)
    init.add_argument("--name", help="system name (default: the current folder's name)")

    add("apply", cmd_apply, "Make the manifests the approved governance state", [files])

    validate = add("validate", cmd_validate, "Check that system and policy fit together (changes nothing)",
                   [files])
    validate.add_argument("--applied", nargs="?", const="", default=None, metavar="SYSTEM",
                          help="check what is applied on the server instead of local files")

    test = add("test", cmd_test, "Run the PolicyTests cases against the policy (changes nothing)", [files])
    test.add_argument("--applied", action="store_true",
                      help="test the policy applied on the server instead of the local manifests")

    release = add("release-check", cmd_release_check, "May this system be released? For CI pipelines")
    release.add_argument("system")
    release.add_argument("--fail-on-review", action="store_true", help="exit 1 instead of 2 on REVIEW")

    status = add("status", cmd_status, "Overview of governed systems")
    status.add_argument("system", nargs="?")

    get = add("get", cmd_get, "List the applied manifests of a system")
    get.add_argument("system")
    get.add_argument("--all", action="store_true", help="include superseded versions")

    explain = add("explain", cmd_explain, "Show why a decision was made")
    explain.add_argument("decision_id")

    authorize = add("authorize", cmd_authorize, "Ask for a decision by hand (for testing a policy)")
    authorize.add_argument("system")
    authorize.add_argument("action")
    authorize.add_argument("--resource", action="append", metavar="KEY=VALUE")
    authorize.add_argument("--context", action="append", metavar="KEY=VALUE")
    authorize.add_argument("--model")
    authorize.add_argument("--environment")
    authorize.add_argument("--dry-run", action="store_true", help="decide without recording anything")

    decisions = add("decisions", cmd_decisions, "List recorded decisions, newest first", [period])
    decisions.add_argument("system", nargs="?")
    decisions.add_argument("--action")
    decisions.add_argument("--status", choices=["ALLOW", "DENY", "REVIEW"])
    decisions.add_argument("--limit", type=int)

    approvals = add("approvals", cmd_approvals, "List approval requests")
    approvals.add_argument("--status", choices=["PENDING", "APPROVED", "REJECTED", "EXPIRED"])
    approvals.add_argument("--limit", type=int)

    drift = add("drift", cmd_drift, "Compare what deployments run with the approved state (exit 1 on drift)")
    drift.add_argument("system", nargs="?")

    report = add("report-state", cmd_report_state, "Report what a deployment is running (exit 1 on drift)")
    report.add_argument("system")
    report.add_argument("--environment")
    report.add_argument("--model", help="name@version")
    report.add_argument("--prompt-hash")
    report.add_argument("--tools", help="comma-separated tool names")
    report.add_argument("--policy-version", type=int)
    report.add_argument("--source", help="who is reporting, e.g. kubernetes or github-actions")

    graph = add("graph", cmd_graph, "Show how systems, models, tools, actions and rules connect")
    graph.add_argument("system", nargs="?")

    closure = add("closure", cmd_closure, "Per rule: is requirement, enforcement and outcome evidenced?", [period])
    closure.add_argument("system")

    evidence = sub.add_parser("evidence", help="Export and verify signed evidence bundles")
    evidence_sub = evidence.add_subparsers(dest="evidence_command", metavar="<export|verify>")
    export = evidence_sub.add_parser("export", parents=[common, period],
                                     help="Write a signed evidence bundle to a folder")
    export.add_argument("system")
    export.add_argument("--out", required=True, help="new or empty folder to write to")
    export.set_defaults(handler=cmd_evidence_export)
    check = evidence_sub.add_parser("verify", parents=[common],
                                    help="Verify a bundle offline (no server or key needed)")
    check.add_argument("directory")
    check.add_argument("--jwks", help="issuer public keys: a URL or a file (default: the copy in the bundle)")
    check.set_defaults(handler=cmd_evidence_verify)
    return parser


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)
    handler = getattr(args, "handler", None)
    if handler is None:
        parser.print_help()
        return EXIT_USAGE
    try:
        return handler(args)
    except GovernanceUnavailable as error:
        _err("error: %s" % error)
        return EXIT_UNAVAILABLE
    except GovernanceApiError as error:
        _err("error: %s" % error.message)
        if error.status == 401:
            _err("hint: check the API key (`devernance login`).")
        elif error.status == 403:
            _err("hint: this key or role is not allowed to do that. See docs/GOVERNANCE_CLI.md, 'Which key'.")
        return EXIT_USAGE if error.status in (400, 404) else EXIT_UNAVAILABLE
    except GovernanceError as error:
        _err("error: %s" % error)
        return EXIT_USAGE
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
