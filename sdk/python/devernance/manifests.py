"""Reads governance manifests (YAML or JSON) from a file or a folder."""

import json
import os

import yaml

from .errors import GovernanceError

API_VERSION = "devernance.io/v1"
RESOURCE_KINDS = ("AISystem", "GovernancePolicy")
TESTS_KIND = "PolicyTests"


class _Loader(yaml.SafeLoader):
    """SafeLoader that keeps dates as text.

    Plain YAML turns `version: 2025-04-14` into a date object. A model version is a name, not a date.
    """


_Loader.yaml_implicit_resolvers = {
    first: [(tag, pattern) for tag, pattern in resolvers if tag != "tag:yaml.org,2002:timestamp"]
    for first, resolvers in yaml.SafeLoader.yaml_implicit_resolvers.items()
}


def _files(path):
    if os.path.isdir(path):
        found = []
        for folder, _, names in os.walk(path):
            for name in sorted(names):
                if name.lower().endswith((".yaml", ".yml", ".json")):
                    found.append(os.path.join(folder, name))
        return sorted(found)
    if os.path.isfile(path):
        return [path]
    raise GovernanceError("No such file or folder: %s" % path)


def load(paths):
    """Returns (resources, test_cases) from the given files and folders.

    resources: AISystem and GovernancePolicy manifests, as dicts, in file order.
    test_cases: the cases of every PolicyTests document.
    """
    resources = []
    cases = []
    for path in paths:
        for file_path in _files(path):
            try:
                with open(file_path, "r", encoding="utf-8") as handle:
                    if file_path.lower().endswith(".json"):
                        documents = [json.load(handle)]
                    else:
                        documents = list(yaml.load_all(handle, Loader=_Loader))
            except (yaml.YAMLError, ValueError) as error:
                raise GovernanceError("%s: cannot be parsed: %s" % (file_path, error)) from None
            for document in documents:
                if document is None:
                    continue
                if not isinstance(document, dict):
                    raise GovernanceError("%s: each document must be a mapping with kind and spec" % file_path)
                kind = document.get("kind")
                if kind == TESTS_KIND:
                    listed = document.get("cases")
                    if not isinstance(listed, list):
                        raise GovernanceError("%s: PolicyTests needs a list under `cases`" % file_path)
                    cases.extend(listed)
                elif kind in RESOURCE_KINDS:
                    resources.append(document)
                else:
                    raise GovernanceError("%s: unknown kind %r (expected AISystem, GovernancePolicy or PolicyTests)"
                                          % (file_path, kind))
    return resources, cases


SYSTEM_TEMPLATE = """\
# The approved state of one AI system. `devernance apply -f governance/` registers it.
# Deployments report what they really run; any difference from this file is governance drift.
apiVersion: devernance.io/v1
kind: AISystem
metadata:
  name: {name}
spec:
  owner: Platform Engineering
  purpose: Describe what this system does and for whom
  riskClass: limited            # minimal | limited | high | prohibited (EU AI Act risk class)
  riskBasis: Why this risk class applies
  deploymentRegion: EU
  model:
    provider: openai
    name: gpt-4.1
    version: "2025-04-14"
  # promptHash: sha256:...      # hash of the system prompt you approved (optional)
  tools:                        # every tool the agent can call
    - search_records
    - send_payment
"""

POLICY_TEMPLATE = """\
# What the system may do. Evaluated by Devernance, outside the model: a prompt cannot change it.
# Full reference: docs/GOVERNANCE_POLICY_REFERENCE.md
apiVersion: devernance.io/v1
kind: GovernancePolicy
metadata:
  name: {name}-policy
spec:
  target:
    system: {name}
  models:
    approved:                   # models this system may run ("name" or "name@version")
      - gpt-4.1@2025-04-14
  tools:                        # tool name -> the action it performs
    search_records: records.search
    send_payment: payment.send
  rules:                        # an action no rule covers is denied
    - action: records.search
      decision: allow
    - action: payment.send
      decision: allow
      requirement: Payments above 1000 need human approval
      review:
        - when:
            - {{field: context.amount, op: gt, value: 1000}}
          approverRoles: [COMPLIANCE_OFFICER]
          reason: High-value payment
  enforcement:
    deployment: blocking        # blocking | advisory  (does a problem fail the release check?)
    runtime: enforce            # enforce | monitor    (monitor records decisions but blocks nothing)
    onDrift: continue           # continue | block     (block denies everything while a deployment has drifted)
"""

TESTS_TEMPLATE = """\
# Policy unit tests. `devernance test` runs them against the manifests in this folder, before anything is
# applied. Each case says what the policy must answer: ALLOW, DENY or REVIEW.
apiVersion: devernance.io/v1
kind: PolicyTests
cases:
  - name: searching records is allowed
    system: {name}
    action: records.search
    expect: ALLOW
  - name: a small payment is allowed
    system: {name}
    action: payment.send
    context: {{amount: 200}}
    expect: ALLOW
  - name: a large payment needs approval
    system: {name}
    action: payment.send
    context: {{amount: 5000}}
    expect: REVIEW
  - name: anything not in the policy is denied
    system: {name}
    action: records.delete
    expect: DENY
"""
