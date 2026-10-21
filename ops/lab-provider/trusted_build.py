"""Offline privileged build broker on a dedicated builder host, never the app host.

Only a root-reviewed job ID can select source, build image, output template or
recipe. Source bytes are opaque block data to the host. No upload/exec endpoint.
"""
import argparse
import datetime as dt
import fcntl
import hashlib
import os
from pathlib import Path
import sys
import shutil
import time
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parent))

from firecracker import Firecracker
from inventory import asset, read_trusted, trusted_path
from protocol import decode, encode, identity, instant, require, SLUG


def build(config, job_id):
    require(os.geteuid() == 0 and SLUG.fullmatch(job_id), "BUILD_DENIED", 403)
    require(config.keys() == {"epoch", "driver", "outputDir", "jobs"}, "INVALID_BUILD_CONFIG", 503)
    identity(config["epoch"])
    output_dir = trusted_path(config["outputDir"], directory=True)
    require(output_dir.stat().st_mode & 0o077 == 0, "PRIVATE_BUILD_OUTPUT_REQUIRED", 503)
    lock = os.open(output_dir / "build.lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        job = config["jobs"].get(job_id)
        require(isinstance(job, dict) and job.keys() == {"source", "archiveSha256", "sourceSha256", "builderKernel",
                "builderKernelSha256", "builderRootfs", "builderRootfsSha256", "outputTemplate", "outputTemplateSha256",
                "recipeId", "approvedUntil"}, "BUILD_JOB_NOT_ADMITTED", 403)
        require(instant(job["approvedUntil"]) > time.time() and SLUG.fullmatch(job["recipeId"]), "BUILD_JOB_NOT_ADMITTED", 403)
        source = asset(job["source"], job["archiveSha256"])
        template = asset(job["outputTemplate"], job["outputTemplateSha256"])
        require(0 < Path(source).stat().st_size <= 33554432 and 0 < Path(template).stat().st_size <= 536870912
                and Path(template).stat().st_size % 512 == 0, "BUILD_ASSET_TOO_LARGE", 403)
        driver = Firecracker(config["driver"], prefix="labbuild")
        # Every invocation first reconciles orphaned prior builds; no occupied slot is reused.
        for orphan in driver.discover():
            require(driver.stop(orphan), "BUILD_ORPHAN_UNCONFIRMED", 503)
        run_id = str(uuid.uuid4())
        expiry = dt.datetime.fromtimestamp(time.time() + 300, dt.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
        cmd = dict(runId=run_id, leaseGeneration=1, operatorEpoch=config["epoch"], commandId=str(uuid.uuid4()),
                   manifestSha256=hashlib.sha256(encode(job)).hexdigest(), imageDigest="sha256:" + job["builderRootfsSha256"],
                   expiresAt=expiry, action="START", scenarioId="trusted-build", operationId=None,
                   inputs={"recipeId": job["recipeId"], "archiveSha256": job["archiveSha256"],
                           "sourceSha256": job["sourceSha256"], "archiveBytes": str(Path(source).stat().st_size)}, memoryMiB=512)
        entry = dict(kernel=job["builderKernel"], kernelSha256=job["builderKernelSha256"],
                     rootfs=job["builderRootfs"], rootfsSha256=job["builderRootfsSha256"])
        destination = None
        try:
            require(driver.start(entry, cmd, 0, (("source", source, False), ("output", template, True))), "BUILD_NOT_READY", 503)
            # Build work uses a separate bounded vsock call with its own monotonic supervisor deadline.
            cmd.update(action="REQUEST", operationId="build", commandId=str(uuid.uuid4()))
            driver.request(cmd)
            require(driver.isolated(cmd), "BUILD_ISOLATION_UNCONFIRMED", 503)
            # Freeze the complete cgroup before reading any bytes from its writable output drive.
            driver.call(["/usr/bin/systemctl", "freeze", driver.unit(run_id)])
            group = Path("/sys/fs/cgroup/system.slice") / driver.unit(run_id)
            require("frozen 1" in (group / "cgroup.events").read_text(), "BUILD_FREEZE_UNCONFIRMED", 503)
            image = driver.root(run_id) / "output"
            with os.fdopen(os.open(image, os.O_RDONLY | os.O_NOFOLLOW), "rb") as stream:
                image_hash = hashlib.file_digest(stream, "sha256").hexdigest()
                stream.seek(0)
                destination = output_dir / (image_hash + ".ext4")
                # Never overwrite an existing immutable output or follow a destination symlink.
                with os.fdopen(os.open(destination, os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, 0o400), "wb") as out:
                    shutil.copyfileobj(stream, out, 1048576)
                    out.flush()
                    os.fsync(out.fileno())
            receipt = {"version": 1, "mode": "UNSIGNED_BUILD_CANDIDATE", "jobId": job_id, "runId": run_id,
                       "archiveSha256": job["archiveSha256"], "sourceSha256": job["sourceSha256"],
                       "builderRootfsSha256": job["builderRootfsSha256"], "recipeId": job["recipeId"],
                       "imageDigest": "sha256:" + image_hash}
            require(driver.stop(run_id), "BUILD_CLEANUP_UNCONFIRMED", 503)
            with os.fdopen(os.open(output_dir / (image_hash + ".json"), os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, 0o400), "wb") as out:
                out.write(encode(receipt))
                out.flush()
                os.fsync(out.fileno())
            return receipt
        finally:
            # A candidate without a receipt must never be admitted. Hard lease still applies if this process dies.
            driver.stop(run_id)
    finally:
        os.close(lock)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True)
    parser.add_argument("--job", required=True)
    args = parser.parse_args()
    build(decode(read_trusted(args.config)), args.job)
    print("Unsigned build candidate created; review the private output directory.")
