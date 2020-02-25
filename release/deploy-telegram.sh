#!/usr/bin/env bash

function send_to_tg() {
  local FILE
  local CHAT_ID
  local CAPTION
  FILE="${1}"
  CHAT_ID="${2}"
  CAPTION="${3}"
  curl -F chat_id="${CHAT_ID}" -F document="@${FILE}" -F caption="${CAPTION}" -F parse_mode="Markdown" "https://api.telegram.org/bot${TG_TOKEN:?}/sendDocument" >/dev/null 2>&1
}

send_to_tg "${TG_FILE:?}" "${TG_TO:?}" "aps-${GITHUB_RUN_NUMBER}-debug"
