"""Build the fixed Windows entry and the same source on GitHub's native Mac runners."""
import argparse
import base64
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]


def command(args, **kwargs):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, **kwargs)
    if result.returncode:
        message = (result.stderr or result.stdout).decode("utf-8", errors="replace")
        raise RuntimeError("\n".join(message.splitlines()[-30:]))
    return result.stdout


def api(repo, endpoint, payload=None):
    args = ["gh", "api", f"repos/{repo}/{endpoint}"]
    options = {}
    if payload is not None:
        args += ["--method", "POST", "--input", "-"]
        options["input"] = json.dumps(payload).encode()
    for attempt in range(4):
        try:
            data = command(args, **options)
            break
        except RuntimeError:
            if payload is not None or attempt == 3:
                raise
            time.sleep(2 ** (attempt + 1))
    return json.loads(data) if data.strip() else {}


def publish_source(repo, base_branch):
    # Publish committed source only: never stage files, include untracked files, or force-push.
    command(["git", "diff", "--quiet", "HEAD", "--"])
    base = api(repo, f"git/ref/heads/{base_branch}")["object"]["sha"]
    base_tree = api(repo, f"git/commits/{base}")["tree"]["sha"]
    remote = api(repo, f"git/trees/{base_tree}?recursive=1")
    if remote.get("truncated"):
        raise RuntimeError("远端文件列表不完整，已停止发布。")
    old = {item["path"]: item for item in remote["tree"] if item["type"] != "tree"}
    local = {}
    for entry in command(["git", "ls-tree", "-rz", "HEAD"]).split(b"\0"):
        if not entry:
            continue
        header, path = entry.split(b"\t", 1)
        mode, kind, sha = header.decode().split()
        local[path.decode("utf-8")] = {"mode": mode, "type": kind, "sha": sha}
    changes = []
    for path, item in local.items():
        previous = old.get(path, {})
        if all(previous.get(key) == item[key] for key in ("mode", "type", "sha")):
            continue
        if item["type"] == "blob":
            content = command(["git", "cat-file", "blob", item["sha"]])
            blob = api(repo, "git/blobs", {
                "encoding": "base64", "content": base64.b64encode(content).decode(),
            })
            if blob["sha"] != item["sha"]:
                raise RuntimeError(f"文件校验失败：{path}")
        changes.append({"path": path, **item})
    for path, item in old.items():
        if path not in local:
            changes.append({"path": path, "mode": item["mode"], "type": item["type"], "sha": None})
    tree = api(repo, "git/trees", {"base_tree": base_tree, "tree": changes})["sha"] if changes else base_tree
    expected = command(["git", "rev-parse", "HEAD^{tree}"]).decode().strip()
    if tree != expected:
        raise RuntimeError("云端源码校验失败，未触发构建。")
    commit = api(repo, "git/commits", {
        "message": "Build integrated desktop packages", "tree": tree, "parents": [base],
    })["sha"]
    branch = "codex/build-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    api(repo, "git/refs", {"ref": f"refs/heads/{branch}", "sha": base})
    # Updating a branch triggers the push workflow even when it is absent on the default branch.
    command(["gh", "api", "--method", "PATCH", f"repos/{repo}/git/refs/heads/{branch}",
             "--input", "-"], input=json.dumps({"sha": commit, "force": False}).encode())
    print(f"云端源码已校验：{branch}（{tree[:12]}）", flush=True)
    return branch, commit


