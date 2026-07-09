import yt_dlp
import json


def download(quickjs_bin: str, video_id: str) -> str:
    opts = {"format": "bestaudio", "js_runtimes": {"quickjs": {"path": quickjs_bin}}}

    return json.dumps(
        yt_dlp.YoutubeDL(opts).extract_info(video_id, download=False), indent=4
    )


def upgrade(package_name):
    try:
        import ensurepip

        ensurepip.bootstrap()
    except Exception as e:
        print(f"Error running ensurepip: ${e}")

    try:
        import pip
        from pip._internal import main as pip_main

        pip_main(["install", "--upgrade", package_name])
        print(f"Successfully upgraded {package_name}")
    except Exception as e:
        print(f"Error upgrading package {package_name}: {e}")
