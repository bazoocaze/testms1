# Experiment: Moving Maven Repository Configuration from `pom.xml` to a Centralized Composite Action

## Goal

Keep the `pom.xml` clean by removing the `<repositories>` section (which points to a private/external Maven repository) and instead place that configuration in a reusable, centralized way. The experiment evolved through three stages:

1. **Stage 1**: Versioned `settings.xml` inside each project's `.github/workflows/`
2. **Stage 2**: Versioned `settings.xml` removed — `settings.xml` generated dynamically by a centralized composite action
3. **Final**: A reusable composite action (`bazoocaze/github-actions/maven-setup@v1`) that any project can use

## Hypothesis

The `actions/setup-java` GitHub Action only configures Maven **authentication** (server credentials) in `~/.m2/settings.xml`. It does **not** provide any input to define custom Maven repository URLs. Therefore, the repository URL must be declared somewhere Maven can read it.

## Setup

### Before

The `pom.xml` contained a `<repositories>` block:

```xml
<repositories>
  <repository>
    <id>github</id>
    <name>GitHub Packages</name>
    <url>https://maven.pkg.github.com/bazoocaze/*</url>
  </repository>
</repositories>
```

> **Note the wildcard `*` at the end of the URL.** GitHub Packages supports a wildcard pattern where `https://maven.pkg.github.com/OWNER/*` acts as a single catch-all repository URL for all packages published by that GitHub owner (user or organization). Without this wildcard, you would need to declare one `<repository>` per package — e.g., `https://maven.pkg.github.com/OWNER/PACKAGE_NAME` for each library you consume. The wildcard drastically simplifies configuration when consuming multiple packages from the same owner.

### Stage 1: Versioned `settings.xml` per project

Each project had a `.github/workflows/settings.xml` copied manually into `~/.m2/` during CI.

### Stage 2 (Final): Centralized composite action

A dedicated repository (`bazoocaze/github-actions`) hosts a [composite action](https://docs.github.com/en/actions/creating-actions/creating-a-composite-action) that:

1. **Generates** `~/.m2/settings.xml` dynamically with:
   - The `<server>` block with authentication (using `${env.GITHUB_ACTOR}` and `${env.GITHUB_TOKEN}`).
   - A `<profile>` with the `<repository>` definition using the wildcard URL.
   - An `<activeProfile>` to activate it automatically.
2. **Sets up JDK** via `actions/setup-java` with `overwrite-settings: false`.

The action is published at `bazoocaze/github-actions/maven-setup@v1` and accepts inputs:
- `java-version` (default `"21"`)
- `java-distribution` (default `"temurin"`)
- `github-owner` (default `"bazoocaze"`)
- `server-id` (default `"github"`)

Usage in any project:

```yaml
steps:
  - uses: actions/checkout@v4

  - uses: bazoocaze/github-actions/maven-setup@v1
    with:
      java-version: "21"
      github-owner: "bazoocaze"

  - name: Build with Maven
    run: mvn -B compile --file pom.xml
    env:
      GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Pitfalls Encountered

### 1. `GITHUB_TOKEN` must be passed explicitly to every Maven step

The `setup-java` action writes the server block into `settings.xml` referencing `${env.GITHUB_TOKEN}`, but it does **not** make the token available to subsequent steps. You must pass it manually:

```yaml
- name: Build with Maven
  run: mvn -B compile --file pom.xml
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

Failing to do so results in a `401 Unauthorized` error when Maven tries to resolve dependencies from GitHub Packages.

### 2. `overwrite-settings: false`

When using a custom `settings.xml` (whether versioned or generated), you must set `overwrite-settings: false` on `setup-java` to prevent it from overwriting your file.

### 3. Cross-repository package access

`GITHUB_TOKEN` is scoped to the current repository. If your project depends on a package published by **another** repository (even under the same owner), the token may not have access. In that case, use a Personal Access Token (PAT) with `read:packages` scope and pass it as a secret (e.g., `GH_TOKEN`).

## Result

✅ All three projects (`testlib1`, `testms1`, `testlib2`) build successfully using the centralized composite action, with no `<repositories>` in `pom.xml` and no per-project `settings.xml`.

## Key Takeaways

- `actions/setup-java` only manages authentication (`settings.xml` servers), **not** repository URLs.
- A centralized composite action eliminates boilerplate across multiple Maven projects.
- **The `GITHUB_TOKEN` environment variable must be passed to every Maven step** that needs to authenticate against GitHub Packages.
- The wildcard URL `https://maven.pkg.github.com/OWNER/*` allows consuming all packages from an owner with a single repository declaration.
- For cross-repository package access, consider using a PAT instead of `GITHUB_TOKEN`.

## Developer Workstation Setup

On a local machine, the developer still needs a `~/.m2/settings.xml` with both the repository URL and authentication. The composite action is CI-only. The developer maintains a personal `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>github</id>
      <repositories>
        <repository>
          <id>github</id>
          <url>https://maven.pkg.github.com/OWNER/*</url>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>github</activeProfile>
  </activeProfiles>
</settings>
```

> **Important:** The developer's `~/.m2/settings.xml` is **personal and never committed to the repository**. It contains the developer's own GitHub token with at least `read:packages` scope. The `OWNER` in the URL is replaced with the actual GitHub owner.

## Applicability

This pattern is useful when:

- You want to keep `pom.xml` clean and free of CI-specific or environment-specific repository declarations.
- You have multiple Maven projects consuming packages from GitHub Packages (or any private Maven registry).
- You want to centralize CI setup logic in a reusable composite action.