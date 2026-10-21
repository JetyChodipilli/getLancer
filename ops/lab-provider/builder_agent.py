"""Run ONLY in the pinned, offline builder microVM, with source/output block drives.

Supported reviewed recipes compile Python, Java or JavaScript without fetching
dependencies, then install into an operator-provided runtime image template.
"""
import hashlib
import os
from pathlib import Path, PurePosixPath
import sys
import shutil
import socket
import stat
import subprocess
import tempfile
import time
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))

from protocol import command, decode, digest, encode, instant, require, run_binding


def extract_archive(stream, destination):
    """No host extraction. Even in this disposable guest reject links, bombs and traversal."""
    with zipfile.ZipFile(stream) as archive:
        entries = archive.infolist()
        require(0 < len(entries) <= 2000, "INVALID_SOURCE_ARCHIVE", 400)
        total, names = 0, set()
        for entry in entries:
            path = PurePosixPath(entry.filename)
            require(not path.is_absolute() and path.parts and not any(p in (".", "..") for p in path.parts)
                    and "\\" not in entry.filename and "\x00" not in entry.filename and
                    path.as_posix() not in names and not entry.flag_bits & 1,
                    "INVALID_SOURCE_ARCHIVE", 400)
            names.add(path.as_posix())
            mode = entry.external_attr >> 16
            require(stat.S_IFMT(mode) in (0, stat.S_IFREG, stat.S_IFDIR), "INVALID_SOURCE_ARCHIVE", 400)
            total += entry.file_size
            require(entry.file_size <= 8388608 and total <= 67108864 and
                    entry.file_size <= max(1024, entry.compress_size * 100), "SOURCE_ARCHIVE_LIMIT", 400)
            target = destination.joinpath(*path.parts)
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(entry) as src, target.open("xb") as dst:
                    count = 0
                    while data := src.read(65536):
                        count += len(data)
                        require(count <= entry.file_size, "SOURCE_ARCHIVE_LIMIT", 400)
                        dst.write(data)
                    require(count == entry.file_size, "INVALID_SOURCE_ARCHIVE", 400)


def tool(argv, timeout=60, cwd="/"):
    # Output cannot consume memory/disk. Compilers have no network device or host mount.
    done = subprocess.run(argv, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                          timeout=timeout, shell=False, cwd=cwd, close_fds=True,
                          env={"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "SOURCE_DATE_EPOCH": "0",
                               "GOTOOLCHAIN": "local", "GOPROXY": "off", "GOSUMDB": "off", "CGO_ENABLED": "0", "GOCACHE": "/tmp/go-cache",
                               "DOTNET_CLI_TELEMETRY_OPTOUT": "1", "DOTNET_SKIP_FIRST_TIME_EXPERIENCE": "1", "DOTNET_CLI_HOME": "/tmp/dotnet"})
    require(done.returncode == 0, "BUILD_FAILED", 502)


def compile_source(source, recipe):
    require(recipe.keys() == {"language", "sourceEntry", "installPrefix", "runtimePolicy"}, "INVALID_RECIPE", 503)
    prefix = PurePosixPath(recipe["installPrefix"])
    require(not prefix.is_absolute() and prefix.parts and not any(p in (".", "..") for p in prefix.parts)
            and prefix.parts[0] == "opt", "INVALID_RECIPE", 503)
    files = sorted(path for path in source.rglob("*") if path.is_file())
    language = recipe["language"]
    if language == "python":
        require(any(p.suffix == ".py" for p in files), "BUILD_FAILED", 502)
        tool(["/usr/bin/python3", "-I", "-m", "compileall", "-q", "--invalidation-mode=checked-hash", str(source)])
    elif language == "java":
        java = [str(p) for p in files if p.suffix == ".java"]
        require(java and len(java) <= 200, "BUILD_FAILED", 502)
        (source / "classes").mkdir()
        tool(["/usr/bin/javac", "-proc:none", "--release", "17", "-d", str(source / "classes"), *java])
    elif language == "javascript":
        javascript = [str(p) for p in files if p.suffix in (".js", ".mjs", ".cjs")]
        require(0 < len(javascript) <= 100, "BUILD_FAILED", 502)
        for path in javascript:
            tool(["/usr/bin/node", "--check", path], timeout=1)
    elif language == "typescript":
        typescript = [str(p) for p in files if p.suffix == ".ts"]
        require(0 < len(typescript) <= 200, "BUILD_FAILED", 502)
        # A pinned offline TypeScript compiler + Node types live in the builder image.
        # Node's type stripping cannot be combined with --check.
        tool(["/opt/toolchains/typescript/bin/tsc", "--target", "ES2022", "--module", "nodenext",
              "--moduleResolution", "nodenext", "--noEmitOnError", "true", "--outDir", str(source/"compiled"), *typescript])
    elif language == "go":
        entry = source.joinpath(*PurePosixPath(recipe["sourceEntry"]).parts)
        (source/"bin").mkdir()
        tool(["/usr/bin/go", "build", "-trimpath", "-o", str(source/"bin/native"), str(entry)], cwd=str(source))
    elif language == "dotnet":
        projects = [str(p) for p in files if p.suffix == ".csproj"]
        require(len(projects) == 1, "BUILD_FAILED", 502)
        tool(["/usr/bin/dotnet", "restore", projects[0], "--source", "/opt/offline-nuget", "--ignore-failed-sources", "-p:NuGetAudit=false"])
        tool(["/usr/bin/dotnet", "publish", projects[0], "--no-restore", "--configuration", "Release", "--output", str(source/"bin"), "-p:UseAppHost=false"])
    elif language == "php":
        php = [str(p) for p in files if p.suffix == ".php"]
        require(0 < len(php) <= 100, "BUILD_FAILED", 502)
        for path in php:
            tool(["/usr/bin/php", "-l", path], timeout=1)
    else:
        require(False, "RECIPE_LANGUAGE_NOT_ADMITTED", 403)


