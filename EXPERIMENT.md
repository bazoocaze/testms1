# Experiment: Moving Maven Repository Configuration from `pom.xml` to a Versioned `settings.xml` in CI

## Goal

Keep the `pom.xml` clean by removing the `<repositories>` section (which points to a private/external Maven repository) and instead place that configuration inside a versioned `settings.xml` file that lives alongside the CI workflow.

## Hypothesis

The `actions/setup-java` GitHub Action only configures Maven **authentication** (server credentials) in `~/.m2/settings.xml`. It does **not** provide any input to define custom Maven repository URLs. Therefore, the repository URL must be declared somewhere Maven can read it. We can place it in a `settings.xml` file committed to the repository and copied into `~/.m2/` during the CI run.

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

### After

1. **`pom.xml`** — No `<repositories>` section. The file is cleaner and contains only project metadata, dependencies, and build plugins.

2. **`.github/workflows/settings.xml`** — A versioned Maven `settings.xml` file placed alongside the workflow definition. It contains:
   - The `<server>` block with authentication credentials (using environment variables).
   - A `<profile>` with the `<repository>` definition.
   - An `<activeProfile>` to activate the profile automatically.

3. **`.github/workflows/build.yml`** — The CI workflow:
   - Copies the versioned `settings.xml` into `~/.m2/settings.xml` **before** `setup-java` runs.
   - Sets `overwrite-settings: false` on `setup-java` so it does not overwrite our custom `settings.xml`.
   - Uses `server-id: github` so `setup-java` can still inject the `GITHUB_TOKEN` into the server credentials (though the token is also passed via `GITHUB_TOKEN` environment variable).

## Result

✅ The build succeeded. Maven resolved the dependency from the external repository using the repository URL defined in the versioned `settings.xml`, without needing a `<repositories>` block in `pom.xml`.

## Key Takeaways

- `actions/setup-java` only manages authentication (`settings.xml` servers), **not** repository URLs.
- A versioned `settings.xml` can be kept in `.github/workflows/` (or any path in the repo) and copied into `~/.m2/` during CI.
- `overwrite-settings: false` is essential to prevent `setup-java` from clobbering the custom `settings.xml`.
- This approach keeps `pom.xml` portable and free of CI-specific repository configuration.

## Developer Workstation Setup

On a local machine, the developer still needs a `~/.m2/settings.xml` with both the repository URL and authentication. The versioned `settings.xml` in `.github/workflows/` is **not** used locally — it is CI-only. Instead, the developer maintains a personal `~/.m2/settings.xml` that looks like this:

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

> **Important:** The developer's `~/.m2/settings.xml` is **personal and never committed to the repository**. It contains the developer's own GitHub token with at least `read:packages` scope. The token is obtained from GitHub Settings → Developer settings → Personal access tokens → Fine-grained tokens (or classic). The `OWNER` in the URL is replaced with the actual GitHub owner (user or organization) that publishes the packages.

### Why not use the versioned `settings.xml` locally?

The versioned `settings.xml` uses `${env.GITHUB_ACTOR}` and `${env.GITHUB_TOKEN}` — environment variables that only exist in the GitHub Actions runner. These variables are not available on a local machine, so the file would not work as-is. Additionally, committing a token or any credential to the repository is a security risk.

## Applicability

This pattern is useful when:

- You want to keep `pom.xml` clean and free of CI-specific or environment-specific repository declarations.
- You consume Maven packages from GitHub Packages, GitLab Packages, or any private Maven registry that requires both authentication and a custom repository URL.
- You want the repository configuration to be version-controlled alongside the CI workflow rather than embedded in the project metadata.