#!/usr/bin/env bash
set -euo pipefail
repo_root="$(git rev-parse --show-toplevel)"
source_dir="$repo_root/.airplay-source"
revision=a46cca0a6a1274e9e33c0e750f0f1c1eb1d881ea
if [ ! -d "$source_dir/.git" ]; then
  git clone --no-checkout https://github.com/jqssun/android-airplay-server.git "$source_dir"
fi
git -C "$source_dir" checkout --detach "$revision"
git -C "$source_dir" submodule update --init --recursive --depth 1
uxplay="$source_dir/app/src/main/cpp/third_party/UxPlay"
git -C "$uxplay" reset --hard
for patch in "$source_dir"/app/src/main/cpp/patches/UxPlay/*.patch; do
  git -C "$uxplay" apply --unidiff-zero "$patch"
done
