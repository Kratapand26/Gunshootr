"""Check Morphe source metadata, the bundle, and its injected Java extension."""

import argparse
from datetime import datetime
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify_metadata(path, version):
    metadata = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(metadata, dict), "Source metadata must be a JSON object")
    created_at = metadata.get("created_at")
    # Morphe 1.34.0 deserializes custom-source timestamps with kotlinx-datetime's
    # LocalDateTime serializer, then interprets them as UTC. Offset suffixes fail
    # before it can download the bundle (MorpheApp/morphe-manager a0e19e5).
    require(
        isinstance(created_at, str)
        and re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}", created_at),
        "created_at must be UTC YYYY-MM-DDTHH:MM:SS without Z or a timezone offset",
    )
    datetime.strptime(created_at, "%Y-%m-%dT%H:%M:%S")
    require(metadata.get("version") == version, "Metadata and bundle versions differ")
    require(
        metadata.get("download_url")
        == f"https://github.com/Kratapand26/Gunshootr/releases/download/v{version}/patches-{version}.mpp",
        "Metadata must point to the matching GitHub release bundle",
    )
    description = metadata.get("description")
    require(isinstance(description, str) and description.strip(), "Missing source description")


def manifest_attributes(data):
    attributes = {}
    key = None
    for line in data.decode("utf-8").splitlines():
        if line.startswith(" ") and key:
            attributes[key] += line[1:]
        elif ": " in line:
            key, attributes[key] = line.split(": ", 1)
    return attributes


def dex_classes(data, dexdump):
    require(data.startswith(b"dex\n"), "LinkedIn extension is not Android DEX")
    # dexdump is the parser shipped in the Android SDK already used by the build.
    with tempfile.NamedTemporaryFile(suffix=".dex", delete=False) as temporary:
        temporary.write(data)
        path = Path(temporary.name)
    try:
        result = subprocess.run(
            [str(dexdump), str(path)], capture_output=True, text=True,
            encoding="utf-8", errors="replace", check=False,
        )
        require(result.returncode == 0, "Android SDK rejected the extension DEX")
        classes = re.findall(r"Class descriptor\s*:\s*'([^']+)'", result.stdout)
        require(classes, "No extension classes found in the Android DEX")
        return classes
    finally:
        path.unlink(missing_ok=True)


def verify(bundle, version, dexdump):
    with zipfile.ZipFile(bundle) as archive:
        require(archive.testzip() is None, "Patch bundle ZIP checksum failed")
        names = archive.namelist()
        require(len(names) == len(set(names)), "Patch bundle contains duplicate entries")
        require(
            not any(name.endswith((".apk", ".exe", ".dll", ".so")) for name in names),
            "Patch bundle contains an unexpected app or native executable",
        )
        require(
            not any(name.startswith("org/junit/") for name in names),
            "JUnit test classes were bundled into the published patches",
        )
        attributes = manifest_attributes(archive.read("META-INF/MANIFEST.MF"))
        require(attributes.get("Name") == "Gunshootr Patches", "Unexpected bundle name")
        require(attributes.get("Version") == version, "Bundle and release versions differ")
        require(
            attributes.get("Source") == "https://github.com/Kratapand26/Gunshootr",
            "Unexpected bundle source URL",
        )
        require(attributes.get("Patcher-Version"), "Missing Morphe patcher version")
        require(archive.read("classes.dex").startswith(b"dex\n"), "Missing Android patch DEX")
        classes = dex_classes(archive.read("extensions/linkedin.mpe"), dexdump)
        unexpected = [name for name in classes if not name.startswith("Lapp/linkedin/extension/")]
        require(
            not unexpected,
            "LinkedIn extension includes classes outside its reviewed namespace: "
            + ", ".join(unexpected[:5]),
        )
        require(
            "Lapp/linkedin/extension/SettingsActivity;" in classes,
            "LinkedIn settings activity is missing from the extension",
        )
    return len(classes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--dexdump", required=True, type=Path)
    arguments = parser.parse_args()
    try:
        verify_metadata(arguments.metadata, arguments.version)
        count = verify(arguments.bundle, arguments.version, arguments.dexdump)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Bundle verification failed: {error}\n")
    print(f"Bundle {arguments.version} verified: source metadata, Android DEX, and {count} LinkedIn extension classes.")


if __name__ == "__main__":
    main()
