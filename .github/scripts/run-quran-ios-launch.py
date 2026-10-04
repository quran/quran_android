"""Launch the built app: compiling a framework alone does not detect startup crashes."""
import argparse
import json
import plistlib
import time
import subprocess
from pathlib import Path


def simctl(*args, **kwargs):
    return subprocess.run(["xcrun", "simctl", *args], check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("app", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    info = plistlib.loads((args.app / "Info.plist").read_bytes())
    if info.get("CADisableMinimumFrameDurationOnPhone") is not True:
        raise RuntimeError("Built app lacks Compose's mandatory frame-duration setting")
    bundle_id = info["CFBundleIdentifier"]
    devices = json.loads(simctl("list", "devices", "available", "--json", capture_output=True, text=True).stdout)
    iphones = [d for runtime, entries in devices["devices"].items()
               if "iOS" in runtime for d in entries if d["name"].startswith("iPhone")]
    if not iphones:
        raise RuntimeError("No available iPhone simulator")
    device = next((d for d in iphones if d["state"] == "Booted"), iphones[0])
    udid = device["udid"]
    if device["state"] != "Booted":
        simctl("boot", udid, timeout=120)
    simctl("bootstatus", udid, "-b", timeout=180)
    simctl("install", udid, str(args.app.resolve()), timeout=60)
    args.output.mkdir(parents=True, exist_ok=True)
    launch = simctl(
        "launch", "--terminate-running-process", udid, bundle_id,
        capture_output=True, text=True, timeout=60,
    )
    (args.output / "launch.log").write_text(launch.stdout + launch.stderr)
    prefix, separator, pid_text = launch.stdout.strip().partition(":")
    if not separator or prefix != bundle_id or not pid_text.strip().isdigit():
        raise RuntimeError("simctl did not return a bundle/PID launch confirmation; see launch.log")
    pid = pid_text.strip()
    try:
        time.sleep(5)
        simctl("io", udid, "screenshot", str(args.output / "launch.png"), timeout=30)
        for probe in range(4):
            process = subprocess.run(
                ["xcrun", "simctl", "spawn", udid, "launchctl", "print", f"pid/{pid}"],
                capture_output=True, text=True, timeout=30,
            )
            if process.returncode == 0:
                if probe < 3:
                    time.sleep(5)
                continue
            raise RuntimeError(f"The app exited after launch; see launch.log ({process.stderr.strip()})")
        print(f"PASS: {bundle_id} stayed running for 20 seconds on {device['name']}")
    finally:
        subprocess.run(["xcrun", "simctl", "terminate", udid, bundle_id], check=False, capture_output=True, timeout=30)


if __name__ == "__main__":
    main()
