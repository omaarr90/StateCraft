#!/usr/bin/env python3
"""Build the pinned CPU-only QuEST runner used by comparison benchmarks."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


QUEST_REPO = "https://github.com/QuEST-Kit/QuEST.git"
QUEST_TAG = "v4.2.0"
QUEST_COMMIT = "9d7618d7263e3bfba433b88cf1eac0647f08fa0a"


def run(command: list[str], cwd: Path | None = None) -> None:
    subprocess.run(command, cwd=cwd, check=True)


def ensure_quest_source(source_dir: Path) -> None:
    if not source_dir.exists():
        source_dir.parent.mkdir(parents=True, exist_ok=True)
        run(["git", "clone", "--branch", QUEST_TAG, "--depth", "1", QUEST_REPO, str(source_dir)])
    commit = subprocess.check_output(["git", "-C", str(source_dir), "rev-parse", "HEAD"], text=True).strip()
    if commit != QUEST_COMMIT:
        run(["git", "-C", str(source_dir), "fetch", "--depth", "1", "origin", QUEST_TAG])
        run(["git", "-C", str(source_dir), "checkout", "--detach", QUEST_COMMIT])
        commit = subprocess.check_output(["git", "-C", str(source_dir), "rev-parse", "HEAD"], text=True).strip()
    if commit != QUEST_COMMIT:
        raise RuntimeError(f"QuEST source is not pinned to {QUEST_COMMIT}: {commit}")


def build_runner(repo_root: Path, source_dir: Path, build_dir: Path, bin_dir: Path) -> Path:
    source_file = repo_root / "scripts/benchmark/comparison/quest_runner.cpp"
    exe_name = "statecraft_quest_runner"
    bin_dir.mkdir(parents=True, exist_ok=True)
    run(
        [
            "cmake",
            "-S",
            str(source_dir),
            "-B",
            str(build_dir),
            "-D",
            f"USER_SOURCE={source_file}",
            "-D",
            f"OUTPUT_EXE={exe_name}",
            "-D",
            f"CMAKE_RUNTIME_OUTPUT_DIRECTORY={bin_dir}",
            "-D",
            "ENABLE_MULTITHREADING=OFF",
            "-D",
            "ENABLE_DISTRIBUTION=OFF",
            "-D",
            "ENABLE_CUDA=OFF",
            "-D",
            "ENABLE_HIP=OFF",
            "-D",
            "ENABLE_CUQUANTUM=OFF",
            "-D",
            "ENABLE_TESTING=OFF",
            "-D",
            "BUILD_EXAMPLES=OFF",
            "-D",
            "BUILD_SHARED_LIBS=OFF",
        ]
    )
    run(["cmake", "--build", str(build_dir), "--target", exe_name, "--parallel"])
    suffix = ".exe" if sys.platform == "win32" else ""
    executable = bin_dir / f"{exe_name}{suffix}"
    if not executable.is_file():
        raise RuntimeError(f"QuEST runner was not produced at {executable}")
    return executable


def main() -> int:
    repo_root = Path(__file__).resolve().parents[3]
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", type=Path, default=repo_root / "build/external/quest-src")
    parser.add_argument("--build-dir", type=Path, default=repo_root / "build/external/quest-build")
    parser.add_argument("--bin-dir", type=Path, default=repo_root / "build/external/quest-bin")
    args = parser.parse_args()

    ensure_quest_source(args.source_dir)
    executable = build_runner(repo_root, args.source_dir, args.build_dir, args.bin_dir)
    print(executable)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"build_quest_runner failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
