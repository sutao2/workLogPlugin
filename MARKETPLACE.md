# JetBrains Marketplace Listing

## Upload Package

Build the ZIP:

```bash
./gradlew clean buildPlugin
```

Upload the generated file from:

```text
build/distributions/
```

Automated publishing uses the Marketplace token from `PUBLISH_TOKEN`:

```bash
PUBLISH_TOKEN=your-token ./gradlew publishPlugin
```

## Short Description

Generate AI-assisted work logs, code review reports, and weekly/monthly/yearly reports from Git activity inside IntelliJ-based IDEs.

## Full Description

WorkLog helps developers turn Git activity into daily work logs, AI summaries, code review reports, and periodic reports directly inside IntelliJ-based IDEs.

Key features:

- Generate Markdown daily work logs from Git commits and optional local code changes.
- Create AI summaries through OpenAI-compatible or custom JSON API providers.
- Review staged changes before commit and review selected historical commits from Git Log.
- Browse, search, preview, and reopen saved work logs.
- Generate weekly, monthly, yearly, and custom range reports from saved logs.
- Configure API providers, code access permission, review prompts, file filters, storage, and output templates.
- Export logs and review reports to Markdown, HTML, or PDF.

## Screenshot Files

Use the screenshots from the request with these final filenames under `screenshots/` before upload:

- `worklog-tool-window.png` - main WorkLog tool window and generated Markdown log.
- `code-review-dialog.png` - AI code review result dialog.
- `code-review-html-report.png` - exported code review HTML report.
- `statistics-report.png` - work statistics and weekly report screen.
- `history-dialog.png` - history search and preview dialog.
- `settings-api-config-redacted.png` - settings screen with API key fully redacted.

Do not publish the provided settings screenshot as-is. It exposes part of an API key and must be redacted or replaced with fake data first.

## Tags

`worklog`, `git`, `ai`, `code review`, `productivity`, `markdown`, `report`

## Release Notes

Initial Marketplace release with AI-assisted work logs, code review, history search, and weekly/monthly/yearly reports.

## Privacy Notes

WorkLog stores generated logs locally in the project by default. Code and diffs are only sent to the configured AI API when the user enables code access and triggers AI-powered generation or review.