def perform(cmd, recipes):
    inputs = cmd["inputs"]
    require(inputs.keys() == {"recipeId", "archiveSha256", "sourceSha256", "archiveBytes"}, "INVALID_BUILD_INPUT", 400)
    recipe = recipes.get(inputs["recipeId"])
    require(recipe is not None, "RECIPE_NOT_ADMITTED", 403)
    size = int(inputs["archiveBytes"])
    require(0 < size <= 33554432, "SOURCE_ARCHIVE_LIMIT", 400)
    with tempfile.TemporaryDirectory(dir="/tmp") as folder:
        folder = Path(folder)
        bundle = folder / "source.zip"
        with open("/dev/vdb", "rb", buffering=0) as disk, bundle.open("xb") as output:
            remaining = size
            while remaining:
                part = disk.read(min(65536, remaining))
                require(part, "TRUNCATED_SOURCE", 400)
                output.write(part)
                remaining -= len(part)
        with bundle.open("rb") as stream:
            require(hashlib.file_digest(stream, "sha256").hexdigest() == inputs["archiveSha256"], "SOURCE_DIGEST_MISMATCH", 403)
        source = folder / "source"
        source.mkdir()
        with bundle.open("rb") as stream:
            extract_archive(stream, source)
        source_entry = PurePosixPath(recipe["sourceEntry"])
        require(not source_entry.is_absolute() and not any(p in (".", "..") for p in source_entry.parts), "INVALID_RECIPE", 503)
        with source.joinpath(*source_entry.parts).open("rb") as primary:
            require(hashlib.file_digest(primary, "sha256").hexdigest() == inputs["sourceSha256"], "SOURCE_DIGEST_MISMATCH", 403)
        compile_source(source, recipe)
        mounted = folder / "runtime"
        mounted.mkdir()
        tool(["/usr/bin/mount", "-t", "ext4", "-o", "rw,nodev,nosuid,noexec", "/dev/vdc", str(mounted)], timeout=2)
        try:
            target = mounted.joinpath(*PurePosixPath(recipe["installPrefix"]).parts)
            # The reviewed template must reserve a new install directory, never a symlink/existing tree.
            for parent in [*reversed(target.parents), target]:
                if parent == mounted or mounted in parent.parents:
                    require(not parent.is_symlink(), "INVALID_OUTPUT_TEMPLATE", 503)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(source, target)
            policy = mounted / "etc/getlancer/runtime-policy.json"
            require(not policy.is_symlink() and not policy.parent.is_symlink(), "INVALID_OUTPUT_TEMPLATE", 503)
            policy.write_bytes(encode(recipe["runtimePolicy"]))
            tool(["/usr/bin/sync"], timeout=2)
        finally:
            tool(["/usr/bin/umount", str(mounted)], timeout=2)
    return {"output": "Build candidate created.", "summary": "Offline compilation and installation completed; operator review is required."}


def main():
    recipes = decode(Path("/etc/getlancer/build-recipes.json").read_bytes())
    binding, inputs_hash, completed = None, None, None
    with socket.socket(socket.AF_VSOCK, socket.SOCK_STREAM) as server:
        server.bind((socket.VMADDR_CID_ANY, 7000))
        server.listen(2)
        while True:
            connection, _ = server.accept()
            with connection:
                connection.settimeout(2)
                try:
                    raw = bytearray()
                    deadline = time.monotonic() + 2
                    while not raw.endswith(b"\n") and len(raw) <= 65536:
                        require(time.monotonic() < deadline)
                        data = connection.recv(min(4096, 65537 - len(raw)))
                        require(data)
                        raw.extend(data)
                    payload = decode(bytes(raw))
                    health = payload.get("action") == "HEALTH"
                    cmd = command(encode(dict(payload, action="START", operationId=None)) if health else bytes(raw))
                    require(cmd["scenarioId"] == "trusted-build" and cmd["leaseGeneration"] == 1 and
                            instant(cmd["expiresAt"]) > time.time() and cmd["inputs"].get("recipeId") in recipes)
                    if binding is None:
                        require(cmd["action"] == "START" and not health)
                        binding, inputs_hash = run_binding(cmd), digest(cmd["inputs"])
                    require(binding == run_binding(cmd) and inputs_hash == digest(cmd["inputs"]))
                    value = {"output": "", "summary": ""}
                    if cmd["action"] == "REQUEST":
                        require(cmd["operationId"] == "build")
                        if completed is None:
                            completed = perform(cmd, recipes)
                        value = completed
                    connection.sendall(encode(dict(binding=binding, commandId=cmd["commandId"],
                                                   state="RESULT" if cmd["action"] == "REQUEST" else "READY", **value)) + b"\n")
                except Exception:
                    return


if __name__ == "__main__":
    main()
