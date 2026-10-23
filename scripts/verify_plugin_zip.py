"""Verify a target-specific Cameo installer without proprietary dependencies."""
import argparse
import hashlib
import io
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zipfile


def verify(path: Path, target: str) -> dict:
    prefix = "plugins/com.claude.cameo.bridge/"
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None, "ZIP CRC failure"
        plugin = ET.fromstring(archive.read(prefix + "plugin.xml"))
        version = plugin.attrib["version"]
        assert path.name == f"cameo-mcp-bridge-{version}-{target}.zip", "Wrong asset name"
        required = plugin.findall("requires/required-plugin")
        assert len(required) == 3 and all(p.attrib["version"] == target for p in required)
        descriptor_path = "data/resourcemanager/MDR_Plugin_Cameo_MCP_Bridge_11974001_descriptor.xml"
        descriptor = ET.fromstring(archive.read(descriptor_path))
        assert descriptor.attrib["mdVersionMin"] == target
        assert descriptor.attrib["mdVersionMax"] == ("2022x" if target == "2022x" else "higher")
        assert descriptor.find("version").attrib["human"] == version
        for entry in descriptor.findall("installation/file"):
            assert entry.attrib["from"] in archive.namelist(), "Missing installation source"
            assert entry.attrib["from"] == entry.attrib["to"], "Unexpected installation destination"
        jar_name = plugin.find("runtime/library").attrib["name"]
        assert jar_name == f"cameo-mcp-bridge-{version}.jar"
        files = {n for n in archive.namelist() if not n.endswith("/")}
        assert files == {prefix + "plugin.xml", prefix + jar_name, descriptor_path}, "Unexpected packaged files"
        classes = 0
        with zipfile.ZipFile(io.BytesIO(archive.read(prefix + jar_name))) as jar:
            assert jar.testzip() is None
            properties = dict(line.split("=", 1) for line in jar.read("cameo-bridge-build.properties").decode().splitlines())
            java = 11 if target == "2022x" else 17
            assert properties == {"cameoTarget": target, "javaRelease": str(java)}
            for name in jar.namelist():
                assert not name.endswith(".jar"), "Nested dependency JAR"
                if name.endswith(".class"):
                    assert name.startswith("com/claude/cameo/bridge/"), "Foreign dependency class"
                    magic, minor, major = struct.unpack(">IHH", jar.read(name)[:8])
                    assert magic == 0xCAFEBABE and major == java + 44 and minor == 0, "Wrong bytecode target"
                    classes += 1
            for name in ("CameoMCPBridgePlugin", "BridgeOptions", "util/BridgePlatform"):
                assert "com/claude/cameo/bridge/" + name + ".class" in jar.namelist()
    return {"file": str(path), "target": target, "version": version, "classes": classes,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


if __name__ == "__main__":
    import json
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--target", choices=("2022x", "2024x"), required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.archive, args.target), indent=2))