def build_windows():
    if os.name != "nt":
        print("当前不是 Windows，本地打包交由 GitHub Windows runner。", flush=True)
        return
    env = os.environ.copy()
    if not env.get("JAVA_HOME"):
        candidates = sorted((Path(env.get("LOCALAPPDATA", "")) / "ChurchPresenterTools").glob("jdk-21*"))
        if candidates:
            env["JAVA_HOME"] = str(candidates[-1])
    args = [str(ROOT / "gradlew.bat"), "--quiet", "--max-workers=3"]
    # Optional machine-local repository/proxy configuration; no machine paths are saved in source.
    if env.get("CHURCHPRESENTER_GRADLE_INIT"):
        args += ["--init-script", env["CHURCHPRESENTER_GRADLE_INIT"]]
    args += [":composeApp:createDistributable"]
    print("正在构建 Windows 固定入口…", flush=True)
    with tempfile.TemporaryFile() as output:
        result = subprocess.run(args, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
        if result.returncode:
            output.seek(0)
            print("\n".join(output.read().decode("utf-8", errors="replace").splitlines()[-30:]))
            raise RuntimeError("Windows 构建失败；若文件被占用，请先退出软件。")
    entry = ROOT / "composeApp/build/compose/binaries/main/app/ChurchPresenter/ChurchPresenter.exe"
    if not entry.is_file():
        raise RuntimeError("未找到 Windows 固定入口。")
    print(f"Windows 构建成功：{entry}", flush=True)


def wait_for_packages(repo, branch, commit, no_wait):
    run = None
    for _ in range(24):
        runs = api(repo, f"actions/runs?branch={branch}&event=push&per_page=10")["workflow_runs"]
        run = next((item for item in runs if item["head_sha"] == commit), None)
        if run:
            break
        time.sleep(5)
    if not run:
        raise RuntimeError(f"未检测到构建任务，请检查 https://github.com/{repo}/actions")
    print(f"构建任务：{run['html_url']}", flush=True)
    if no_wait:
        return
    retried = set()
    previous = None
    while True:
        run = api(repo, f"actions/runs/{run['id']}")
        jobs = api(repo, f"actions/runs/{run['id']}/jobs")["jobs"]
        state = [(job["name"], job["status"], job["conclusion"]) for job in jobs]
        if state != previous:
            print(" | ".join(f"{name}: {conclusion or status}" for name, status, conclusion in state
                             if conclusion != "skipped"), flush=True)
            previous = state
        if run["status"] == "completed":
            retry = None
            for job in jobs:
                if job["name"] in retried:
                    continue
                capacity_cancelled = False
                if job["conclusion"] == "cancelled":
                    annotations = api(repo, f"check-runs/{job['id']}/annotations")
                    capacity_cancelled = any("capacity constraints" in item["message"].lower()
                                             or "not acquired by runner" in item["message"].lower()
                                             for item in annotations)
                if job["conclusion"] in ("failure", "timed_out") or capacity_cancelled:
                    retry = job
                    break
            if retry:
                print(f"重试云端任务（每个平台仅一次）：{retry['name']}", flush=True)
                api(repo, f"actions/jobs/{retry['id']}/rerun", {})
                retried.add(retry["name"])
                time.sleep(10)
                continue
            artifacts = api(repo, f"actions/runs/{run['id']}/artifacts")["artifacts"]
            for artifact in artifacts:
                if not artifact["expired"]:
                    print(f"{artifact['name']}: {run['html_url']}/artifacts/{artifact['id']}")
            if run["conclusion"] != "success":
                raise RuntimeError("部分云端构建失败，请查看上述任务链接。")
            names = {item["name"] for item in artifacts if not item["expired"]}
            if not {"windows-msi", "macos-dmg", "macos-dmg-x64"}.issubset(names):
                raise RuntimeError("任务结束，但缺少预期的三平台安装包。")
            print("三平台构建成功。未配置 Apple 签名凭据时，Mac 包未签名、未公证。")
            return
        time.sleep(30)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="cxze299/ChurchsProjection")
    parser.add_argument("--base-branch", default="builds/integrated-20261115")
    parser.add_argument("--cloud-only", action="store_true")
    parser.add_argument("--no-wait", action="store_true")
    args = parser.parse_args()
    for tool in ("git", "gh"):
        if not shutil.which(tool):
            raise RuntimeError(f"请先安装 {tool}。")
    command(["gh", "auth", "status"])
    if subprocess.run(["git", "diff", "--quiet", "HEAD", "--"], cwd=ROOT).returncode:
        raise RuntimeError("存在未提交的源码修改，请提交后再构建，以保证三个版本使用同一份源码。")
    if not args.cloud_only:
        build_windows()
    branch, commit = publish_source(args.repo, args.base_branch)
    wait_for_packages(args.repo, branch, commit, args.no_wait)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, KeyboardInterrupt) as error:
        print(f"构建已停止：{error}", file=sys.stderr)
        sys.exit(1)
