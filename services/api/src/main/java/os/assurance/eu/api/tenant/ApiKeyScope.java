package os.assurance.eu.api.tenant;

/** What an API key may do. A key otherwise acts with its creator's role, so CI keys are kept to the gate. */
public enum ApiKeyScope {
  /** Only {@code GET /api/v1/ci/release-gate}: what a deploy pipeline needs, and nothing a leaked key could abuse. */
  RELEASE_GATE,
  /** Every endpoint the creator's role allows, except minting keys, inviting users or managing the workspace. */
  FULL;

  static final String RELEASE_GATE_PATH = "/api/v1/ci/release-gate";

  public boolean allows(String method, String path) {
    return this == FULL || ("GET".equalsIgnoreCase(method) && RELEASE_GATE_PATH.equals(path));
  }
}
