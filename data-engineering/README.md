# data-engineering

QuickPoll real-time analytics pipeline using [uv](https://docs.astral.sh/uv/), [rav](https://github.com/jmitchel3/rav), [ruff](https://docs.astral.sh/ruff/), Docker, and GitHub Actions CI.

## Prerequisites

- **Python 3.11+**
- **uv** — [astral.sh/uv](https://docs.astral.sh/uv/getting-started/installation/)

## Setup

```powershell
uv sync
```

## Directory Structure

See [DIRECTORY_STRUCTURE.md](DIRECTORY_STRUCTURE.md) for the full layout.

## Usage

| Command | Description |
|---------|-------------|
| `rav x run` | Run main pipeline |
| `rav x test` | Run pytest |
| `rav x test-cov` | Run pytest with coverage |
| `rav x lint` | Ruff lint |
| `rav x format-fix` | Ruff format |
| `rav list` | List all commands |

## Docker

```powershell
docker build -t data-engineering .
docker run --rm data-engineering
```

## Documentation

- [PIPELINE_ARCHITECTURE.md](PIPELINE_ARCHITECTURE.md)
- [docs/](docs/)
