#!/usr/bin/env bash
#
# Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
# SPDX-License-Identifier: GPL-3.0-only
#

set -ex

LATEST_TAG="latest"
CURRENT_REV="$(git rev-parse --short HEAD)"
ASSET_DIRECTORY="${GITHUB_WORKSPACE:?}/app/outputs"

function overwrite_local_tag() {
  git tag -f "${LATEST_TAG}"
}

function overwrite_remote_tag() {
  git push -f origin "${LATEST_TAG}"
}

function has_release() {
  gh release view "${LATEST_TAG}" &>/dev/null
  echo "$?"
}

function delete_release() {
  gh release delete --yes "${LATEST_TAG}"
}

function create_rev_file() {
  pushd "${ASSET_DIRECTORY}" || exit 1
  echo "${CURRENT_REV}" | tee rev-hash.txt
  popd || exit 1
}

function create_release() {
  local CHANGELOG_FILE
  CHANGELOG_FILE="$(mktemp)"
  cp scripts/snapshot-changelog-template.txt "${CHANGELOG_FILE}"
  sed -i "s/__SNAPSHOT_REV__/${CURRENT_REV}/" "${CHANGELOG_FILE}"
  pushd "${ASSET_DIRECTORY}" || exit 1
  gh release create --prerelease --title "Latest snapshot build" --notes-file "${CHANGELOG_FILE}" "${LATEST_TAG}" ./*
  popd || exit 1
}

overwrite_local_tag

create_rev_file

overwrite_remote_tag

if [[ "$(has_release)" -eq 0 ]]; then
  delete_release
fi

create_release
