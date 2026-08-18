# Issue tracker: Local Markdown

Issues and PRDs for this repo live as markdown files in `.agents/`.

## Conventions

- Specs / PRDs live at `.agents/specs/<slug>.md`
- Issues live at `.agents/issues/<NN>-<slug>.md`, numbered from `01`
- Triage state is recorded as a `Status:` line near the top of each issue file (see `triage-labels.md` for the role strings)
- Comments and conversation history append to the bottom of the file under a `## Comments` heading

Note: `.agents/refs/` holds this repo's reference docs — it is not an issues directory.

## When a skill says "publish to the issue tracker"

Create a new file under `.agents/issues/` (creating the directory if needed).

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path. The user will normally pass the path or the issue number directly.
